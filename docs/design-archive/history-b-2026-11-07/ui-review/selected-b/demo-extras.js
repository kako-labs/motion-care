/* Isolated browser fixtures. No vehicle, network, navigation or environment service is invoked. */
const demoEnvironment = {
  capturedAt: '2026-11-04 08:10',
  cabinTemperature: 22.0, cabinSetTemperature: 22.0, outsideTemperature: 16.0,
  pm25: 28, pm10: 42, grade: '보통', recirculation: false, airCleaner: true,
};
const routeFixtures = [
  {id:'standard',title:'기본 경로',minutes:32,km:18.2,description:'소요 시간과 이동 거리를 비교하는 기본 옵션의 화면 예시예요.'},
  {id:'comfortable',title:'편안한 경로',minutes:38,km:21.4,description:'COMFORTABLE 옵션의 표시 예시예요. 실제 지원·응답과 멀미 감소 효과는 확인 전이에요.'},
];
const originalDemoFixture=JSON.parse(JSON.stringify(demo));
let environmentMode='available',chosenRoute='comfortable',appliedRoute=null;
const main=document.querySelector('main');
main.insertAdjacentHTML('beforeend','<section class="screen" id="screen-environment"></section><section class="screen" id="screen-routes"></section>');

function environmentValue(value,unit='°C') {
  return environmentMode==='unsupported'?'—':environmentMode==='loading'?'확인 중':value.toFixed(1)+unit;
}
function renderEnvironment() {
  const mode=environmentMode,missing=['unsupported','permission','network','loading'].includes(mode),stale=mode==='stale';
  const note={available:'현재 위치 부근의 바깥 대기질을 보여주는 합성 예시예요.',stale:'오래된 예측값이에요. 현재 상태로 판단하지 않고 마지막 값과 시각을 함께 보여줘요.',permission:'위치 권한이 없어 바깥 대기질을 확인할 수 없어요. 차량 공조 정보는 별도로 표시해요.',network:'네트워크에 연결하지 못했어요. 저장된 값이 없는 상황이므로 대기질은 표시하지 않아요.',unsupported:'이 기기에서 지원하지 않는 값이에요. 0이나 다른 출처의 값으로 대신하지 않아요.',loading:'아직 값을 받지 못했어요. 확인 중에는 정상·좋음으로 표시하지 않아요.'}[mode];
  document.querySelector('#screen-environment').innerHTML=`<div class="extras-head"><div><button class="btn back small" data-go="home">← 출발 안내</button><div class="kicker" style="margin-top:18px">BEFORE WE GO</div><h1>출발 전, 공기까지 살펴봐요.</h1><p>실내 공조와 바깥 대기질은 서로 다른 정보예요.</p></div><span class="source-pill">합성 값 · 출처 표시 형식 예시</span></div><div class="environment-layout"><article class="climate-display"><div class="climate-ring"></div><small>실내 온도 · 차량 공조 측정 항목</small><div class="climate-reading">${['unsupported','loading'].includes(mode)?'<span style="font-size:40px;letter-spacing:-1px">'+(mode==='loading'?'확인 중':'미지원')+'</span>':demoEnvironment.cabinTemperature.toFixed(1)+'<small>°C</small>'}</div><div class="climate-secondary"><div><span>설정 온도 · 운전석</span><b>${environmentValue(demoEnvironment.cabinSetTemperature)}</b></div><div><span>바깥 온도 · 차량</span><b>${environmentValue(demoEnvironment.outsideTemperature)}</b></div></div><p class="environment-reading-note">측정 온도와 설정 온도는 다른 값이에요.<br>시연 값 기준 시각 ${demoEnvironment.capturedAt} · 실제 차량 수신 아님</p></article><div class="environment-right"><article class="panel air-card"><div class="air-title"><h2>바깥 대기질</h2><span class="tag">${missing?{permission:'권한 없음',network:'연결 필요',unsupported:'미지원',loading:'확인 중'}[mode]:stale?'오래된 값':demoEnvironment.grade+' · 예시'}</span></div><div class="air-numbers"><div><small>PM2.5</small><strong>${missing?'—':demoEnvironment.pm25}<em>µg/m³</em></strong></div><div><small>PM10</small><strong>${missing?'—':demoEnvironment.pm10}<em>µg/m³</em></strong></div></div><p class="availability-note" role="status">${note}</p><p class="source-note">표시 출처: 대기질: Open-Meteo, CAMS 예측<br>${missing?'예측 시각 없음':'예측 시각 예시 '+(stale?'2026-11-04 04:00':demoEnvironment.capturedAt)} · 실측값이 아니에요.<br>이 화면은 API를 호출하지 않고 고정 합성 값을 보여줘요.</p></article><article class="panel climate-status"><h3>현재 공조 상태</h3><div class="environment-row"><span>순환 모드</span><b>${mode==='unsupported'?'미지원':mode==='loading'?'확인 중':'외기 유입 · 예시'}</b></div><div class="environment-row"><span>공기청정</span><b>${mode==='unsupported'?'미지원':mode==='loading'?'확인 중':'켜짐 · 예시'}</b></div><div class="environment-row"><span>실내 PM2.5 · CO₂</span><b>지원 신호 없음</b></div></article></div></div><div class="environment-bottom"><p>온도·대기질로 멀미를 진단하거나 운전 패턴과 합쳐 점수를 만들지 않아요.<br>설정 변경과 차량 제어는 하지 않아요. 실제 앱에서는 각 값의 출처·시각·지원 상태를 그대로 연결해요.</p><button class="btn" data-go="routes">다음 · 경로 살펴보기 ↗</button></div>`;
}
function mapSvg(){return `<svg viewBox="0 0 620 420" role="img" aria-label="실제 도로가 아닌 두 경로 비교 개념도"><defs><filter id="mapGlow"><feGaussianBlur stdDeviation="4"/></filter></defs>${Array.from({length:12},(_,i)=>`<path class="map-grid" d="M${i*60} 0V420 M0 ${i*50}H620"/>`).join('')}<path class="map-road" d="M-20 340 170 250 240 100 450 70 650 120 M20 -10 130 90 300 210 410 270 650 300 M-30 170 160 160 320 100 520 200 650 390"/><path class="map-route" d="M110 320 210 245 285 250 330 185 400 190 500 100" stroke="${chosenRoute==='standard'?'#bceeff':'#68819f'}"/><path class="map-route" d="M110 320 C120 200 155 88 270 80 S430 68 500 100" stroke="${chosenRoute==='comfortable'?'#bceeff':'#68819f'}"/><circle cx="110" cy="320" r="9" fill="#d3f4ff"/><circle cx="110" cy="320" r="18" fill="none" stroke="#bceeff55"/><circle cx="500" cy="100" r="9" fill="#d3f4ff"/><text x="80" y="360">출발</text><text x="475" y="73">목적지</text><text x="205" y="59" style="font-size:11px">편안한 옵션 · 합성 경로</text><text x="322" y="278" style="font-size:11px">기본 옵션 · 합성 경로</text></svg>`}
function renderRoutes(){
  const selected=routeFixtures.find(r=>r.id===chosenRoute);
  document.querySelector('#screen-routes').innerHTML=`<div class="extras-head"><div><button class="btn back small" data-go="home">← 출발 안내</button><div class="kicker" style="margin-top:18px">A LITTLE MORE COMFORT</div><h1>오늘은 어떤 길로 갈까요?</h1><p>시간과 거리를 살펴보고, 이동에 맞는 옵션을 골라봐요.</p></div><span class="source-pill">경로 기능 콘셉트 · 합성 예시</span></div><div class="route-layout"><div class="route-map"><span class="map-label">CONCEPT MAP · 실제 지도 아님</span>${mapSvg()}<div class="map-legend"><span><i></i>선택한 옵션</span><span><i></i>비교 옵션</span></div></div><div class="route-options">${routeFixtures.map(r=>`<button class="route-choice ${chosenRoute===r.id?'selected':''}" data-route-choice="${r.id}" aria-pressed="${chosenRoute===r.id}"><div class="route-choice-top"><h2>${r.title}</h2><span class="tag">${chosenRoute===r.id?'선택됨':'비교'}</span></div><strong>${r.minutes}<small>분 · ${r.km.toFixed(1)}km</small></strong><p>${r.description}</p></button>`).join('')}<button class="advice-ribbon route-action" data-apply-route><div class="advice-copy"><small>시연에서 선택하기</small><h2>${selected.title}로 살펴보기</h2></div><span class="advice-action">선택 ↗</span></button><p class="route-feedback" id="routeFeedback" role="status">${appliedRoute?'시연에서 '+routeFixtures.find(r=>r.id===appliedRoute).title+'를 선택했어요. 실제 내비 경로는 변경하지 않았어요.':'옵션을 고르면 비교 경로가 강조돼요.'}</p><button class="text-action" data-route-failure>내비 연결 실패 상황 보기 ↗</button></div></div><p class="route-footer"><b>편안한 옵션이 멀미가 적은 길을 보장하지는 않아요.</b><br>시간·거리·선은 모두 합성 예시예요. NaviHelper의 실제 옵션 지원·탐색 응답·내비 연결은 검증 전이며 자체 도로 위험 점수는 만들지 않았어요. 연결 실패 시 기존 내비 경로를 유지해요.</p>`;
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
  container.innerHTML=`<form id="profileForm"><span class="kicker">PERSONAL COMFORT</span><h2 id="editorTitle">프로필 편집</h2><label>표시할 이름<input name="displayName" value="${escapeHtml(person.name)}" maxlength="20" required></label><label>등록한 민감도<select name="sensitivity">${['낮음','보통','높음'].map(v=>`<option ${person.sensitivity===v?'selected':''}>${v}</option>`).join('')}</select></label>${person.kind==='어린이'||person.id!=='driver'?`<label>선호 온도 · °C<input name="temperature" type="number" min="16" max="32" step="0.5" value="${parseFloat(person.temp)||22}" required></label><label>선호 휴식 간격 · 분<input name="rest" type="number" min="5" max="180" value="${parseInt(person.rest)||20}" required></label>`:''}<p class="limit">브라우저 시연용 정보예요. 차량 설정이나 팀 공유 프로필은 바꾸지 않아요.</p><div class="editor-actions"><button type="button" class="btn" data-close-editor>취소</button><button class="btn primary" type="submit">저장</button></div></form>`;
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
  if(e.target.closest('[data-route-failure]'))document.querySelector('#routeFeedback').textContent='내비와 연결하지 못했어요. 기존 경로를 유지하고 출발 전 준비와 기록은 계속 사용할 수 있어요. (실패 시연)';
  if(e.target.closest('[data-demo-reset]')){
    seatAssignments={...defaultSeats};chosenRating=null;chosenReasons.clear();environmentMode='available';chosenRoute='comfortable';appliedRoute=null;
    demo.people=JSON.parse(JSON.stringify(originalDemoFixture.people));demo.trips=JSON.parse(JSON.stringify(originalDemoFixture.trips));selectedTrip='t1';selectedPerson='driver';
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
