"""Inspect the built release APK for accidental debug fixture/class inclusion."""
import hashlib,json,sys,zipfile
from pathlib import Path

apk=Path(sys.argv[1]) if len(sys.argv)>1 else Path('app/build/outputs/apk/release/app-release-unsigned.apk')
with zipfile.ZipFile(apk) as archive:
    assets=[name for name in archive.namelist() if 'history-b-synthetic' in name]
    forbidden=[b'HistoryDemoActivity',b'DemoRepositories',b'history-b-synthetic.json']
    classes={name:[item.decode() for item in forbidden if item in archive.read(name)] for name in archive.namelist() if name.endswith('.dex')}
    matches={name:items for name,items in classes.items() if items}
assert not assets and not matches, {'assets':assets,'classes':matches}
result={'apk':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'debug_fixture_assets':assets,'debug_class_strings':matches,'passed':True}
Path('docs/history-b-android/device-checks/release-isolation.json').write_text(json.dumps(result,indent=2),encoding='utf8')
print(json.dumps(result))
