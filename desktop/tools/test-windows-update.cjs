const {test}=require('node:test');
const assert=require('node:assert/strict');
const {registration,unregistration,shouldOffer}=require('../windows-associations.cjs');
const {createPreflight}=require('../video-preflight.cjs');
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
test('registration advertises every format without writing user defaults or other players',()=>{
 const add=registration().join('\n'),remove=unregistration().join('\n');
 for(const ext of Object.keys(require('../media-probe.cjs').types)){
  assert.ok(add.includes(`.${ext}\\OpenWithProgids`));assert.ok(add.includes(`".${ext}" "RAKOTOPlayer.Media"`));
 }
 assert.ok(add.includes('Applications\\RAKOTO Player.exe\\SupportedTypes'));
 assert.ok(!/UserChoice|VLC|PotPlayer|DeleteReg/.test(add));
 assert.ok(!/DeleteRegKey HKCU "Software\\Classes\\\./.test(remove));
 assert.ok(add.includes('"%1'));
});
test('default suggestion requires a known different default and respects postponement and opt-out',()=>{
 const state={installed:true,isDefault:false};
 assert.equal(shouldOffer(state,{opens:2},100),false);
 assert.equal(shouldOffer(state,{opens:3},100),true);
 assert.equal(shouldOffer(state,{opens:9,next:101},100),false);
 assert.equal(shouldOffer(state,{opens:9,never:true},100),false);
 assert.equal(shouldOffer({...state,isDefault:true},{opens:9},100),false);
 assert.equal(shouldOffer({...state,isDefault:null},{opens:9},100),false);
 assert.equal(shouldOffer({...state,installed:false},{opens:9},100),false);
});
test('background preflight is serial, finite and a deferred file is not retried in a busy loop',async()=>{
 let active=0,maximum=0,calls=0;
 const session={items:[{id:'a'},{id:'b'},{id:'c'}]};
 const scanner=createPreflight({delay:2,update(){},async analyze(id){calls++;maximum=Math.max(maximum,++active);await wait(10);active--;if(id==='b')throw Object.assign(Error('budget'),{code:'ANALYSIS_BUDGET'})}});
 scanner.setSession(session);await wait(12);assert.equal(calls,0,'nothing scans before playback is ready');
 scanner.playback(true);await wait(120);scanner.dispose();
 assert.equal(maximum,1);assert.equal(calls,3);assert.deepEqual(session.items.map(item=>item.analysis),['ready','deferred','ready']);
});
test('changing folders cancels progress updates from the old session',async()=>{
 let finish;const updates=[];
 const scanner=createPreflight({delay:1,update:owner=>updates.push(owner.id),analyze:()=>new Promise(resolve=>{finish=resolve})});
 scanner.setSession({id:'old',items:[{id:'a'}]});scanner.playback(true);await wait(20);
 scanner.setSession({id:'new',items:[]});const before=updates.length;finish();await wait(20);scanner.dispose();
 assert.equal(updates.length,before);
});
