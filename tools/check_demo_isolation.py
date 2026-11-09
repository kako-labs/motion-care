"""Check only IDs/counts, without copying user databases into the repository."""
import hashlib,json,sqlite3,subprocess,sys
from pathlib import Path
from history_b_device_check import adb

result={}
for name in ['motion-care-profile.db','motion-care-sample.db']:
    data=adb('exec-out','run-as','dev.motioncare.scaffold','cat','databases/'+name)
    connection=sqlite3.connect(':memory:'); connection.deserialize(data)
    tables=[row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
    info={}
    for table in tables:
        # Identifiers originate from sqlite_master and are quoted, never shell-interpolated.
        quoted='"'+table.replace('"','""')+'"'
        count=connection.execute('SELECT COUNT(*) FROM '+quoted).fetchone()[0]
        columns=[row[1] for row in connection.execute('PRAGMA table_info('+quoted+')')]
        fixture_matches=0
        for column in columns:
            qcol='"'+column.replace('"','""')+'"'
            fixture_matches+=connection.execute('SELECT COUNT(*) FROM '+quoted+' WHERE '+qcol+' IN (?,?,?,?,?)',('j1','j2','j3','jiyoung','seoa')).fetchone()[0]
        assert fixture_matches==0, 'Debug fixture leaked into '+table
        info[table]={'rows':count,'fixture_matches':fixture_matches}
    result[name]={'sha256':hashlib.sha256(data).hexdigest(),'tables':info}
path=Path(sys.argv[1]) if len(sys.argv)>1 else Path('docs/history-b-android/device-checks/database-isolation.json')
path.write_text(json.dumps(result,indent=2),encoding='utf8'); print(json.dumps(result))
