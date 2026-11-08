"""Capture the real Android feature screens, preserving the approved debug fixture isolation."""
import hashlib,json,re,sys,time
from pathlib import Path
import history_b_device_check as ui

width,height=map(int,sys.argv[1:3])
ui.OUT=Path('docs/history-b-android/device-checks/final')/f'{width}x{height}'
ui.OUT.mkdir(parents=True,exist_ok=True)
checks=[]
activity='dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity'
def start():
    ui.adb('shell','am','start','-S','-W','-n',activity)
    time.sleep(.6)
def labels(root=None):
    return [n.get('text') or n.get('content-desc') for n in (root or ui.tree()).iter('node') if n.get('text') or n.get('content-desc')]
def visible(label,prefix=False):
    for node in ui.tree().iter('node'):
        value=node.get('text') or node.get('content-desc') or ''
        if (value.startswith(label) if prefix else value==label):
            bounds=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(bounds)==4 and bounds[3]-bounds[1]>=15: return value
    return None
def click(label,prefix=False):
    for attempt in range(9):
        found=visible(label,prefix)
        if found:
            ui.click(found); return
        ui.swipe('down')
    raise AssertionError('Cannot reach '+label)
def capture(name,required):
    ui.observe(name)
    seen=labels()
    assert all(any(item in value for value in seen) for item in required),(name,required,seen)
    checks.append({'screen':name,'required':required,'passed':True,'sha256':hashlib.sha256((ui.OUT/(name+'.png')).read_bytes()).hexdigest()})
    print('CAPTURED '+name,flush=True)

previous_size=ui.adb('shell','wm','size').decode()
previous_override=re.search(r'Override size: (\d+x\d+)',previous_size)
try:
    ui.adb('shell','wm','size',f'{width}x{height}')
    start();capture('departure',['최근 운행','지영','서아','오늘의 제안'])
    click('운행 기록');capture('records',['전체 운행 기록','11월 4일'])
    start();click('프로필');capture('driver',['전체 운전 기록','지영'])
    ui.swipe('down');capture('driver-history',['전체 운전 기록','최근 1.3회'])
    start();click('뒤 오른쪽 · 서아');click('프로필');capture('passenger',['서아','민감도','조금 더 신경 쓸 순간'])
    start();click('프로필');click('자세히  ›');capture('advice',['왜 이 조언인가요?','1.3','2.1'])
    click('감속 3 ·',prefix=True)
    for _ in range(5):
        if visible('운행 상세에서 보기'): break
        ui.swipe('down')
    capture('braking-detail',['감속 3 ·','km/h','운행 상세에서 보기'])
    click('운행 상세에서 보기');capture('trip',['운행 상세','완료된 운행','지영 · 서아'])
    for _ in range(5):
        plot=next((n for n in ui.tree().iter('node') if n.get('content-desc')=='감속 전 접근과 정차 후 재출발 상세 그래프'),None)
        bounds=list(map(int,re.findall(r'\d+',plot.get('bounds','')))) if plot is not None else []
        if len(bounds)==4 and bounds[3]-bounds[1]>=180: break
        ui.swipe('down')
    if not visible('감속 3 ·',prefix=True):
        x=str(int(width*.82)); y=int(height*.40)
        ui.adb('shell','input','swipe',x,str(y),x,str(y+int(height*.16)),'300')
    capture('trip-graph',['감속 3 ·','감속 전 접근과 정차 후 재출발 상세 그래프'])
    start();click('완료 평가');capture('feedback',['서아의 한마디','1점','5점'])
    click('커브')
    for _ in range(5):
        if visible('나의 응답 남기기') and visible('건너뛰기'): break
        ui.swipe('down')
    capture('feedback-actions',['나의 응답 남기기','건너뛰기'])
    start();click('종료 요약');capture('finished',['운행을 마쳤어요','운행 시간'])
    for _ in range(5):
        if visible('주행 상세'): break
        ui.swipe('down')
    capture('finished-actions',['주행 상세','완료'])
    (ui.OUT/'results.json').write_text(json.dumps({'device':'mc_car Android API 35 x86_64','size':[width,height],'checks':checks},ensure_ascii=False,indent=2),encoding='utf8')
    print('PASS '+str(len(checks))+' captures',flush=True)
finally:
    ui.adb('shell','wm','size',previous_override.group(1) if previous_override else 'reset')
