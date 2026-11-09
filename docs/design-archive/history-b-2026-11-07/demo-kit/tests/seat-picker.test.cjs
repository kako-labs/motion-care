const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const c={};vm.createContext(c);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../../ui-review/selected-b/seat-picker.js'),'utf8'),c);
function place(anchor,viewport,size){return c.seatPickerPlacement(anchor,viewport,size);}
let p=place({left:80,right:580,top:400,bottom:480},{width:1600,height:900},{width:300,height:370});
assert.equal(p.side,'right');assert.ok(p.left>=580&&p.left+300<=1586);assert.ok(p.top>=14&&p.top+370<=886);
p=place({left:900,right:1400,top:760,bottom:830},{width:1500,height:850},{width:300,height:400});
assert.equal(p.side,'left');assert.ok(p.left+300<=900);assert.ok(p.top+400<=836);
p=place({left:20,right:370,top:550,bottom:620},{width:390,height:844},{width:362,height:310});
assert.equal(p.side,'bottom');assert.equal(p.left,14);assert.equal(p.top,520);
for(const viewport of [{width:1024,height:600},{width:768,height:600},{width:320,height:568}]){
 const size={width:Math.min(300,viewport.width-28),height:Math.min(400,viewport.height-28)};
 const result=place({left:50,right:500,top:30,bottom:90},viewport,size);
 assert.ok(result.left>=14&&result.top>=14);assert.ok(result.left+size.width<=viewport.width-14);assert.ok(result.top+size.height<=viewport.height-14);
}
console.log('PASS: seat picker anchors beside vehicle, clamps at edges, and uses compact mobile placement');
