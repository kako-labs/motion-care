"""Observe and exercise a connected debug build through Android's own UI hierarchy."""
import json, os, re, subprocess, sys, time
from pathlib import Path
from xml.etree import ElementTree as ET

ADB=os.environ.get('HISTORY_B_ADB','C:/Users/hhson/AppData/Local/Android/Sdk/platform-tools/adb.exe')
OUT=Path('docs/history-b-android/device-checks'); OUT.mkdir(parents=True,exist_ok=True)
def adb(*args):
    p=subprocess.run([ADB,'-s','emulator-5554',*args],capture_output=True)
    if p.returncode: raise RuntimeError(p.stderr.decode('utf8',errors='replace'))
    return p.stdout
def tree():
    adb('shell','uiautomator','dump','/sdcard/history-b-ui.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/history-b-ui.xml'))
def observe(name):
    root=tree()
    (OUT/(name+'.xml')).write_text(ET.tostring(root,encoding='unicode'),encoding='utf8')
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    nodes=[{k:n.get(k) for k in ('text','content-desc','bounds','clickable','enabled')} for n in root.iter('node') if n.get('text') or n.get('content-desc')]
    print(json.dumps({'capture':name,'visible':[n['text'] or n['content-desc'] for n in nodes]},ensure_ascii=False))
def click(label):
    root=tree()
    nodes=[n for n in root.iter('node') if n.get('text')==label or n.get('content-desc')==label]
    if not nodes: raise RuntimeError('Not visible: '+label)
    bounds=list(map(int,re.findall(r'\d+',nodes[0].get('bounds'))))
    x=(bounds[0]+bounds[2])//2; y=(bounds[1]+bounds[3])//2
    adb('shell','input','tap',str(x),str(y)); time.sleep(.8)
def swipe(direction):
    if direction=='down': adb('shell','input','swipe','1500','950','1500','350','450')
    else: adb('shell','input','swipe','1500','350','1500','950','450')
    time.sleep(.5)
if __name__=='__main__':
    command=sys.argv[1]
    if command=='observe': observe(sys.argv[2])
    elif command=='click': click(sys.argv[2]); observe(sys.argv[3])
    elif command=='scroll': swipe(sys.argv[2]); observe(sys.argv[3])
    elif command=='back': adb('shell','input','keyevent','4'); time.sleep(.7); observe(sys.argv[2])
