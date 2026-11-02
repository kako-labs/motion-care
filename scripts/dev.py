#!/usr/bin/env python3
"""Portable project entry point. Python standard library only."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def properties(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8').splitlines()
                if '=' in line and not line.lstrip().startswith('#'))

def sdk_path():
    value = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    local = ROOT / 'local.properties'
    if not value and local.exists():
        value = properties(local).get('sdk.dir', '').replace('\\:', ':').replace('\\\\', '\\')
    return Path(value).expanduser() if value else None

def java_command():
    if os.environ.get('JAVA_HOME'):
        return str(Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
    return shutil.which('java')

def doctor():
    errors = []
    java = java_command()
    if java and Path(java).is_file():
        result = subprocess.run([java, '-version'], capture_output=True, text=True)
        output = result.stdout + result.stderr
        if result.returncode or not re.search(r'version "17[.\"]', output):
            errors.append('JDK 17 is required. Set JAVA_HOME to the installed JDK 17.')
    else:
        errors.append('Java was not found. Install JDK 17.')
    sdk = sdk_path()
    config = properties(ROOT / 'config/android-test.properties')
    if not sdk or not sdk.is_dir():
        errors.append('Set ANDROID_HOME or sdk.dir in local.properties to your Android SDK.')
    else:
        for relative in ['platforms/android-' + config['compileSdk'], 'build-tools/' + config['buildTools']]:
            if not (sdk / relative).is_dir():
                errors.append('Missing SDK package: ' + relative + '. Run setup-sdk after accepting SDK licenses.')
    for error in errors:
        print('ERROR:', error)
    if errors:
        return 1
    print('Ready: JDK 17, Android SDK packages, project Gradle wrapper.')
    return 0

def gradle(tasks, clean_home=False):
    env = os.environ.copy()
    if clean_home:
        env['GRADLE_USER_HOME'] = str(ROOT / '.gradle-user-home')
    wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    return subprocess.call([str(wrapper), *tasks, '--no-daemon'], cwd=ROOT, env=env)

def setup_sdk():
    sdk = sdk_path()
    executable = 'sdkmanager.bat' if os.name == 'nt' else 'sdkmanager'
    manager = sdk / 'cmdline-tools/latest/bin' / executable if sdk else None
    if not manager or not manager.is_file():
        print('Install Android SDK Command-line Tools (latest) using SDK Manager first.')
        return 1
    config = properties(ROOT / 'config/android-test.properties')
    return subprocess.call([str(manager), 'platforms;android-' + config['compileSdk'],
        'build-tools;' + config['buildTools'], 'platform-tools', 'emulator'], cwd=ROOT)

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('command', choices=['doctor', 'setup-sdk', 'verify', 'ui', 'ui-connected'])
parser.add_argument('--clean-home', action='store_true', help='Use a project-local Gradle home without personal init scripts.')
args = parser.parse_args()
if args.command == 'doctor':
    sys.exit(doctor())
if args.command == 'setup-sdk':
    sys.exit(setup_sdk())
if doctor():
    sys.exit(1)
tasks = {
    'verify': ['verifyAll'],
    'ui': [':app:pixel2api35DebugAndroidTest'],
    'ui-connected': [':app:connectedDebugAndroidTest'],
}
sys.exit(gradle(tasks[args.command], args.clean_home))
