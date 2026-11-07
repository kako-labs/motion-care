
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

const labels = { stop: '정차 직전 속도 변화', start: '출발 직후 속도 증가', repeat: '반복 가감속', curve: '빠른 회전', sway: '반복 좌우 움직임' };
const previous = { stop: 2.4, start: 1.4, repeat: .9, curve: .6, sway: .3 };
let selectedTrip = 't1';
let selectedPerson = 'driver';
const defaultSeats = { 'rear-left': null, 'rear-center': null, 'rear-right': 'passenger', 'front-passenger': null, driver: 'driver' };
let seatAssignments = { ...defaultSeats };
try {
  const savedSeats = JSON.parse(localStorage.getItem('motion-care-demo-seats'));
  if (savedSeats && Object.keys(defaultSeats).every(key => key in savedSeats)) seatAssignments = savedSeats;
} catch (_) {}

function tripButton(t) {
  return `<button class="trip" data-trip="${t.id}"><div class="trip-top"><b>${t.date}</b><span class="rating">${t.feedback === null ? '미응답' : '평가 '+t.feedback+'/5'}</span></div><p>${t.duration}분 · ${t.distance.toFixed(1)}km<br>${t.driver} 운전 · ${t.passenger} 동승</p></button>`;
}

const seatLabels = { 'rear-left': '뒤 왼쪽', 'rear-center': '뒤 가운데', 'rear-right': '뒤 오른쪽', 'front-passenger': '조수석', driver: '운전석' };
let openSeat = null;

function renderSeats() {
  document.querySelector('#passengerCount').textContent = Object.values(seatAssignments).filter(Boolean).length + '명';
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
  document.querySelector('#homeTrips').innerHTML = demo.trips.map(tripButton).join('');
  renderSeats();
}

function renderSelector() {
  document.querySelector('#profileSelect').innerHTML = Object.values(demo.people).map(p => `
    <button class="profile-card" data-open-person="${p.id}">
      <div class="avatar"><img src="${p.avatar}" alt=""></div><div class="kicker">${p.role}</div><h2>${p.name}</h2>
      <div class="profile-one-line"><small>최근 한줄 평</small><strong>${p.summary}</strong></div><span class="enter">→</span>
    </button>`).join('');
}

function renderProfile() {
  const p = demo.people[selectedPerson], driver=p.kind==='성인'&&p.id==='driver';
  document.querySelector('#profileTitle').textContent = `${p.name}의 프로필`;
  document.querySelector('#profileSummary').innerHTML = `<div class="portrait-halo"><img src="${p.avatar}" alt="${p.name}의 가상 인물 이미지"></div><span class="tag">${p.kind} · ${p.role}</span><h2>${p.name}</h2><p>함께 타는 사람</p><div class="profile-highlight"><small>최근 이동에서</small><strong>${p.summary}</strong></div><div class="portrait-footer"><i class="live"></i>합성 프로필</div>`;
  const t=demo.trips[0];
  if (driver) {
    document.querySelector('#profileMain').innerHTML = `<div class="profile-section-heading"><span class="kicker">DRIVING JOURNAL</span><h2>조금씩, 더 편안한 운전으로.</h2><p class="desc">점수보다 지난 이동에서 달라진 순간을 살펴봐요.</p></div><div class="preference-grid"><div class="preference"><span>저장된 운행</span><strong>${p.trips}<small>회</small></strong></div><div class="preference"><span>정차 직전 변화</span><strong>${t.rates.stop}<small>회 / 10분</small></strong></div><div class="preference"><span>최근 동승자 평가</span><strong>${t.feedback===null?'미응답':t.feedback+'<small>/ 5</small>'}</strong></div></div><div class="panel driver-patterns"><div class="section-label"><span>최근 운전 경향</span><small>이번 운행 · 10분당 관찰 빈도</small></div>${Object.keys(labels).map(k=>`<div class="driver-pattern-row"><span>${labels[k]}</span><div class="pattern-track"><i style="width:${Math.min(t.rates[k]/3*100,100)}%"></i></div><b>${t.rates[k].toFixed(1)}</b><small>이전 ${previous[k].toFixed(1)}</small></div>`).join('')}<p class="limit">운전 패턴의 관찰 빈도이며 멀미 감소를 뜻하지 않아요.</p></div>`;
  } else {
    document.querySelector('#profileMain').innerHTML = `<div class="profile-section-heading"><span class="kicker">COMFORT PREFERENCES</span><h2>${p.name}에게 편안한 이동을.</h2><p class="desc">직접 등록한 선호와 지난 이동의 응답을 함께 살펴봐요.</p></div><div class="preference-grid"><div class="preference"><span>등록한 민감도</span><strong>${p.sensitivity}</strong><small>자기보고 정보</small></div><div class="preference"><span>선호 실내 온도</span><strong>${p.temp}</strong><small>개인 선호 · 자동 제어 아님</small></div><div class="preference"><span>선호 휴식 간격</span><strong>${p.rest}</strong><small>개인 선호</small></div></div><div class="panel sensitivity-panel"><div class="section-label"><span>조금 더 신경 쓸 순간</span><small>등록 특성 / 최근 응답</small></div><div class="sensitivity-list">${p.triggers.map((x,i)=>`<div class="sensitivity-item"><span class="sensitivity-number">0${i+1}</span><div><h3>${x}</h3><p>${i===0?'최근 평가에서 불편을 남겼어요.':'프로필에 등록한 관심 상황이에요.'}</p></div><span class="tag">${i===0?'최근 응답':'등록 정보'}</span></div>`).join('')}</div></div><div class="profile-bottom-note"><span>개인마다, 이동마다 다를 수 있어요.</span><p>이 정보는 출발 전 조언을 위한 참고이며 의학적 진단이 아니에요.</p></div>`;
  }
}

function renderRecords() {
  document.querySelector('#recordList').innerHTML = demo.trips.map((t,i)=>`<button class="trip ${t.id===selectedTrip?'selected':''}" data-trip="${t.id}"><span class="journal-index">0${i+1}</span><div><b>${t.date}</b><p>${t.duration}분 · ${t.distance}km</p></div><span class="journal-arrow">↗</span></button>`).join('');
  const t=demo.trips.find(x=>x.id===selectedTrip),variant=demo.trips.indexOf(t);
  const speeds=[0,18,8,37,34,56,52,62,24,7,35,18,0].map((v,i)=>Math.max(0,v+(variant&&i>0&&i<12?variant*3:0)));
  const coords=speeds.map((v,i)=>`${48+i*49},${218-v*2.5}`).join(' ');
  document.querySelector('#tripDetail').innerHTML=`<div class="report-heading"><div><div class="kicker">TRIP JOURNAL · 0${variant+1}</div><h2>${t.date}</h2><p class="desc">${t.driver} 운전 <span>·</span> ${t.passenger} 동승</p></div><span class="tag">합성 시연 데이터</span></div><div class="report-metrics"><div><small>함께한 시간</small><strong>${t.duration}<em>분</em></strong></div><div><small>이동 거리 근사</small><strong>${t.distance}<em>km</em></strong></div><div><small>유효 분석 구간</small><strong>${t.analyzed}<em>분</em></strong></div></div><div class="report-body"><div class="report-primary"><div class="speed-heading"><h3>이동의 흐름</h3><span>속도 · km/h</span></div><svg class="speed-chart" viewBox="0 0 680 260" role="img" aria-label="합성 속도 예시 그래프, 실제 분석 시계열이 아님"><defs><linearGradient id="speedFill" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#89d9ff" stop-opacity=".3"/><stop offset="1" stop-color="#89d9ff" stop-opacity="0"/></linearGradient></defs>${[0,40,80].map(v=>`<line x1="48" x2="636" y1="${218-v*2.5}" y2="${218-v*2.5}" stroke="#8acfff1c" stroke-dasharray="3 7"/><text x="9" y="${222-v*2.5}">${v}</text>`).join('')}<path d="M48 218 L${coords.replaceAll(' ',' L')} L636 218Z" fill="url(#speedFill)"/><polyline points="${coords}" fill="none" stroke="#9ce6ff" stroke-width="3" stroke-linejoin="round"/>${speeds.map((v,i)=>`<circle cx="${48+i*49}" cy="${218-v*2.5}" r="5" class="speed-point"><title>${Math.round(t.duration*i/12)}분 · ${v}km/h · 합성 예시</title></circle>`).join('')}<text x="48" y="250">0분</text><text x="320" y="250">${Math.round(t.duration/2)}분</text><text x="602" y="250">${t.duration}분</text></svg><p class="chart-caption">${t.samples}개 속도 샘플 · 분석 ${t.analyzed}분 <span>그래프는 시연용 합성 예시</span></p><div class="pattern-heading"><h3>관찰된 움직임</h3><small>멀미 여부를 판단하는 수치는 아니에요.</small></div><div class="movement-grid">${Object.keys(labels).map(k=>`<div class="movement"><span>${labels[k]}</span><strong>${t.patterns[k]}<small>회</small></strong><i style="--extent:${Math.max(3,t.patterns[k]*9)}%"></i></div>`).join('')}</div></div><aside class="report-side"><div class="rating-orbit"><span>동승자의 응답</span><strong>${t.feedback===null?'—':t.feedback}<small>/ 5</small></strong><p>${t.feedback===null?'아직 남긴 응답이 없어요.':t.feedbackReasons?.length?'불편했던 순간 · '+t.feedbackReasons.join(' · '):t.feedbackReasons?'추가로 남긴 불편 상황 없음':'불편했던 순간 · 출발·정차'}</p><button class="text-action" data-feedback>${t.feedback===null?'평가 남기기':'평가 수정'} ↗</button></div><div class="guidance-note"><span class="kicker">GUIDANCE LOG</span><h3>이동 중 전한 한마디</h3><p>${t.guidance||'표시된 안내 없음'}</p><small>${t.time||'—'} · 합성 이력</small></div><button class="environment-teaser" data-go="environment"><span>실내·외부 환경 표시</span><b>환경 화면 살펴보기 ↗</b><small>합성 값으로 표시 방식 확인</small></button></aside></div><div class="report-footnote">속도 변화로 계산하는 근사값이에요. 도로·교통 상황의 영향이 포함되며 실제 멀미 여부나 예방 효과를 확인한 결과는 아니에요.</div>`;
}

function go(name) {
  document.querySelectorAll('.screen').forEach(x => x.classList.toggle('active', x.id === `screen-${name}`));
  document.querySelectorAll('.nav').forEach(x => x.classList.toggle('active', x.dataset.go === name || (name === 'profile-detail' && x.dataset.go === 'profiles') || ((['why','finished','environment','routes'].includes(name)) && x.dataset.go === 'home')));
  if (name === 'profiles') renderSelector();
  if (name === 'profile-detail') renderProfile();
  if (name === 'records') renderRecords();
  if (name === 'feedback') renderFeedback();
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
