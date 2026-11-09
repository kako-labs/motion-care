/*
 * Deterministic mock replay. Stop shapes are calibrated from complete-stop
 * trajectories in the FHWA NGSIM Peachtree Street data (vehicles 37, 55, 56,
 * 61 and 68; 0.1 s source samples). This is not raw vehicle acquisition data.
 */
function buildJourneyReplay(durationMinutes, hardStopCount, rapidStartCount, variant, repeatCount=1) {
  const duration=durationMinutes*60, count=hardStopCount+4;
  const weights=[.75,1.32,.67,1.18,1.45,.92,1.28,.85,.78,1.16,.95,1.38,1.08];
  const totalWeight=weights.slice(0,count).reduce((a,b)=>a+b,0);let cumulative=0;
  const stops=weights.slice(0,count).map(w=>{cumulative+=w;return Math.round(duration*cumulative/totalWeight/5)*5;});
  const hardIndices=new Set(Array.from({length:hardStopCount},(_,i)=>Math.floor(i*(stops.length-1)/hardStopCount)));
  const gapIndex=Math.floor(stops.length/2)-1;
  const gapStart=Math.round((stops[gapIndex]+stops[gapIndex+1])/2)-30, gapEnd=gapStart+60;
  const cruise=[[0,37],[.12,48],[.27,55],[.36,32],[.43,22],[.48,43],[.63,58],[.77,51],[.86,39],[1,34]];
  const ngsimStopProfiles=[
    [[0,1],[.27,.97],[.41,.71],[.48,.48],[.62,.36],[.68,.13],[.89,.13],[.96,.01],[1,0]],
    [[0,1],[.26,.94],[.43,.78],[.52,.58],[.61,.39],[.70,.38],[.78,.24],[.87,.09],[.96,.08],[1,0]],
    [[0,1],[.20,.92],[.40,.82],[.53,.81],[.67,.69],[.80,.44],[.84,.24],[.90,.13],[.97,.04],[1,0]]
  ];
  const profileRatio=(profile,u)=>{let j=1;while(j<profile.length-1&&profile[j][0]<u)j++;const a=profile[j-1],b=profile[j],blend=(u-a[0])/(b[0]-a[0]);return a[1]+(b[1]-a[1])*blend;};
  const base=t=>{const fraction=t/duration;let j=1;while(j<cruise.length-1&&cruise[j][0]<fraction)j++;const a=cruise[j-1],b=cruise[j],blend=(fraction-a[0])/(b[0]-a[0]);return a[1]+(b[1]-a[1])*blend+2*Math.sin(t/37)+1.3*Math.sin(t/13)+variant*.6;};
  const observedHardStopSeconds=[8,12,15,13,12];
  const brakeDuration=index=>{
    if(!hardIndices.has(index))return [22,27,31][(variant+index)%3];
    const observed=observedHardStopSeconds[(variant+index)%observedHardStopSeconds.length];
    const approach=base(stops[index]-observed);
    if(approach<38)return 8;
    if(approach<45)return Math.min(observed,12);
    return observed;
  };
  const dwellFor=nextIndex=>nextIndex===0?3:[3,6,2,13,3,8,32,16][(nextIndex-1)%8];
  const accelerationDuration=nextIndex=>nextIndex>0&&nextIndex<=rapidStartCount?[7,8,9][(variant+nextIndex)%3]:20;
  const repeatCandidates=[];
  for(let nextIndex=1;nextIndex<stops.length;nextIndex++){
    const start=stops[nextIndex-1]+dwellFor(nextIndex)+accelerationDuration(nextIndex)+12;
    const end=stops[nextIndex]-brakeDuration(nextIndex)-12;
    if(end-start>=28){const center=Math.round((start+end)/2);if(center<gapStart-24||center>gapEnd+24)repeatCandidates.push(center);}
  }
  const repeatCenters=repeatCandidates.slice(0,repeatCount);
  const repeatEffect=t=>repeatCenters.reduce((effect,center)=>{const start=center-14,end=center+14;if(t<=start||t>=end)return effect;const u=(t-start)/(end-start);return effect+5.2*Math.sin(Math.PI*u)*Math.sin(Math.PI*4*u);},0);
  const cruiseAt=t=>Math.max(7,base(t)+repeatEffect(t));
  function speedAt(t){
    const nextIndex=stops.findIndex(s=>s>=t), next=stops[nextIndex]??duration;
    const last=nextIndex>0?stops[nextIndex-1]:0, since=t-last;
    const dwell=dwellFor(nextIndex);
    const brakeSeconds=brakeDuration(nextIndex);
    if(t===0||t===duration||since<=dwell)return 0;
    if(next-t<=brakeSeconds){
      const u=1-(next-t)/brakeSeconds;
      const approachSpeed=cruiseAt(next-brakeSeconds);
      if(hardIndices.has(nextIndex)){
        const profile=ngsimStopProfiles[(variant+nextIndex)%ngsimStopProfiles.length];
        return Math.max(0,approachSpeed*profileRatio(profile,u));
      }
      const pressure=.08*u+.92*(3*u*u-2*u*u*u);
      return Math.max(0,approachSpeed*(1-pressure));
    }
    const accelerationSeconds=accelerationDuration(nextIndex);
    if(since<dwell+accelerationSeconds){const u=(since-dwell)/accelerationSeconds;return cruiseAt(t)*(.1*u+.9*(3*u*u-2*u*u*u));}
    return cruiseAt(t);
  }
  const samples=Array.from({length:duration+1},(_,t)=>({second:t,kmh:t>gapStart&&t<gapEnd?null:Math.round(speedAt(t)*10)/10}));
  let distanceKm=0,validSeconds=0;
  for(let i=1;i<samples.length;i++)if(samples[i-1].kmh!==null&&samples[i].kmh!==null){distanceKm+=(samples[i-1].kmh+samples[i].kmh)/2/3600;validSeconds++;}
  const makeDetailSamples=(start,end,paddingBefore=24,paddingAfter=36)=>{
    const detailStart=Math.max(0,start-paddingBefore),detailEnd=Math.min(duration,end+paddingAfter);
    return Array.from({length:Math.round((detailEnd-detailStart)*5)+1},(_,j)=>{const second=detailStart+j/5;return {second,kmh:second>gapStart&&second<gapEnd?null:Math.round(speedAt(second)*10)/10};});
  };
  const stopEvents=stops.map((t,i)=>({t,i})).filter(p=>hardIndices.has(p.i)).map((p,i)=>{
    const start=p.t-brakeDuration(p.i),detailStart=Math.max(0,start-24),detailEnd=Math.min(duration,p.t+36);
    const detailSamples=makeDetailSamples(start,p.t);
    const finalWindow=detailSamples.filter(point=>point.second>=p.t-5&&point.second<=p.t&&point.kmh!==null);
    let peakDecelerationMps2=0;
    for(let j=1;j<finalWindow.length;j++){
      const deltaSeconds=finalWindow[j].second-finalWindow[j-1].second;
      peakDecelerationMps2=Math.max(peakDecelerationMps2,(finalWindow[j-1].kmh-finalWindow[j].kmh)/3.6/deltaSeconds);
    }
    return {id:'stop-'+i,type:'stop',start,end:p.t,before:samples[start].kmh,after:0,peakDecelerationMps2:Math.round(peakDecelerationMps2*100)/100,detailSamples};
  });
  const startEvents=stops.slice(0,-1).map((stop,index)=>{
    const nextIndex=index+1,start=stop+dwellFor(nextIndex),end=Math.min(duration,start+accelerationDuration(nextIndex));
    if(start>gapStart-2&&start<gapEnd+2)return null;
    const detailSamples=makeDetailSamples(start,end,12,24),window=detailSamples.filter(point=>point.second>=start&&point.second<=end&&point.kmh!==null);
    let peakAccelerationMps2=0;for(let j=1;j<window.length;j++){const dt=window[j].second-window[j-1].second;peakAccelerationMps2=Math.max(peakAccelerationMps2,(window[j].kmh-window[j-1].kmh)/3.6/dt);}
    return {id:'start-'+index,type:'start',start,end,before:0,after:samples[end]?.kmh??0,peakAccelerationMps2:Math.round(peakAccelerationMps2*100)/100,detailSamples};
  }).filter(Boolean).slice(0,rapidStartCount);
  const repeatEvents=repeatCenters.map((center,index)=>{
    const start=center-14,end=center+14,detailSamples=makeDetailSamples(start,end,8,8),window=detailSamples.filter(point=>point.second>=start&&point.second<=end&&point.kmh!==null),speeds=window.map(point=>point.kmh);
    return {id:'repeat-'+index,type:'repeat',start,end,before:samples[start]?.kmh??0,after:samples[end]?.kmh??0,minKmh:Math.min(...speeds),maxKmh:Math.max(...speeds),cycles:2,detailSamples};
  });
  return {origin:'SYNTHETIC',calibration:{dataset:'FHWA NGSIM Peachtree Street',sampleRateHz:10,vehicleIds:[37,55,56,61,68]},sampleIntervalSeconds:1,detailSampleIntervalSeconds:.2,samples,events:stopEvents,stopEvents,startEvents,repeatEvents,gap:{start:gapStart,end:gapEnd},distanceKm,validSeconds,maxKmh:Math.max(...samples.map(p=>p.kmh??0))};
}
function elapsedTime(seconds){return Math.floor(seconds/60)+':'+String(Math.floor(seconds%60)).padStart(2,'0');}
function clockTime(trip,second){const match=trip.date.match(/(\d{2}):(\d{2})/);const minutes=Number(match[1])*60+Number(match[2])+Math.floor(second/60);return String(Math.floor(minutes/60)%24).padStart(2,'0')+':'+String(minutes%60).padStart(2,'0');}
let selectedJourneyMovement='stop';
const journeyMovementMeta={stop:{label:'정차 전 큰 감속',short:'감속'},start:{label:'출발 직후 빠른 가속',short:'빠른 가속'},repeat:{label:'반복 가감속',short:'반복 가감속'}};
function journeyEventGroups(replay){return {stop:replay.stopEvents||replay.events||[],start:replay.startEvents||[],repeat:replay.repeatEvents||[]};}
function journeyEventValue(event){if(event.type==='start')return `${Math.round(event.before)} → ${Math.round(event.after)}`;if(event.type==='repeat')return `${Math.round(event.minKmh)} ↔ ${Math.round(event.maxKmh)}`;return `${Math.round(event.before)} → 0`;}
function journeyChart(trip){
  const r=trip.replay,total=trip.duration*60,x=t=>54+t/total*916,y=v=>221-v/80*188;
  const groups=journeyEventGroups(r);if(!groups[selectedJourneyMovement]?.length)selectedJourneyMovement='stop';const activeEvents=groups[selectedJourneyMovement],meta=journeyMovementMeta[selectedJourneyMovement];
  let path='',open=false;for(const p of r.samples){if(p.kmh===null){open=false;continue;}path+=(open?'L':'M')+x(p.second).toFixed(1)+' '+y(p.kmh).toFixed(1)+' ';open=true;}
  const axis=[0,20,40,60,80].map(v=>`<line x1="54" x2="970" y1="${y(v)}" y2="${y(v)}" class="speed-grid"/><text x="12" y="${y(v)+4}">${v}</text>`).join('');
  const tickSeconds=Array.from({length:Math.floor(total/600)+1},(_,i)=>i*600);if(total-tickSeconds.at(-1)<180)tickSeconds.pop();tickSeconds.push(total);
  const ticks=tickSeconds.map(second=>`<text x="${x(second)}" y="252" text-anchor="${second===0?'start':second===total?'end':'middle'}">${clockTime(trip,second)}</text>`).join('');
  const markerSvg=activeEvents.map((event,index)=>{const second=event.type==='start'?event.end:event.type==='repeat'?(event.start+event.end)/2:event.start,speed=event.type==='start'?event.after:event.type==='repeat'?(event.minKmh+event.maxKmh)/2:event.before;return `<g class="trace-event ${event.type}" data-chart-event="${index}" data-chart-type="${event.type}" role="button" tabindex="0" aria-label="${clockTime(trip,event.start)} ${meta.label} ${journeyEventValue(event)}km/h"><line x1="${x(second)}" x2="${x(second)}" y1="24" y2="221"/><circle cx="${x(second)}" cy="${y(speed)}" r="5"/><rect x="${x(second)-11}" y="6" width="22" height="19" rx="6"/><text x="${x(second)}" y="20" text-anchor="middle">${index+1}</text></g>`;}).join('');
  const tabs=Object.entries(journeyMovementMeta).map(([type,item])=>`<button data-movement-type="${type}" class="${type===selectedJourneyMovement?'selected':''}" aria-pressed="${type===selectedJourneyMovement}">${item.short}<b>${groups[type].length}</b></button>`).join('');
  const cards=activeEvents.map((event,index)=>`<button data-chart-event="${index}" data-chart-type="${event.type}" aria-pressed="false"><span>${index+1}</span>${clockTime(trip,event.start)}<small>${journeyEventValue(event)}</small></button>`).join('');
  return `<div class="speed-heading"><h3>주행 속도와 움직임 구간</h3><span>km/h</span></div><div class="trace-legend"><span><i></i>주행 속도</span><span><i class="movement ${selectedJourneyMovement}"></i>${meta.label} ${activeEvents.length}회</span><span><i class="missing"></i>기록 없음 1분</span></div><div class="trace-wrap"><svg class="speed-chart" id="journeyChart" viewBox="0 0 1000 275" preserveAspectRatio="none" role="img" aria-label="${trip.date}부터 ${trip.duration}분 동안의 주행 속도. 선택 항목 ${meta.label} ${activeEvents.length}회"><rect x="${x(r.gap.start)}" y="28" width="${x(r.gap.end)-x(r.gap.start)}" height="193" fill="#95afca12"/>${axis}<path class="journey-trace" d="${path}"/>${markerSvg}${ticks}<line id="traceCrosshair" x1="54" x2="54" y1="28" y2="221" style="display:none"/><circle id="traceCursor" r="5" style="display:none"/></svg><div class="trace-readout" id="traceReadout">${clockTime(trip,0)} · 0km/h</div></div><div class="trace-summary"><span>주행 <b>${trip.duration}분</b></span><span>분석 <b>${Math.round(r.validSeconds/60)}분</b></span><span>최고 속도 <b>${Math.round(r.maxKmh)}km/h</b></span><span>가상 운행 <b>NGSIM 실차 감속 형태 보정</b></span></div><div class="movement-tabs" role="tablist" aria-label="속도 기반 움직임 선택">${tabs}</div><div class="braking-events">${cards}</div><div class="braking-detail" id="brakingDetail"><span>${meta.label} 구간을 선택하세요</span></div>`;
}
function selectJourneyEvent(index,type=selectedJourneyMovement){
  selectedJourneyMovement=type;const t=demo.trips.find(t=>t.id===selectedTrip),e=journeyEventGroups(t.replay)[type]?.[index];if(!e)return;
  document.querySelectorAll('[data-chart-event]').forEach(n=>{const active=Number(n.dataset.chartEvent)===index&&n.dataset.chartType===type;n.classList.toggle('chosen',active);if(n.tagName==='BUTTON')n.setAttribute('aria-pressed',String(active));});
  const values=e.type==='repeat'?`<b>${Math.round(e.minKmh)}<small>km/h</small></b><span>↔</span><b>${Math.round(e.maxKmh)}<small>km/h</small></b><em>${e.cycles}회 반복</em>`:`<b>${Math.round(e.before)}<small>km/h</small></b><span>→</span><b>${Math.round(e.after)}<small>km/h</small></b><em>${e.end-e.start}초 동안</em>`;
  document.querySelector('#brakingDetail').innerHTML=`<div><small>${journeyMovementMeta[type].label} · ${index+1}</small><strong>${clockTime(t,e.start)} <span>· 출발 후 ${elapsedTime(e.start)}</span></strong></div><div class="braking-values">${values}</div>`;
}
document.addEventListener('click',e=>{const tab=e.target.closest('[data-movement-type]');if(tab){selectedJourneyMovement=tab.dataset.movementType;renderRecords();return;}const button=e.target.closest('[data-chart-event]');if(button)selectJourneyEvent(Number(button.dataset.chartEvent),button.dataset.chartType);});
document.addEventListener('keydown',e=>{const button=e.target.closest('g[data-chart-event]');if(button&&(e.key==='Enter'||e.key===' ')){e.preventDefault();selectJourneyEvent(Number(button.dataset.chartEvent),button.dataset.chartType);}});
document.addEventListener('pointermove',e=>{
  const svg=e.target.closest('#journeyChart');if(!svg)return;
  const point=new DOMPoint(e.clientX,e.clientY).matrixTransform(svg.getScreenCTM().inverse());if(point.x<54||point.x>970)return;
  const trip=demo.trips.find(t=>t.id===selectedTrip),second=Math.round((point.x-54)/916*trip.duration*60),sample=trip.replay.samples[second];if(!sample)return;
  const line=document.querySelector('#traceCrosshair'),cursor=document.querySelector('#traceCursor');line.style.display='';line.setAttribute('x1',point.x);line.setAttribute('x2',point.x);
  cursor.style.display=sample.kmh===null?'none':'';cursor.setAttribute('cx',point.x);cursor.setAttribute('cy',221-(sample.kmh??0)/80*188);
  document.querySelector('#traceReadout').textContent=clockTime(trip,second)+' · 출발 후 '+elapsedTime(second)+' · '+(sample.kmh===null?'기록 없음':Math.round(sample.kmh)+'km/h');
});
