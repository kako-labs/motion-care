// Regenerates debug-only synthetic data from the approved, archived HTML fixture.
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const context={document:{addEventListener(){}}}; vm.createContext(context);
const source=path.join(root,'docs/design-archive/history-b-2026-11-07/ui-review/selected-b/journey-data.js');
vm.runInContext(fs.readFileSync(source,'utf8'),context);
const trips=[['j1',32,4,2,3,'2026-11-04T09:20:00+09:00'],['j2',35,6,3,4,'2026-11-03T14:10:00+09:00'],['j3',29,7,3,5,'2026-11-01T17:35:00+09:00']]
  .map(([id,duration,stop,start,variant,date])=>({id,started:Date.parse(date),duration,replay:context.buildJourneyReplay(duration,stop,start,variant)}));
const target=path.join(root,'feature/history/src/debug/assets/history-b-synthetic.json');
fs.mkdirSync(path.dirname(target),{recursive:true});
fs.writeFileSync(target,JSON.stringify({origin:'SYNTHETIC',source:'Approved B HTML replay, isolated debug fixture, not measured vehicle data',trips}));
