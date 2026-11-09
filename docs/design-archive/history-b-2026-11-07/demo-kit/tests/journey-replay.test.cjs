const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const context={document:{addEventListener(){}},console};vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../../ui-review/selected-b/journey-data.js'),'utf8'),context);
for(const [minutes,stops,starts,repeats,variant] of [[38,5,3,2,0],[34,8,4,3,1],[31,9,5,4,2],[32,4,2,1,3],[35,6,3,2,4],[29,7,3,2,5]]){
  const replay=context.buildJourneyReplay(minutes,stops,starts,variant,repeats);
  assert.equal(replay.origin,'SYNTHETIC');
  assert.equal(replay.samples.length,minutes*60+1);
  assert.equal(replay.validSeconds,minutes*60);
  assert.equal(replay.events.length,stops);
  assert.equal(replay.startEvents.length,starts);
  assert.equal(replay.repeatEvents.length,repeats);
  assert.ok(replay.samples.every((p,i)=>p.second===i&&(p.kmh===null||(Number.isFinite(p.kmh)&&p.kmh>=0&&p.kmh<=80))));
  assert.equal(replay.samples[0].kmh,0);assert.equal(replay.samples.at(-1).kmh,0);
  for(const event of replay.events){
    assert.equal(replay.samples[event.start].kmh,event.before);
    assert.equal(replay.samples[event.end].kmh,0);
    assert.ok(event.end-event.start>=7&&event.end-event.start<=15,'NGSIM-calibrated complete stops take 7–15 seconds');
    assert.ok(event.peakDecelerationMps2>=2,'the final five seconds contain a large deceleration sample');
    assert.equal(replay.detailSampleIntervalSeconds,.2);
    for(const point of event.detailSamples){
      if(Number.isInteger(point.second))assert.equal(point.kmh,replay.samples[point.second].kmh,'detail and overview must agree at shared timestamps');
    }
    const braking=event.detailSamples.filter(p=>p.second>=event.start&&p.second<=event.end);
    assert.ok(braking.every((p,i)=>!i||p.kmh<=braking[i-1].kmh),'braking must decrease toward a complete stop');
    const early=braking[Math.round((braking.length-1)*.2)];
    assert.ok(early.kmh>event.before*.84,'progressive brake pressure must differ from a straight linear ramp');
  }
  for(const event of replay.startEvents){
    assert.equal(event.type,'start');
    assert.equal(event.before,0);
    assert.ok(event.after>=15,'rapid-start event must reach a meaningful road speed');
    assert.ok(event.peakAccelerationMps2>0,'rapid-start event must contain positive acceleration');
    assert.ok(event.end>event.start);
  }
  for(const event of replay.repeatEvents){
    assert.equal(event.type,'repeat');
    assert.equal(event.end-event.start,28);
    assert.equal(event.cycles,2);
    assert.ok(event.maxKmh-event.minKmh>=7,'repeated acceleration must have a visible speed range');
  }
  const trip={replay,duration:minutes,date:'11월 4일 08:12'};
  const svg=context.journeyChart(trip);
  const trace=svg.match(/class="journey-trace" d="([^"]+)"/)[1];
  assert.equal((trace.match(/M/g)||[]).length,1,'the speed trace must remain continuous');
  assert.ok(!svg.includes('NaN'));
}
console.log('PASS: 6 continuous replay fixtures; stop, rapid-start and repeated-acceleration events remain consistent with the speed trace');
