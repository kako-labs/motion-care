#!/usr/bin/env python3
"""Exercise an installed scaffold on a dedicated emulator without clearing app data."""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default='adb')
args = parser.parse_args()
package = 'dev.motioncare.scaffold'
activity = package + '/dev.motioncare.app.MainActivity'

def adb(*parts):
    return subprocess.check_output([args.adb, '-s', args.serial, *parts], text=True)

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/motioncare-smoke.xml')
    xml = adb('shell', 'cat', '/sdcard/motioncare-smoke.xml')
    return list(ET.fromstring(xml).iter('node'))

def find(text, exact=False):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        for node in nodes():
            label = node.get('text', '')
            if (label == text if exact else text in label):
                return node
        time.sleep(0.2)
    raise AssertionError('UI text not found: ' + text)

def tap(text, exact=True):
    node = find(text, exact)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))

def restart():
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', activity)

restart()
find('합성 샘플 전용')
tap('프로필 연결 자리')
find('사람·차량 등록')
tap('돌아가기')
tap('샘플 주행 시작')
for version in range(1, 4):
    tap('샘플 신호 추가')
    find(f'원본 버전: {version}')
tap('샘플 주행 종료')
tap('주행 상세')
find('샘플 3개')
tap('평가 연결 자리')
find('연결된 주행:')
adb('shell', 'input', 'keyevent', '4')
find('샘플 3개')
tap('메인으로')
tap('완료')
restart()
find('COMPLETED')
tap('샘플 주행 시작')
tap('샘플 신호 추가')
find('원본 버전: 1')
restart()
find('INTERRUPTED')
find('COMPLETED')
find('샘플 주행 시작')
print('PASS: navigation, capture, completion, feedback return, persistence, interruption recovery')
