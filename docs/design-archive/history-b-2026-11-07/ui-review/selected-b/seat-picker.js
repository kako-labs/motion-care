/* Pure viewport placement, shared by the runtime and geometry regression checks. */
function seatPickerPlacement(anchor, viewport, size) {
  const margin=14,gap=14,clamp=(value,min,max)=>Math.max(min,Math.min(value,Math.max(min,max)));
  let side,left,top;
  if(viewport.width<=600){side='bottom';left=(viewport.width-size.width)/2;top=viewport.height-size.height-margin;}
  else {
    const right=viewport.width-anchor.right,leftRoom=anchor.left;
    side=right>=size.width+gap?'right':leftRoom>=size.width+gap?'left':right>=leftRoom?'right':'left';
    left=side==='right'?anchor.right+gap:anchor.left-size.width-gap;
    top=(anchor.top+anchor.bottom-size.height)/2;
  }
  left=clamp(left,margin,viewport.width-size.width-margin);
  top=clamp(top,margin,viewport.height-size.height-margin);
  return {side,left,top};
}
function positionSeatPicker(seatId) {
  const dialog=document.querySelector('#seatDrawer');
  if(!seatId){delete dialog.dataset.seat;return;}
  dialog.dataset.seat=seatId;
  const seat=document.querySelector(`.car [data-seat="${seatId}"]`),car=document.querySelector('.car');
  if(!seat||!car)return;
  const s=seat.getBoundingClientRect(),c=car.getBoundingClientRect();
  const placement=seatPickerPlacement({left:c.left,right:c.right,top:s.top,bottom:s.bottom},{width:innerWidth,height:innerHeight},{width:dialog.offsetWidth,height:dialog.offsetHeight});
  dialog.style.left=placement.left+'px';dialog.style.top=placement.top+'px';dialog.dataset.side=placement.side;
}
if(typeof window!=='undefined'){
  let pending=false;
  const update=()=>{const dialog=document.querySelector('#seatDrawer');if(!dialog?.classList.contains('open')||pending)return;pending=true;requestAnimationFrame(()=>{pending=false;positionSeatPicker(dialog.dataset.seat);});};
  window.addEventListener('resize',update);
  window.addEventListener('scroll',update,true);
}
