const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../../ui-review/selected-b/app.js'),'utf8').split('renderHome(); renderSelector(); renderProfile(); renderRecords();')[0];
function load(search,savedDriver='driver'){
 const nodes=new Map(),node=key=>{if(!nodes.has(key))nodes.set(key,{innerHTML:'',textContent:'',classList:{toggle(){}},setAttribute(){}});return nodes.get(key);};
 const storage=new Map([['motion-care-demo-scenario','populated-v2'],['motion-care-demo-seats',JSON.stringify({'rear-left':null,'rear-center':null,'rear-right':'passenger','front-passenger':savedDriver===null?'jiyoung':null,driver:savedDriver})]]);
 const c={URLSearchParams,location:{search},localStorage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v)},document:{querySelector:node,querySelectorAll:()=>[],addEventListener(){}},buildJourneyReplay:()=>({samples:[],events:[{end:0}],distanceKm:20,validSeconds:1800}),clockTime:()=>'',journeyChart:()=>'',selectJourneyEvent(){}};
 vm.createContext(c);vm.runInContext(source,c);return {c,node,run:code=>vm.runInContext(code,c)};
}
for(const driver of ['driver',null]){
 const {c,node,run}=load('?driver=jiyoung',driver);
 assert.equal(run('seatAssignments.driver'),'jiyoung','explicit populated scenario wins over saved seats');
 assert.equal(run('Object.values(seatAssignments).filter(x=>x==="jiyoung").length'),1);
 c.renderHome();c.renderRecords();
 c.renderProfile();
 assert.ok(node('#profileMain').innerHTML.includes('최근 운전 경향'),'the selected adult driver uses their own driving profile');
 assert.ok(node('#profileMain').innerHTML.includes('3<small>회'),'profile count matches the three displayed trips');
 assert.equal((node('#homeTrips').innerHTML.match(/data-trip=/g)||[]).length,3);
 assert.equal((node('#recordList').innerHTML.match(/data-trip=/g)||[]).length,3);
 assert.ok(!node('#recordList').innerHTML.includes('data-trip="t1"'));
 run('selectedTrip="t3"');c.renderRecords();assert.equal(run('selectedTrip'),'j1');
 run('assignPerson("driver","driver")');c.renderHome();c.renderRecords();
 assert.equal(run('selectedTrip'),'t1');assert.ok(node('#recordList').innerHTML.includes('data-trip="t1"'));
 run('seatAssignments.driver=null');c.renderHome();c.renderRecords();
 assert.equal((node('#recordList').innerHTML.match(/data-trip=/g)||[]).length,0);
 assert.ok(node('#tripDetail').innerHTML.includes('운전자'));
}
console.log('PASS: populated deep link, stale seats, per-driver home/history, selected trip and empty state');
