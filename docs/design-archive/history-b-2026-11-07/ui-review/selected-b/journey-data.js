/* Deterministic, synthetic 1 Hz replay fixture. This is not a vehicle acquisition or detector implementation. */
function buildJourneyReplay(durationMinutes, hardStopCount, rapidStartCount, variant) {
  const duration=durationMinutes*60, count=hardStopCount+4;
  const weights=[.75,1.32,.67,1.18,1.45,.92,1.28,.85,.78,1.16,.95,1.38,1.08];
  const totalWeight=weights.slice(0,count).reduce((a,b)=>a+b,0);let cumulative=0;
  const stops=weights.slice(0,count).map(w=>{cumulative+=w;return Math.round(duration*cumulative/totalWeight/5)*5;});
  const hardIndices=new Set(Array.from({length:hardStopCount},(_,i)=>Math.floor(i*(stops.length-1)/hardStopCount)));
  const gapIndex=Math.floor(stops.length/2)-1;
  const gapStart=Math.round((stops[gapIndex]+stops[gapIndex+1])/2)-30, gapEnd=gapStart+60;
  const cruise=[[0,37],[.12,48],[.27,55],[.36,32],[.43,22],[.48,43],[.63,58],[.77,51],[.86,39],[1,34]];
  const base=t=>{const fraction=t/duration;let j=1;while(j<cruise.length-1&&cruise[j][0]<fraction)j++;const a=cruise[j-1],b=cruise[j],blend=(fraction-a[0])/(b[0]-a[0]);return a[1]+(b[1]-a[1])*blend+2*Math.sin(t/37)+1.3*Math.sin(t/13)+variant*.6;};
  const brakeDuration=index=>hardIndices.has(index)?Math.max(2,Math.min(5,Math.floor(base(stops[index]-5)/8.2))):25;
  function speedAt(t){
    const nextIndex=stops.findIndex(s=>s>=t), next=stops[nextIndex]??duration;
    const last=nextIndex>0?stops[nextIndex-1]:0, since=t-last;
    const dwell=nextIndex===0?3:[18,34,21,45,14,28,22,31][(nextIndex-1)%8];
    const brakeSeconds=brakeDuration(nextIndex);
    if(t===0||t===duration||since<=dwell)return 0;
    if(next-t<=brakeSeconds){
      const u=1-(next-t)/brakeSeconds;
      const pressure=.08*u+.92*(3*u*u-2*u*u*u);
      return Math.max(0,base(next-brakeSeconds)*(1-pressure));
    }
    const accelerationSeconds=nextIndex>0&&nextIndex<=rapidStartCount?4:25;
    if(since<dwell+accelerationSeconds){const u=(since-dwell)/accelerationSeconds;return base(t)*(.1*u+.9*(3*u*u-2*u*u*u));}
    return base(t);
  }
  const samples=Array.from({length:duration+1},(_,t)=>({second:t,kmh:t>gapStart&&t<gapEnd?null:Math.round(speedAt(t)*10)/10}));
  let distanceKm=0,validSeconds=0;
  for(let i=1;i<samples.length;i++)if(samples[i-1].kmh!==null&&samples[i].kmh!==null){distanceKm+=(samples[i-1].kmh+samples[i].kmh)/2/3600;validSeconds++;}
  const events=stops.map((t,i)=>({t,i})).filter(p=>hardIndices.has(p.i)).map((p,i)=>{
    const start=p.t-brakeDuration(p.i),detailStart=Math.max(0,start-24),detailEnd=Math.min(duration,p.t+36);
    const detailSamples=Array.from({length:(detailEnd-detailStart)*5+1},(_,j)=>{
      const second=detailStart+j/5;
      return {second,kmh:second>gapStart&&second<gapEnd?null:Math.round(speedAt(second)*10)/10};
    });
    return {id:'stop-'+i,type:'stop',start,end:p.t,before:samples[start].kmh,after:0,detailSamples};
  });
  return {origin:'SYNTHETIC',sampleIntervalSeconds:1,detailSampleIntervalSeconds:.2,samples,events,gap:{start:gapStart,end:gapEnd},distanceKm,validSeconds,maxKmh:Math.max(...samples.map(p=>p.kmh??0))};
}
function elapsedTime(seconds){return Math.floor(seconds/60)+':'+String(Math.floor(seconds%60)).padStart(2,'0');}
function clockTime(trip,second){const match=trip.date.match(/(\d{2}):(\d{2})/);const minutes=Number(match[1])*60+Number(match[2])+Math.floor(second/60);return String(Math.floor(minutes/60)%24).padStart(2,'0')+':'+String(minutes%60).padStart(2,'0');}
function journeyChart(trip){
  const r=trip.replay,total=trip.duration*60,x=t=>54+t/total*916,y=v=>221-v/80*188;
  let path='',open=false;for(const p of r.samples){if(p.kmh===null){open=false;continue;}path+=(open?'L':'M')+x(p.second).toFixed(1)+' '+y(p.kmh).toFixed(1)+' ';open=true;}
  const axis=[0,20,40,60,80].map(v=>`<line x1="54" x2="970" y1="${y(v)}" y2="${y(v)}" class="speed-grid"/><text x="12" y="${y(v)+4}">${v}</text>`).join('');
  const tickSeconds=Array.from({length:Math.floor(total/600)+1},(_,i)=>i*600);if(total-tickSeconds.at(-1)<180)tickSeconds.pop();tickSeconds.push(total);
  const ticks=tickSeconds.map(second=>`<text x="${x(second)}" y="252" text-anchor="${second===0?'start':second===total?'end':'middle'}">${clockTime(trip,second)}</text>`).join('');
  return `<div class="speed-heading"><h3>주행 속도와 감속 구간</h3><span>km/h</span></div><div class="trace-legend"><span><i></i>주행 속도</span><span><i class="braking"></i>정차 전 큰 감속 ${r.events.length}회</span><span><i class="missing"></i>기록 없음 1분</span></div><div class="trace-wrap"><svg class="speed-chart" id="journeyChart" viewBox="0 0 1000 275" preserveAspectRatio="none" role="img" aria-label="${trip.date}부터 ${trip.duration}분 동안의 주행 속도. 세로축 km/h, 가로축 시각. 큰 감속 ${r.events.length}회"><rect x="${x(r.gap.start)}" y="28" width="${x(r.gap.end)-x(r.gap.start)}" height="193" fill="#95afca12"/>${axis}<path class="journey-trace" d="${path}"/>${r.events.map((e,i)=>`<g class="trace-event" data-chart-event="${i}" role="button" tabindex="0" aria-label="${clockTime(trip,e.start)} 감속 ${e.before}에서 0km/h"><line x1="${x(e.start)}" x2="${x(e.start)}" y1="24" y2="221"/><circle cx="${x(e.start)}" cy="${y(e.before)}" r="5"/><rect x="${x(e.start)-11}" y="6" width="22" height="19" rx="6"/><text x="${x(e.start)}" y="20" text-anchor="middle">${i+1}</text></g>`).join('')}${ticks}<line id="traceCrosshair" x1="54" x2="54" y1="28" y2="221" style="display:none"/><circle id="traceCursor" r="5" style="display:none"/></svg><div class="trace-readout" id="traceReadout">${clockTime(trip,0)} · 0km/h</div></div><div class="trace-summary"><span>주행 <b>${trip.duration}분</b></span><span>분석 <b>${Math.round(r.validSeconds/60)}분</b></span><span>최고 속도 <b>${Math.round(r.maxKmh)}km/h</b></span></div><div class="braking-heading">감속 구간 </div><div class="braking-events">${r.events.map((e,i)=>`<button data-chart-event="${i}" aria-pressed="false"><span>${i+1}</span>${clockTime(trip,e.start)}<small>${Math.round(e.before)} → 0</small></button>`).join('')}</div><div class="braking-detail" id="brakingDetail"><span>감속 구간을 선택하세요</span></div>`;
}
function selectJourneyEvent(index){
  const t=demo.trips.find(t=>t.id===selectedTrip),e=t.replay.events[index];if(!e)return;
  document.querySelectorAll('[data-chart-event]').forEach(n=>{const active=Number(n.dataset.chartEvent)===index;n.classList.toggle('chosen',active);if(n.tagName==='BUTTON')n.setAttribute('aria-pressed',String(active));});
  document.querySelector('#brakingDetail').innerHTML=`<div><small>정차 전 큰 감속 · ${index+1}</small><strong>${clockTime(t,e.start)} <span>· 출발 후 ${elapsedTime(e.start)}</span></strong></div><div class="braking-values"><b>${Math.round(e.before)}<small>km/h</small></b><span>→</span><b>0<small>km/h</small></b><em>${e.end-e.start}초 동안</em></div>`;
}
document.addEventListener('click',e=>{const button=e.target.closest('[data-chart-event]');if(button)selectJourneyEvent(Number(button.dataset.chartEvent));});
document.addEventListener('keydown',e=>{const button=e.target.closest('g[data-chart-event]');if(button&&(e.key==='Enter'||e.key===' ')){e.preventDefault();selectJourneyEvent(Number(button.dataset.chartEvent));}});
document.addEventListener('pointermove',e=>{
  const svg=e.target.closest('#journeyChart');if(!svg)return;
  const point=new DOMPoint(e.clientX,e.clientY).matrixTransform(svg.getScreenCTM().inverse());if(point.x<54||point.x>970)return;
  const trip=demo.trips.find(t=>t.id===selectedTrip),second=Math.round((point.x-54)/916*trip.duration*60),sample=trip.replay.samples[second];if(!sample)return;
  const line=document.querySelector('#traceCrosshair'),cursor=document.querySelector('#traceCursor');line.style.display='';line.setAttribute('x1',point.x);line.setAttribute('x2',point.x);
  cursor.style.display=sample.kmh===null?'none':'';cursor.setAttribute('cx',point.x);cursor.setAttribute('cy',221-(sample.kmh??0)/80*188);
  document.querySelector('#traceReadout').textContent=clockTime(trip,second)+' · 출발 후 '+elapsedTime(second)+' · '+(sample.kmh===null?'기록 없음':Math.round(sample.kmh)+'km/h');
});
