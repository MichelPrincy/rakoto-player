const bridge=window.desktop;
const $=id=>document.getElementById(id);
const surface=$('surface'),bar=$('glass'),seek=$('seek'),volume=$('volume');
let packet={},timer,held=false,menu=false,nativeMenu=false,chaptersKey='',lastPoint,drag;
const clock=value=>{
  const time=Math.max(0,Math.floor(Number(value)||0));
  const hours=Math.floor(time/3600);
  const minutes=Math.floor(time/60)%60;
  return (hours?hours+':'+String(minutes).padStart(2,'0'):minutes)+':'+String(time%60).padStart(2,'0');
};
const clear=()=>{clearTimeout(timer);timer=null;};
function schedule() {
  clear();
  if(!packet.fullscreen||held||menu||nativeMenu)return;
  timer=setTimeout(()=>{
    if(!held&&!menu&&!nativeMenu){document.body.classList.remove('active');$('hover-time').hidden=true;}
  },3000);
}
function show() {document.body.classList.add('active');schedule();}
function command(action,value){return bridge.mediaCommand(action,value).catch(()=>{});}
async function openMenu(kind){
  if(menu)return;
  menu=true;show();
  try{await bridge.mediaMenu(kind);}finally{menu=false;show();}
}
function language(code) {
  const aliases={fra:'fr',fre:'fr',eng:'en',jpn:'ja',mlg:'mg',deu:'de',ger:'de',spa:'es'};
  if(!code||code==='und')return '';
  try{return new Intl.DisplayNames(['fr'],{type:'language'}).of(aliases[code]||code);}catch{return code;}
}
function update(data) {
  const changed=packet.fullscreen!==data.fullscreen||packet.mode!==data.mode;
  packet=data;
  const props=data.native?.properties||{};
  const state=data.state||{};
  document.documentElement.dataset.theme=state.theme==='dark'?'dark':'light';
  const time=Number(props['time-pos']??state.time)||0;
  const duration=Number(props.duration??state.duration)||0;
  const tracks=props['track-list']||[];
  document.body.classList.toggle('fullscreen',Boolean(data.fullscreen));
  document.body.classList.toggle('compact',data.mode==='video');
  if(changed) {held=false;drag=null;clear();show();}
  $('elapsed').textContent=clock(time);
  $('duration').textContent=clock(duration);
  seek.disabled=!duration;
  seek.max=duration||1;
  if(!held)seek.value=time;
  seek.style.setProperty('--progress',Math.min(100,100*time/(duration||1))+'%');
  if(!held)volume.value=state.volume??.68;
  volume.style.setProperty('--progress',100*volume.value+'%');
  const playing=Boolean(state.playing);
  $('play').textContent=playing?'Ⅱ':'▶';
  $('play').setAttribute('aria-label',playing?'Pause':'Lire');
  const audio=tracks.find(track=>track.type==='audio'&&track.selected);
  const subtitle=tracks.find(track=>track.type==='sub'&&track.selected);
  $('audio').textContent=language(audio?.lang)||'Audio';
  $('audio').title=audio ? [language(audio.lang),audio.title,audio.codec,audio['demux-channels']].filter(Boolean).join(' · ') : 'Audio désactivé ou absent';
  $('subtitle').textContent=subtitle?(language(subtitle.lang)||'CC actif'):'CC';
  $('subtitle').title=subtitle ? [language(subtitle.lang),subtitle.title,subtitle.codec].filter(Boolean).join(' · ') : 'Sous-titres désactivés';
  $('speed').textContent=(Number(props.speed||state.speed)||1).toLocaleString('fr')+'×';
  $('full').title=data.fullscreen?'Quitter le plein écran':'Plein écran';
  $('float').dataset.command=data.mode==='video'?'normal':'video';
  $('float').title=data.mode==='video'?'Restaurer la fenêtre':'Vidéo flottante';
  const chapters=data.chapters||[];
  const key=JSON.stringify([duration,chapters]);
  if(chaptersKey!==key){
    chaptersKey=key;$('marks').replaceChildren();
    if(duration)for(const chapter of chapters){
      const marker=document.createElement('button');
      marker.type='button';marker.className='chapter';marker.style.left=Math.min(100,100*chapter.time/duration)+'%';
      marker.title=chapter.title+' · '+clock(chapter.time);marker.setAttribute('aria-label',marker.title);
      marker.addEventListener('click',()=>command('seek',chapter.time));
      $('marks').append(marker);
    }
  }
}
bridge.onOverlay(update);
bridge.onMenuLock(locked=>{nativeMenu=locked;show();});
surface.addEventListener('pointerdown',event=>{
  if(packet.mode!=='video'||event.button!==0||event.target.closest('button,input,.glass'))return;
  drag={id:event.pointerId,x:event.screenX,y:event.screenY};
  surface.setPointerCapture(event.pointerId);
});
surface.addEventListener('lostpointercapture',()=>{drag=null;});
surface.addEventListener('pointermove',event=>{
  if(drag&&event.pointerId===drag.id){
    bridge.mediaDrag(event.screenX-drag.x,event.screenY-drag.y);
    drag.x=event.screenX;drag.y=event.screenY;
  }
  if(!lastPoint||event.screenX!==lastPoint[0]||event.screenY!==lastPoint[1]){
    lastPoint=[event.screenX,event.screenY];show();
  }
});
surface.addEventListener('pointerenter',show);
surface.addEventListener('contextmenu',event=>{event.preventDefault();openMenu('main');});
surface.addEventListener('dblclick',event=>{if(!event.target.closest('button,input,.glass'))command('fullscreen');});
surface.addEventListener('wheel',event=>{
  if(!packet.fullscreen||!packet.wheel||event.target.closest('input'))return;
  event.preventDefault();show();
  command('volume',Math.max(0,Math.min(1,Number(volume.value)-Math.sign(event.deltaY)*.04)));
},{passive:false});
bar.addEventListener('pointerdown',()=>{held=true;show();});
window.addEventListener('pointerup',()=>{if(held){held=false;show();}});
window.addEventListener('pointercancel',()=>{held=false;show();});
window.addEventListener('blur',()=>{held=false;schedule();});
window.addEventListener('pagehide',clear);
window.addEventListener('keydown',event=>{
  if(event.ctrlKey||event.altKey||event.metaKey)return;
  show();
  if(event.code==='Escape'){command(packet.mode==='video'?'normal':packet.fullscreen?'fullscreen':'library');return;}
  if(event.code==='F10'&&event.shiftKey){event.preventDefault();openMenu('main');return;}
  if(event.target.closest('input,button'))return;
  const time=Number(seek.value);
  if(event.code==='Space'){event.preventDefault();command('toggle');}
  if(event.code==='ArrowLeft'||event.code==='ArrowRight'){
    event.preventDefault();command('seek',Math.max(0,Math.min(Number(seek.max),time+(event.code==='ArrowLeft'?-5:5))));
  }
  if(event.code==='KeyF')command('fullscreen');
});
$('back').addEventListener('click',()=>command(packet.mode==='video'?'normal':'library'));
for(const button of document.querySelectorAll('[data-command]'))button.addEventListener('click',()=>command(button.dataset.command));
for(const button of document.querySelectorAll('[data-menu]'))button.addEventListener('click',()=>openMenu(button.dataset.menu));
seek.addEventListener('input',()=>{show();seek.style.setProperty('--progress',(100*seek.value/seek.max)+'%');});
seek.addEventListener('change',()=>command('seek',Number(seek.value)));
volume.addEventListener('input',()=>{show();command('volume',Number(volume.value));});
$('timeline').addEventListener('pointermove',event=>{
  const rect=seek.getBoundingClientRect();
  const fraction=Math.max(0,Math.min(1,(event.clientX-rect.left)/rect.width));
  const tooltip=$('hover-time');
  tooltip.hidden=seek.disabled;tooltip.textContent=clock(fraction*Number(seek.max));tooltip.style.left=(fraction*100)+'%';
});
$('timeline').addEventListener('pointerleave',()=>{$('hover-time').hidden=true;});




