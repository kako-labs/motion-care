"""Device regression: a landscape departure keeps seats, history and advice visible together."""
import json,re
from history_b_device_check import tree,adb

root=tree()
width,height=map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size').decode())[-1])
def fully_visible(node):
    bounds=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
    return len(bounds)==4 and 0<=bounds[0]<bounds[2]<=width and 0<=bounds[1]<bounds[3]<=height and bounds[3]-bounds[1]>=18
nodes=[n for n in root.iter('node') if fully_visible(n)]
text=[n.get('text','') for n in nodes]
descriptions=[n.get('content-desc','') for n in nodes]
for label in ['최근 운행','오늘의 제안','정지 시 조금 일찍 감속해 보세요.']:
    assert label in text, 'Landscape departure hides '+label
for seat in ['운전석','조수석','뒤 왼쪽','뒤 가운데','뒤 오른쪽']:
    assert any(n.startswith(seat+' ·') for n in descriptions), 'Landscape departure hides '+seat
print(json.dumps({'landscape_departure':'passed','seats':5,'history_and_advice_visible':True}))
