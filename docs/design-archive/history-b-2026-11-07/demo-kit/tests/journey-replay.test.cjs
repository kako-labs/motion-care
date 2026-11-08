const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const context={document:{addEventListener(){}},console};vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../../ui-review/selected-b/journey-data.js'),'utf8'),context);
for(const [minutes,stops,starts,variant] of [[38,5,3,0],[34,8,4,1],[31,9,5,2]]){
  const replay=context.buildJourneyReplay(minutes,stops,starts,variant);
  assert.equal(replay.origin,'SYNTHETIC');
  assert.equal(replay.samples.length,minutes*60+1);
  assert.equal(replay.validSeconds,(minutes-1)*60);
  assert.equal(replay.events.length,stops);
  assert.ok(replay.samples.every((p,i)=>p.second===i&&(p.kmh===null||(Number.isFinite(p.kmh)&&p.kmh>=0&&p.kmh<=80))));
  assert.equal(replay.samples[0].kmh,0);assert.equal(replay.samples.at(-1).kmh,0);
  for(const event of replay.events){
    assert.equal(replay.samples[event.start].kmh,event.before);
    assert.equal(replay.samples[event.end].kmh,0);
    assert.ok(event.before/(event.end-event.start)/3.6>=2);
  }
  const trip={replay,duration:minutes,date:'11월 4일 08:12'};
  const svg=context.journeyChart(trip);
  const trace=svg.match(/class="journey-trace" d="([^"]+)"/)[1];
  assert.equal((trace.match(/M/g)||[]).length,2,'one missing span must split the trace into two subpaths');
  assert.ok(!svg.includes('NaN'));
}
console.log('PASS: 3 replay fixtures; timestamps, coverage, braking intervals and signal gap');
