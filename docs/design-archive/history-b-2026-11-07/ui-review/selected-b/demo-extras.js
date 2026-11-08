/* Isolated browser fixtures. No vehicle, network, navigation or environment service is invoked. */
const demoEnvironment = {
  capturedAt: '2026-11-04 08:10',
  cabinTemperature: 22.0, cabinSetTemperature: 22.0, outsideTemperature: 16.0,
  pm25: 28, pm10: 42, grade: '보통', recirculation: false, airCleaner: true,
};
const routeFixtures = window.MOTION_ROUTE_MAP.routes;
const originalDemoFixture=JSON.parse(JSON.stringify(demo));
let environmentMode='available',chosenRoute='comfortable',appliedRoute=null;
const main=document.querySelector('main');
main.insertAdjacentHTML('beforeend','<section class="screen" id="screen-environment"></section><section class="screen" id="screen-routes"></section>');

function environmentValue(value,unit='°C') {
  return environmentMode==='unsupported'?'—':environmentMode==='loading'?'확인 중':value.toFixed(1)+unit;
}
function renderEnvironment() {
  const mode=environmentMode,missing=['unsupported','permission','network','loading'].includes(mode),stale=mode==='stale';
  const note={available:'',stale:'오래된 예측값이에요. 마지막 업데이트 시각을 확인하세요.',permission:'위치 권한이 없어 바깥 대기질을 확인할 수 없어요. 차량 공조 정보는 별도로 표시해요.',network:'네트워크에 연결하지 못했어요. 저장된 값이 없는 상황이므로 대기질은 표시하지 않아요.',unsupported:'이 기기에서 지원하지 않는 값이에요. ',loading:'아직 값을 받지 못했어요. '}[mode];
  document.querySelector('#screen-environment').innerHTML=`<div class="extras-head"><div><button class="btn back small" data-go="home">← 출발 안내</button><div class="kicker" style="margin-top:18px">BEFORE WE GO</div><h1>출발 전, 공기까지 살펴봐요.</h1><p>실내 공조와 바깥 대기질은 서로 다른 정보예요.</p></div></div><div class="environment-layout"><article class="climate-display"><div class="climate-ring"></div><small>실내 온도 · 차량 공조 측정 항목</small><div class="climate-reading">${['unsupported','loading'].includes(mode)?'<span style="font-size:40px;letter-spacing:-1px">'+(mode==='loading'?'확인 중':'미지원')+'</span>':demoEnvironment.cabinTemperature.toFixed(1)+'<small>°C</small>'}</div><div class="climate-secondary"><div><span>설정 온도 · 운전석</span><b>${environmentValue(demoEnvironment.cabinSetTemperature)}</b></div><div><span>바깥 온도 · 차량</span><b>${environmentValue(demoEnvironment.outsideTemperature)}</b></div></div><p class="environment-reading-note">측정 온도와 설정 온도는 다른 값이에요.<br>기준 시각 ${demoEnvironment.capturedAt}</p></article><div class="environment-right"><article class="panel air-card"><div class="air-title"><h2>바깥 대기질</h2><span class="tag">${missing?{permission:'권한 없음',network:'연결 필요',unsupported:'미지원',loading:'확인 중'}[mode]:stale?'오래된 값':demoEnvironment.grade+''}</span></div><div class="air-numbers"><div><small>PM2.5</small><strong>${missing?'—':demoEnvironment.pm25}<em>µg/m³</em></strong></div><div><small>PM10</small><strong>${missing?'—':demoEnvironment.pm10}<em>µg/m³</em></strong></div></div><p class="availability-note" role="status">${note}</p><p class="source-note">대기질: Open-Meteo, CAMS 예측<br>${missing?'예측 시각 없음':'예측 시각 '+(stale?'2026-11-04 04:00':demoEnvironment.capturedAt)}</p></article><article class="panel climate-status"><h3>현재 공조 상태</h3><div class="environment-row"><span>순환 모드</span><b>${mode==='unsupported'?'미지원':mode==='loading'?'확인 중':'외기 유입'}</b></div><div class="environment-row"><span>공기청정</span><b>${mode==='unsupported'?'미지원':mode==='loading'?'확인 중':'켜짐'}</b></div><div class="environment-row"><span>실내 PM2.5 · CO₂</span><b>지원 신호 없음</b></div></article></div></div><div class="environment-bottom"><p></p><button class="btn" data-go="routes">다음 · 경로 살펴보기 ↗</button></div>`;
}
function mapSvg(){
  const data=window.MOTION_ROUTE_MAP;if(!data)return '<div class="map-unavailable">지도를 불러오지 못했어요.</div>';
  const merc=p=>[p[0]*Math.PI/180,Math.log(Math.tan(Math.PI/4+p[1]*Math.PI/360))];
  const a=merc([data.bounds[0],data.bounds[1]]),b=merc([data.bounds[2],data.bounds[3]]),scale=Math.min(940/(b[0]-a[0]),690/(b[1]-a[1])),cx=(a[0]+b[0])/2,cy=(a[1]+b[1])/2;
  const project=p=>{const q=merc(p);return [500+(q[0]-cx)*scale,370-(q[1]-cy)*scale];};
  const path=coords=>coords.map((p,i)=>{const q=project(p);return(i?'L':'M')+q[0].toFixed(1)+' '+q[1].toFixed(1);}).join(' ');
  const widths={motorway:7,trunk:7,primary:6,secondary:5,tertiary:4,residential:2,unclassified:2,living_street:1.5,service:1.4};
  const roads=data.features.filter(f=>f.tags.highway).sort((a,b)=>(widths[a.tags.highway]||3)-(widths[b.tags.highway]||3));
  const shapes=data.features.filter(f=>!f.tags.highway).map(f=>`<path d="${path(f.coordinates)}" class="${f.tags.waterway?'osm-waterline':f.tags.natural==='water'?'osm-water':f.tags.leisure==='park'?'osm-park':'osm-land'}"/>`).join('');
  const roadSvg=roads.map(f=>{const w=widths[f.tags.highway]||3;return `<path d="${path(f.coordinates)}" stroke-width="${w+2}" class="osm-road-case"/><path d="${path(f.coordinates)}" stroke-width="${w}" class="osm-road"/>`;}).join('');
  const routeSvg=data.routes.slice().sort((a,b)=>(a.id===chosenRoute?1:0)-(b.id===chosenRoute?1:0)).map(r=>`<path d="${path(r.coordinates)}" class="route-casing"/><path d="${path(r.coordinates)}" class="real-route ${r.id===chosenRoute?'active':'alternative'}"/>`).join('');
  const selected=data.routes.find(r=>r.id===chosenRoute),start=project(selected.coordinates[0]),end=project(selected.coordinates.at(-1));
  const stations=data.places.map(p=>{const q=project(p.point);return `<g class="map-station"><circle cx="${q[0]}" cy="${q[1]}" r="4"/><text x="${q[0]+10}" y="${q[1]+4}">${escapeHtml(p.name)}</text></g>`;}).join('');
  const marks=selected.turnPoints.map(p=>{const q=project(p);return `<circle cx="${q[0]}" cy="${q[1]}" r="7" class="route-turn"><title>좌·우회전 지점</title></circle>`;}).join('');
  return `<svg viewBox="0 0 1000 760" role="img" aria-label="양재역에서 봉은사역 부근까지 실제 도로와 경로. 선택 경로는 ${selected.title}"><defs><radialGradient id="mapLight"><stop stop-color="#254363"/><stop offset="1" stop-color="#111e32"/></radialGradient></defs><rect width="1000" height="760" fill="url(#mapLight)"/>${shapes}${roadSvg}${routeSvg}${marks}${stations}<circle cx="${start[0]}" cy="${start[1]}" r="17" class="map-start-halo"/><circle cx="${start[0]}" cy="${start[1]}" r="8" class="map-endpoint"/><g transform="translate(${end[0]},${end[1]})"><path d="M0 10C-25-10-18-34 0-34S25-10 0 10" fill="#bceaff"/><circle cy="-17" r="6" fill="#16324e"/></g><text x="${start[0]+24}" y="${start[1]+6}" class="endpoint-label">출발</text><text x="${end[0]+24}" y="${end[1]-14}" class="endpoint-label">도착</text><g class="map-north" transform="translate(940,50)"><text y="-13" text-anchor="middle">N</text><path d="M0-4-8 18 0 12 8 18Z"/></g></svg>`;
}
function renderRoutes(){
  const selected=routeFixtures.find(r=>r.id===chosenRoute),standard=routeFixtures.find(r=>r.id==='standard'),comfortable=routeFixtures.find(r=>r.id==='comfortable');
  const saving=standard.turns-comfortable.turns,extra=(comfortable.km-standard.km).toFixed(1);
  document.querySelector('#screen-routes').innerHTML=`<div class="extras-head"><div><button class="btn back small" data-go="home">← 출발 안내</button><div class="kicker" style="margin-top:18px">ROUTE COMPARISON</div><h1>회전을 줄여, 조금 더 여유롭게.</h1><p>양재역 부근 <span class="destination-arrow">→</span> 봉은사역 부근</p></div></div><div class="route-layout"><div class="route-map">${mapSvg()}<div class="map-route-label"><i></i>${selected.title}<span>좌·우회전 ${selected.turns}회</span></div><a class="map-credit" href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">© OpenStreetMap contributors</a></div><div class="route-options">${routeFixtures.map(r=>`<button class="route-choice ${chosenRoute===r.id?'selected':''}" data-route-choice="${r.id}" aria-pressed="${chosenRoute===r.id}"><div class="route-choice-top"><h2>${r.title}</h2><span class="route-radio" aria-hidden="true">${chosenRoute===r.id?'●':'○'}</span></div><strong>${r.minutes}<small>분 · ${r.km.toFixed(1)}km</small></strong><div class="route-facts"><span>좌·우회전 <b>${r.turns}회</b></span><span>소요시간 <small>교통 미반영</small></span></div><p>${r.roadNames.filter(n=>!n.endsWith('350길')).slice(0,3).join(' → ')}</p></button>`).join('')}<div class="route-insight"><i>↱</i><div><strong>좌·우회전 ${saving}회 적게</strong><p>짧은 길보다 ${extra}km 더 이동해요.</p></div></div><div class="route-condition"><span>방지턱·노면 상태</span><b>확인할 정보 없음</b></div><button class="advice-ribbon route-action" data-apply-route><div class="advice-copy"><small>${selected.title} · ${selected.km.toFixed(1)}km</small><h2>${appliedRoute===chosenRoute?'선택한 경로':'이 경로 선택'}</h2></div><span class="advice-action">${appliedRoute===chosenRoute?'✓':'↗'}</span></button><p class="route-feedback" id="routeFeedback" role="status">${appliedRoute?'선택한 경로 · '+routeFixtures.find(r=>r.id===appliedRoute).title:''}</p></div></div>`;
}

function updateRatingWord(){const el=document.querySelector('#ratingWord');if(el)el.textContent=['어땠나요?','많이 불편했어요','조금 불편했어요','보통이었어요','편안했어요','아주 편안했어요'][chosenRating||0];}
function renderExtras(name){
  if(name==='environment')renderEnvironment();
  if(name==='routes')renderRoutes();
  if(name==='feedback')updateRatingWord();
}
function escapeHtml(value){return String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function openProfileEditor(){
  document.querySelector('.profile-editor')?.remove();
  const person=demo.people[selectedPerson],container=document.createElement('div');container.className='profile-editor';container.setAttribute('role','dialog');container.setAttribute('aria-modal','true');container.setAttribute('aria-labelledby','editorTitle');
  container.innerHTML=`<form id="profileForm"><span class="kicker">PERSONAL COMFORT</span><h2 id="editorTitle">프로필 편집</h2><label>표시할 이름<input name="displayName" value="${escapeHtml(person.name)}" maxlength="20" required></label><label>등록한 민감도<select name="sensitivity">${['낮음','보통','높음'].map(v=>`<option ${person.sensitivity===v?'selected':''}>${v}</option>`).join('')}</select></label>${person.kind==='어린이'||person.id!=='driver'?`<label>선호 온도 · °C<input name="temperature" type="number" min="16" max="32" step="0.5" value="${parseFloat(person.temp)||22}" required></label><label>선호 휴식 간격 · 분<input name="rest" type="number" min="5" max="180" value="${parseInt(person.rest)||20}" required></label>`:''}<div class="editor-actions"><button type="button" class="btn" data-close-editor>취소</button><button class="btn primary" type="submit">저장</button></div></form>`;
  document.body.append(container);container.querySelector('input').focus();
  container.addEventListener('click',e=>{if(e.target===container||e.target.closest('[data-close-editor]'))container.remove();});
  container.querySelector('form').addEventListener('submit',e=>{e.preventDefault();const data=new FormData(e.target),oldName=person.name;person.name=data.get('displayName').trim().slice(0,20);if(!person.name)return;
    // Names are rendered as HTML in the original prototype; escape at the boundary.
    person.name=escapeHtml(person.name);person.sensitivity=data.get('sensitivity');if(data.has('temperature')){person.temp=data.get('temperature')+'°C';person.rest=data.get('rest')+'분';}
    demo.trips.forEach(t=>{if(t.driver===oldName)t.driver=person.name;if(t.passenger===oldName)t.passenger=person.name;});
    renderProfile();renderSeats();renderSelector();renderHome();renderRecords();container.remove();});
}
document.addEventListener('click',e=>{
  const route=e.target.closest('[data-route-choice]');if(route){chosenRoute=route.dataset.routeChoice;renderRoutes();}
  if(e.target.closest('[data-apply-route]')){appliedRoute=chosenRoute;renderRoutes();}
  if(e.target.closest('[data-route-failure]')){go('routes');document.querySelector('#routeFeedback').textContent='내비와 연결하지 못했어요. 기존 경로를 유지해요.';document.querySelector('.demo-tools').open=false;}
  if(e.target.closest('[data-demo-reset]')){
    seatAssignments={...defaultSeats};chosenRating=null;chosenReasons.clear();environmentMode='available';chosenRoute='comfortable';appliedRoute=null;
    demo.people=JSON.parse(JSON.stringify(originalDemoFixture.people));demo.trips=JSON.parse(JSON.stringify(originalDemoFixture.trips));selectedTrip='j1';selectedPerson='jiyoung';
    ['motion-care-demo-seats','motion-care-b-feedback'].forEach(k=>localStorage.removeItem(k));
    document.querySelector('#environmentMode').value='available';renderHome();renderRecords();renderFeedback();go('home');
    document.querySelector('.demo-tools').open=false;
  }
  if(e.target.closest('[data-rating]'))updateRatingWord();
  if(e.target.closest('[data-go]'))document.querySelector('.demo-tools').open=false;
});
document.querySelector('#environmentMode').addEventListener('change',e=>{environmentMode=e.target.value;renderEnvironment();});
document.addEventListener('keydown',e=>{if(e.key==='Escape')document.querySelector('.profile-editor')?.remove();});
// Scenario deep links are confined to this browser prototype.
const qs=new URLSearchParams(location.search);if(qs.has('environment')&&['available','stale','permission','network','unsupported','loading'].includes(qs.get('environment'))){environmentMode=qs.get('environment');document.querySelector('#environmentMode').value=environmentMode;}
if(qs.has('person')&&demo.people[qs.get('person')]){selectedPerson=qs.get('person');renderProfile();}
renderEnvironment();renderRoutes();updateRatingWord();
if(['environment','routes'].includes(location.hash.slice(1)))go(location.hash.slice(1));
