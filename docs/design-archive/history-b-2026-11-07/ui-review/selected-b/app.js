
const demo = {
  people: {
    driver: { id: 'driver', avatar: 'assets/avatar-minjun.png', name: '민준', emoji: '🧑🏻', role: '운전자', kind: '성인', sensitivity: '낮음', triggers: ['출발·정차'], trips: 12, summary: '정차 직전 급감속 주의' },
    passenger: { id: 'passenger', avatar: 'assets/avatar-seoa.png', name: '서아', emoji: '👧🏻', role: '동승자', kind: '어린이', sensitivity: '높음', triggers: ['출발·정차', '반복 가감속', '커브'], temp: '22°C', rest: '20분', summary: '출발·정차 구간에 민감해요' },
    jiyoung: { id: 'jiyoung', avatar: 'assets/avatar-jiyoung.png', name: '지영', emoji: '👩🏻', role: '동승자', kind: '성인', sensitivity: '보통', triggers: ['급커브'], temp: '23°C', rest: '40분', summary: '급커브 상황 주의' },
    doyun: { id: 'doyun', avatar: 'assets/avatar-doyun.png', name: '도윤', emoji: '👦🏻', role: '동승자', kind: '어린이', sensitivity: '보통', triggers: ['반복 가감속'], temp: '22°C', rest: '30분', summary: '반복 가감속 상황 주의' }
  },
  trips: [
    { id: 't1', date: '11월 4일 08:12', duration: 38, distance: 21.4, driver: '민준', passenger: '서아', feedback: 3, patterns: { stop: 5, start: 3, repeat: 2, curve: 1, sway: 0 }, rates: { stop: 1.4, start: .8, repeat: .5, curve: .3, sway: 0 }, samples: 457, analyzed: 37, guidance: '정차 시 조금 일찍 감속해보세요', time: '08:31' },
    { id: 't2', date: '11월 3일 18:40', duration: 34, distance: 18.2, driver: '민준', passenger: '서아', feedback: 4, patterns: { stop: 8, start: 4, repeat: 3, curve: 2, sway: 1 }, rates: { stop: 2.4, start: 1.2, repeat: .9, curve: .6, sway: .3 }, samples: 409, analyzed: 33 },
    { id: 't3', date: '11월 2일 09:05', duration: 31, distance: 16.7, driver: '민준', passenger: '서아', feedback: 3, patterns: { stop: 9, start: 5, repeat: 4, curve: 2, sway: 1 }, rates: { stop: 2.9, start: 1.6, repeat: 1.3, curve: .6, sway: .3 }, samples: 373, analyzed: 30 }
  ]
};

// Separate synthetic history for the second adult driver.
for(const [index,source] of demo.trips.slice(0,3).entries()){
 const stops=[4,6,7][index];
 demo.trips.push({...source,id:'j'+(index+1),date:['11월 4일 09:20','11월 3일 14:10','11월 1일 17:35'][index],driver:'지영',duration:[32,35,29][index],feedback:[4,3,4][index],patterns:{stop:stops,start:[2,3,3][index],repeat:[1,2,2][index],curve:[1,1,2][index],sway:[0,1,0][index]},rates:{...source.rates},guidance:index===0?'정차 전, 조금 일찍 감속해 보세요.':undefined});
}

for(const [i,t] of demo.trips.entries()) { t.replay=buildJourneyReplay(t.duration,t.patterns.stop,t.patterns.start,i);t.distance=Number(t.replay.distanceKm.toFixed(1));t.samples=t.replay.samples.length;t.analyzed=Math.round(t.replay.validSeconds/60);if(t.guidance)t.time=clockTime(t,t.replay.events[Math.min(2,t.replay.events.length-1)].end);for(const k of Object.keys(t.rates))t.rates[k]=Number((t.patterns[k]*600/t.replay.validSeconds).toFixed(1));}

const labels = { stop: '정차 직전 속도 변화', start: '출발 직후 속도 증가', repeat: '반복 가감속', curve: '빠른 회전', sway: '반복 좌우 움직임' };
const previous = { stop: 2.4, start: 1.4, repeat: .9, curve: .6, sway: .3 };
for(const key of Object.keys(previous))previous[key]=Number((demo.trips.filter(t=>t.driver==='민준').slice(1).reduce((sum,t)=>sum+t.patterns[key]*600/t.replay.validSeconds,0)/2).toFixed(1));
let selectedTrip = 'j1';
let selectedPerson = 'jiyoung';
const defaultSeats = { 'rear-left': null, 'rear-center': null, 'rear-right': 'passenger', 'front-passenger': null, driver: 'jiyoung' };
let seatAssignments = { ...defaultSeats };
try {
  const savedSeats = JSON.parse(localStorage.getItem('motion-care-demo-seats'));
  if (localStorage.getItem('motion-care-demo-scenario')==='populated-v2' && savedSeats && Object.keys(defaultSeats).every(key => key in savedSeats) && demo.people[savedSeats.driver]?.kind==='성인') seatAssignments = savedSeats;
  else {localStorage.setItem('motion-care-demo-seats',JSON.stringify(defaultSeats));localStorage.setItem('motion-care-demo-scenario','populated-v2');}
} catch (_) {}

// Apply the requested demo driver before any screen renders, including restored seats.
const requestedDriver=typeof location!=='undefined'?new URLSearchParams(location.search).get('driver'):null;
if(demo.people[requestedDriver]?.kind==='성인')assignPerson(requestedDriver,'driver');

function driverTrips(){
  const driverName=demo.people[seatAssignments.driver]?.name;
  return demo.trips.filter(t=>t.driver===driverName);
}

function syncSelectedTrip(){
  const trips=driverTrips();
  if(!trips.some(t=>t.id===selectedTrip))selectedTrip=trips[0]?.id??null;
  return trips;
}

function tripButton(t) {
  return `<button class="trip" data-trip="${t.id}"><div class="trip-top"><b>${t.date}</b><span class="rating">${t.feedback === null ? '미응답' : '평가 '+t.feedback+'/5'}</span></div><p>${t.duration}분 · ${t.distance.toFixed(1)}km</p></button>`;
}

const seatLabels = { 'rear-left': '뒤 왼쪽', 'rear-center': '뒤 가운데', 'rear-right': '뒤 오른쪽', 'front-passenger': '조수석', driver: '운전석' };
let openSeat = null;

function renderSeats() {
  const trips=syncSelectedTrip();
  const driver=demo.people[seatAssignments.driver];
  document.querySelector('.recent-title').textContent=driver?`${driver.name}의 최근 운행`:'최근 운행';
  document.querySelector('#passengerCount').textContent = Object.values(seatAssignments).filter(Boolean).length + '명';
  if(typeof syncHomeAdvice === 'function')syncHomeAdvice();
  document.querySelector('#homeTrips').innerHTML=trips.slice(0,3).map(tripButton).join('');
  document.querySelectorAll('[data-seat]').forEach(seat => {
    const seatId = seat.dataset.seat;
    const person = seatAssignments[seatId] ? demo.people[seatAssignments[seatId]] : null;
    seat.classList.toggle('filled', Boolean(person));
    seat.setAttribute('aria-pressed', String(Boolean(person)));
    seat.setAttribute('aria-label', seatLabels[seatId] + (person ? ': ' + person.name : ': 사람 선택'));
    seat.classList.toggle('selected', openSeat === seatId);
    seat.innerHTML = `<span class="seat-circle">${person ? `<span class="avatar"><img src="${person.avatar}" alt=""></span>` : '<span class="seat-plus">＋</span>'}</span><span class="seat-name">${person ? person.name : ''}</span>`;
  });
}

function canSit(person, seatId) {
  return seatId !== 'driver' || person.kind === '성인';
}

function renderDrawer() {
  const drawer = document.querySelector('#seatDrawer');
  if (!openSeat) return;
  const occupantId = seatAssignments[openSeat];
  const occupant = occupantId ? demo.people[occupantId] : null;
  drawer.innerHTML = `
    <div class="drawer-head"><h2>${seatLabels[openSeat]}</h2><button class="btn icon" data-close-drawer aria-label="닫기">✕</button></div>
    ${occupant ? `<div class="occupant"><span class="avatar"><img src="${occupant.avatar}" alt=""></span><b>${occupant.name}</b><button class="btn primary" data-open-person="${occupant.id}">프로필</button><button class="btn" data-clear-seat>비우기</button></div>` : ''}
    ${Object.values(demo.people).filter(p => p.id !== occupantId).map(p => {
      const here = occupantId === p.id;
      const elsewhere = Object.keys(seatAssignments).find(key => key !== openSeat && seatAssignments[key] === p.id);
      return `<button class="drawer-person ${here ? 'here' : ''}" data-pick="${p.id}" ${here || !canSit(p, openSeat) ? 'disabled' : ''}>
        <span class="avatar"><img src="${p.avatar}" alt=""></span><span><b>${p.name}</b><small class="${elsewhere ? 'seated' : ''}">${elsewhere ? seatLabels[elsewhere] : p.role}</small></span><i>${here ? '✓' : ''}</i>
      </button>`;
    }).join('')}`;
}

function setDrawer(seatId) {
  openSeat = seatId;
  renderDrawer();
  document.querySelector('#seatDrawer').classList.toggle('open', Boolean(seatId));
  document.querySelector('#seatDrawer').setAttribute('aria-hidden', String(!seatId));
  document.querySelector('#seatDrawer').inert = !seatId;
  document.querySelector('#drawerScrim').classList.toggle('open', Boolean(seatId));
  renderSeats();
}

function saveSeats() {
  try { localStorage.setItem('motion-care-demo-seats', JSON.stringify(seatAssignments)); } catch (_) {}
}

function assignPerson(personId, seatId) {
  const person = demo.people[personId];
  if (!person || !canSit(person, seatId)) return;
  const previousSeat = Object.keys(seatAssignments).find(key => seatAssignments[key] === personId);
  const displaced = seatAssignments[seatId];
  if (previousSeat) seatAssignments[previousSeat] = displaced || null;
  seatAssignments[seatId] = personId;
  saveSeats();
}

function renderHome() {
  renderSeats();
}

function renderSelector() {
  document.querySelector('#profileSelect').innerHTML = Object.values(demo.people).map(p => `
    <button class="profile-card" data-open-person="${p.id}">
      <div class="avatar"><img src="${p.avatar}" alt=""></div><div class="kicker">${p.role}</div><h2>${p.name}</h2>
      <div class="profile-one-line"><small>이동 메모</small><strong>${p.summary}</strong></div><span class="enter">→</span>
    </button>`).join('');
}

function renderProfile() {
  const p = demo.people[selectedPerson], personTrips=demo.trips.filter(t=>t.driver===p.name), driver=p.kind==='성인'&&personTrips.length>0;
  const profilePrevious=Object.fromEntries(Object.keys(labels).map(k=>[k,personTrips.slice(1).reduce((sum,t)=>sum+t.rates[k],0)/Math.max(1,personTrips.length-1)]));
  document.querySelector('#profileSummary').classList.toggle('driver-summary',driver);
  document.querySelector('#profileTitle').textContent = `${p.name}의 프로필`;
  document.querySelector('#profileSummary').innerHTML = `<div class="portrait-halo"><img src="${p.avatar}" alt="${p.name}의 프로필 이미지"></div><span class="tag">${p.kind} · ${driver?'운전자':p.role}</span><h2>${p.name}</h2><div class="profile-highlight"><small>최근 이동에서</small><strong>${driver?'최근 운행 '+personTrips.length+'건':p.summary}</strong></div>`;
  const t=personTrips[0]||demo.trips[0];
  if (driver) {
    document.querySelector('#profileMain').innerHTML = `<div class="profile-section-heading"><span class="kicker">DRIVING JOURNAL</span><h2>운전 기록</h2><p class="desc">같은 운전자의 운행 기록을 시간순으로 비교해요.</p></div><div class="preference-grid"><div class="preference"><span>저장된 운행</span><strong>${personTrips.length}<small>회</small></strong></div><div class="preference"><span>정차 직전 변화</span><strong>${t.rates.stop}<small>회 / 10분</small></strong></div><div class="preference"><span>최근 동승자 평가</span><strong>${t.feedback===null?'미응답':t.feedback+'<small>/ 5</small>'}</strong></div></div><div class="panel driver-patterns"><div class="section-label"><span>최근 운전 경향</span><small>이번 운행 · 10분당 관찰 빈도</small></div>${Object.keys(labels).map(k=>`<div class="driver-pattern-row"><span>${labels[k]}</span><div class="pattern-track"><i style="width:${Math.min(t.rates[k]/3*100,100)}%"></i></div><b>${t.rates[k].toFixed(1)}</b><small>이전 ${profilePrevious[k].toFixed(1)}</small></div>`).join('')}</div>`;
  } else {
    document.querySelector('#profileMain').innerHTML = `<div class="profile-section-heading"><span class="kicker">COMFORT PREFERENCES</span><h2>${p.name}에게 편안한 이동을.</h2><p class="desc">직접 등록한 선호와 지난 이동의 응답을 함께 살펴봐요.</p></div><div class="preference-grid"><div class="preference"><span>등록한 민감도</span><strong>${p.sensitivity}</strong><small>자기보고 정보</small></div><div class="preference"><span>선호 실내 온도</span><strong>${p.temp}</strong><small>직접 등록한 선호</small></div><div class="preference"><span>선호 휴식 간격</span><strong>${p.rest}</strong><small>개인 선호</small></div></div><div class="panel sensitivity-panel"><div class="section-label"><span>조금 더 신경 쓸 순간</span><small>등록 특성 / 최근 응답</small></div><div class="sensitivity-list">${p.triggers.map((x,i)=>`<div class="sensitivity-item"><span class="sensitivity-number">0${i+1}</span><div><h3>${x}</h3><p>${i===0?'최근 평가에서 불편을 남겼어요.':'프로필에 등록한 관심 상황이에요.'}</p></div><span class="tag">${i===0?'최근 응답':'등록 정보'}</span></div>`).join('')}</div></div>`;
  }
}

function renderRecords() {
  const trips=syncSelectedTrip();
  if(!trips.length){
    document.querySelector('#recordList').innerHTML='';
    document.querySelector('#tripDetail').innerHTML='<p class="desc">운전자를 선택하면 해당 운전자의 운행 기록을 볼 수 있어요.</p>';
    return;
  }
  document.querySelector('#recordList').innerHTML = trips.map((t,i)=>`<button class="trip ${t.id===selectedTrip?'selected':''}" data-trip="${t.id}"><span class="journal-index">0${i+1}</span><div><b>${t.date}</b><p>${t.duration}분 · ${t.distance}km</p></div><span class="journal-arrow">↗</span></button>`).join('');
  const t=demo.trips.find(x=>x.id===selectedTrip),variant=trips.indexOf(t);
  document.querySelector('#tripDetail').innerHTML=`<div class="report-heading"><div><div class="kicker">TRIP JOURNAL · 0${variant+1}</div><h2>${t.date}</h2><p class="desc">${t.driver} 운전 <span>·</span> ${t.passenger} 동승</p></div></div><div class="report-metrics"><div><small>함께한 시간</small><strong>${t.duration}<em>분</em></strong></div><div><small>이동 거리 근사</small><strong>${t.distance}<em>km</em></strong></div><div><small>유효 분석 구간</small><strong>${t.analyzed}<em>분</em></strong></div></div><div class="report-body"><div class="report-primary">${journeyChart(t)}</div><aside class="report-side"><div class="rating-orbit"><span>동승자의 응답</span><strong>${t.feedback===null?'—':t.feedback}<small>/ 5</small></strong><p>${t.feedback===null?'아직 남긴 응답이 없어요.':t.feedbackReasons?.length?'불편했던 순간 · '+t.feedbackReasons.join(' · '):t.feedbackReasons?'추가로 남긴 불편 상황 없음':'불편했던 순간 · 출발·정차'}</p><button class="text-action" data-feedback>${t.feedback===null?'평가 남기기':'평가 수정'} ↗</button></div><div class="report-patterns"><div class="pattern-heading"><h3>관찰된 움직임</h3></div><div class="movement-grid">${Object.keys(labels).map(k=>`<div class="movement"><span>${labels[k]}</span><strong>${t.patterns[k]}<small>회</small></strong><i style="--extent:${Math.max(3,t.patterns[k]*9)}%"></i></div>`).join('')}</div></div><div class="guidance-note"><span class="kicker"></span><h3>주행 안내</h3><p>${t.guidance||'표시된 안내 없음'}</p><small>${t.time||'—'}</small></div><button class="environment-teaser" data-go="environment"><span>실내·외부 환경 표시</span><b>환경 화면 살펴보기 ↗</b><small></small></button></aside></div>`;
  selectJourneyEvent(0);
}

function go(name) {
  document.querySelectorAll('.screen').forEach(x => x.classList.toggle('active', x.id === `screen-${name}`));
  document.querySelectorAll('.nav').forEach(x => x.classList.toggle('active', x.dataset.go === name || (name === 'profile-detail' && x.dataset.go === 'profiles') || ((['why','finished','environment','routes'].includes(name)) && x.dataset.go === 'home')));
  if (name === 'profiles') renderSelector();
  if (name === 'home') renderHome();
  if (name === 'profile-detail') renderProfile();
  if (name === 'records') renderRecords();
  if (name === 'feedback') renderFeedback();
  if (name === 'why' && typeof renderAdviceDetail === 'function') renderAdviceDetail();
  document.querySelector('main').scrollTop = 0;
  history.replaceState(null,'','#'+name);
  if (typeof renderExtras === 'function') renderExtras(name);
}

function showToast() {
  const node = document.querySelector('#toast');
  node.querySelector('b').textContent = '운행 후 평가';
  node.querySelector('span').textContent = '서아 3/5 · 가장 불편했던 상황: 출발·정차';
  node.classList.add('show');
  clearTimeout(window.toastTimer); window.toastTimer = setTimeout(() => node.classList.remove('show'), 2200);
}

document.addEventListener('click', e => {
  const destination = e.target.closest('[data-go]'); if (destination) { setDrawer(null); go(destination.dataset.go); return; }
  if (e.target.closest('[data-close-drawer]')) { setDrawer(null); return; }
  const pick = e.target.closest('[data-pick]'); if (pick) { assignPerson(pick.dataset.pick, openSeat); setDrawer(null); return; }
  if (e.target.closest('[data-clear-seat]')) { seatAssignments[openSeat] = null; saveSeats(); setDrawer(null); return; }
  const seat = e.target.closest('[data-seat]'); if (seat) { setDrawer(openSeat === seat.dataset.seat ? null : seat.dataset.seat); return; }
  const person = e.target.closest('[data-open-person]'); if (person) { setDrawer(null); selectedPerson = person.dataset.openPerson; go('profile-detail'); return; }
  const trip = e.target.closest('[data-trip]'); if (trip) { selectedTrip = trip.dataset.trip; go('records'); return; }
  if (e.target.closest('[data-feedback]')) { go('feedback'); return; }
});

document.addEventListener('keydown', e => { if (e.key === 'Escape' && openSeat) setDrawer(null); });

renderHome(); renderSelector(); renderProfile(); renderRecords();


let chosenRating = null;
let chosenReasons = new Set();
const feedbackReasons = ['출발·정차', '반복 가감속', '커브', '좌우 움직임', '차량 환경'];
function renderFeedback() {
 const t=demo.trips.find(x=>x.id===selectedTrip)||demo.trips[0];
 document.querySelector('#screen-feedback .screen-head .desc').textContent=t.date+' · '+t.duration+'분 · '+t.driver+' 운전';
 document.querySelector('.recap-number').innerHTML=t.duration+' <small>분</small>';
 document.querySelectorAll('.recap-row b')[0].textContent=t.distance+' km';
 document.querySelectorAll('.recap-row b')[1].textContent=t.analyzed+'분';
 document.querySelectorAll('.recap-row b')[2].textContent=t.driver+' · '+t.passenger;

 document.querySelector('#ratingStars').innerHTML=Array.from({length:5},(_,i)=>`<button class="star ${chosenRating!==null&&i<chosenRating?'active':''}" data-rating="${i+1}" aria-label="${i+1}점" aria-pressed="${chosenRating!==null&&i<chosenRating}"><svg viewBox="0 0 24 24"><path d="m12 2.5 2.9 5.9 6.5.9-4.7 4.6 1.1 6.5-5.8-3.1-5.8 3.1 1.1-6.5L2.6 9.3l6.5-.9Z"/></svg></button>`).join('');
 document.querySelector('#reasonChips').innerHTML=feedbackReasons.map(r=>`<button class="reason-chip ${chosenReasons.has(r)?'active':''}" data-reason="${r}" aria-pressed="${chosenReasons.has(r)}">${r}</button>`).join('');
 document.querySelector('[data-save-feedback]').disabled=chosenRating===null;
}
function finishFeedback(skip) {
 const value=skip?null:chosenRating;if(!skip&&value===null)return;
 const payload={tripId:selectedTrip,personId:'passenger',rating:value,reasons:skip?[]:[...chosenReasons],source:'synthetic-preview'};
 const t=demo.trips.find(x=>x.id===selectedTrip);if(t)t.feedback=value;if(t){t.feedbackReasons=skip?[]:[...chosenReasons];}renderHome();renderRecords();
 const m=document.querySelectorAll('#screen-finished .metric strong');if(t){m[0].textContent=t.duration+'분';m[1].textContent=t.distance+'km';document.querySelector('#screen-finished [data-trip]').dataset.trip=t.id;}
 try{localStorage.setItem('motion-care-b-feedback',JSON.stringify(payload));}catch(_){}
 document.querySelector('#finishedRating').textContent=value===null?'미응답':value+' / 5';
 document.querySelector('#finishedStatus').textContent=value===null?'평가는 건너뛰고 이동 기록을 남겼어요.':'서아의 평가가 저장됐어요.';go('finished');
}
document.addEventListener('click',e=>{
 const rating=e.target.closest('[data-rating]');if(rating){chosenRating=Number(rating.dataset.rating);renderFeedback();if(typeof updateRatingWord==='function')updateRatingWord();return;}
 const reason=e.target.closest('[data-reason]');if(reason){const r=reason.dataset.reason;chosenReasons.has(r)?chosenReasons.delete(r):chosenReasons.add(r);renderFeedback();if(typeof updateRatingWord==='function')updateRatingWord();return;}
 if(e.target.closest('[data-save-feedback]')){finishFeedback(false);return;}
 if(e.target.closest('[data-skip-feedback]')){chosenRating=null;chosenReasons.clear();finishFeedback(true);return;}
 if(e.target.closest('[data-edit-person]')){openProfileEditor();}
});
renderFeedback();

const requestedScreen = location.hash.slice(1);
if (['home','profiles','profile-detail','records','why','feedback','finished'].includes(requestedScreen)) go(requestedScreen);
