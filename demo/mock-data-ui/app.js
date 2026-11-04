const demo = {
  people: {
    driver: { id: 'driver', name: '민준', emoji: '🧑🏻', role: '운전자', kind: '성인', sensitivity: '낮음', triggers: ['출발·정차'], trips: 12 },
    passenger: { id: 'passenger', name: '서아', emoji: '👧🏻', role: '동승자', kind: '어린이', sensitivity: '높음', triggers: ['출발·정차', '반복 가감속', '커브'], temp: '22°C', rest: '20분' }
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

function tripButton(t) {
  return `<button class="trip" data-trip="${t.id}"><div class="trip-top"><b>${t.date}</b><span class="rating">평가 ${t.feedback}/5</span></div><p>${t.duration}분 · ${t.distance.toFixed(1)}km<br>${t.driver} 운전 · ${t.passenger} 동승</p></button>`;
}

function renderHome() {
  document.querySelector('#homeTrips').innerHTML = demo.trips.map(tripButton).join('');
}

function renderSelector() {
  document.querySelector('#profileSelect').innerHTML = Object.values(demo.people).map(p => `
    <button class="profile-card" data-open-person="${p.id}">
      <div class="avatar">${p.emoji}</div><div class="kicker">${p.role}</div><h2>${p.name}</h2>
      <p>${p.id === 'driver' ? '최근 차량 운행에서 관찰된 운전 경향과 변화' : '멀미 민감도와 불편을 느끼기 쉬운 상황'}</p>
      <div class="chips">${p.triggers.map(x => `<span class="chip">${x}</span>`).join('')}</div><span class="enter">→</span>
    </button>`).join('');
}

function renderProfile() {
  const p = demo.people[selectedPerson];
  document.querySelector('#profileTitle').textContent = `${p.name} ${p.role} 프로필`;
  document.querySelector('#profileSummary').innerHTML = `<div class="kicker">${p.role}</div><div class="avatar">${p.emoji}</div><h2>${p.name}</h2><p>${p.kind}</p><div class="chips" style="justify-content:center">${p.triggers.map(x => `<span class="chip">${x}</span>`).join('')}</div>`;
  if (p.id === 'driver') {
    const t = demo.trips[0];
    document.querySelector('#profileMain').innerHTML = `
      <div class="metrics"><div class="panel metric"><small>저장된 운행</small><strong>${p.trips}회</strong></div><div class="panel metric"><small>정차 직전 변화</small><strong>${t.rates.stop}회/10분</strong></div><div class="panel metric"><small>최근 동승자 평가</small><strong>${t.feedback}/5</strong></div></div>
      <div class="panel table"><div class="table-title">최근 운전 경향</div><div class="row head"><span>관찰 항목</span><span>이번</span><span>최근</span><span>이전</span></div>${Object.keys(labels).map(k => `<div class="row"><span>${labels[k]}</span><strong>${t.patterns[k]}회</strong><span>${t.rates[k].toFixed(1)}/10분</span><span>${previous[k].toFixed(1)}/10분</span></div>`).join('')}</div>`;
  } else {
    document.querySelector('#profileMain').innerHTML = `
      <div class="metrics"><div class="panel metric"><small>멀미 민감도</small><strong>${p.sensitivity}</strong></div><div class="panel metric"><small>선호 실내 온도</small><strong>${p.temp}</strong></div><div class="panel metric"><small>선호 휴식 간격</small><strong>${p.rest}</strong></div></div>
      <div class="panel table"><div class="table-title">불편을 느끼기 쉬운 상황</div>${p.triggers.map((x, i) => `<div class="row"><span>${x}</span><strong>${i === 0 ? '주요' : '관심'}</strong><span>${i === 0 ? '최근 평가' : '등록 특성'}</span><span>편집 가능</span></div>`).join('')}<p class="limit">개인별 조언을 위한 참고 정보이며 멀미 발생을 의학적으로 예측하거나 진단하지 않습니다.</p></div>`;
  }
}

function renderRecords() {
  document.querySelector('#recordList').innerHTML = demo.trips.map(t => `<button class="trip ${t.id === selectedTrip ? 'selected' : ''}" data-trip="${t.id}"><div class="trip-top"><b>${t.date}</b><span>›</span></div><p>${t.duration}분 · ${t.distance}km<br>${t.driver} · ${t.passenger}</p></button>`).join('');
  const t = demo.trips.find(x => x.id === selectedTrip);
  const points = [18, 35, 12, 52, 48, 72, 67, 78, 34, 18, 49, 27, 8].map((v, i) => `${i * 48 + 12},${220 - v * 2.2}`).join(' ');
  document.querySelector('#tripDetail').innerHTML = `
    <div class="detail-head"><div><div class="kicker">이번 운행 요약</div><h2>${t.date} 출발</h2><p class="desc">${t.driver} 운전 · ${t.passenger} 동승 · 중형 전기차</p></div><span class="tag">차량 데이터</span></div>
    <div class="metrics" style="margin-top:17px"><div class="panel metric"><small>운행 시간</small><strong>${t.duration}분</strong></div><div class="panel metric"><small>거리 근사</small><strong>${t.distance}km</strong></div><div class="panel metric"><small>유효 분석</small><strong>${t.analyzed}분</strong></div></div>
    <div class="detail-grid"><div class="panel chart-panel"><b>시간에 따른 속도</b><svg class="chart" viewBox="0 0 620 250" preserveAspectRatio="none"><defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#37dcff" stop-opacity=".4"/><stop offset="1" stop-color="#37dcff" stop-opacity="0"/></linearGradient></defs><path d="M12 220 L${points.replaceAll(' ', ' L')} L588 220Z" fill="url(#g)"/><polyline points="${points}" fill="none" stroke="#37dcff" stroke-width="4"/><line x1="10" y1="220" x2="600" y2="220" stroke="rgba(94,190,255,.25)"/><text x="10" y="243">0분</text><text x="285" y="243">${Math.round(t.duration / 2)}분</text><text x="565" y="243">${t.duration}분</text></svg><p class="limit">${t.samples}개 속도 샘플 중 연속된 ${t.analyzed}분을 분석했습니다.</p></div>
      <div class="stack"><div class="panel mini"><h3>관찰된 패턴</h3>${Object.keys(labels).filter(k => t.patterns[k] > 0).map(k => `<div class="pattern"><span>${labels[k]}</span><b>${t.patterns[k]}회</b></div>`).join('')}</div><div class="panel mini"><h3>동승자 평가</h3><div class="score">${t.feedback}/5</div><p class="desc">가장 불편: 출발·정차</p><button class="btn small" data-feedback>평가 수정</button></div><div class="panel mini"><h3>실제 안내 이력</h3><p>${t.guidance || '표시된 안내 없음'}</p><small class="desc">${t.time || '—'}</small></div></div></div>
    <p class="limit">차량 속도 변화로 계산한 근사값입니다. 탑승자의 실제 멀미 여부를 확인한 결과는 아니며 도로·교통 상황의 영향이 포함될 수 있습니다.</p>`;
}

function go(name) {
  document.querySelectorAll('.screen').forEach(x => x.classList.toggle('active', x.id === `screen-${name}`));
  document.querySelectorAll('.nav').forEach(x => x.classList.toggle('active', x.dataset.go === name || (name === 'profile-detail' && x.dataset.go === 'profiles') || (name === 'why' && x.dataset.go === 'home')));
  if (name === 'profiles') renderSelector();
  if (name === 'profile-detail') renderProfile();
  if (name === 'records') renderRecords();
  document.querySelector('main').scrollTop = 0;
}

function showToast() {
  const node = document.querySelector('#toast'); node.classList.add('show');
  clearTimeout(window.toastTimer); window.toastTimer = setTimeout(() => node.classList.remove('show'), 2200);
}

document.addEventListener('click', e => {
  const destination = e.target.closest('[data-go]'); if (destination) { go(destination.dataset.go); return; }
  const person = e.target.closest('[data-open-person]'); if (person) { selectedPerson = person.dataset.openPerson; go('profile-detail'); return; }
  const trip = e.target.closest('[data-trip]'); if (trip) { selectedTrip = trip.dataset.trip; go('records'); return; }
  if (e.target.closest('[data-feedback]') || e.target.closest('#feedback')) showToast();
});

renderHome(); renderSelector(); renderProfile(); renderRecords();
