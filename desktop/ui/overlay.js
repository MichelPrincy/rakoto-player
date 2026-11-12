import {filterVideoItems,videoWindow} from './video-list.js';
const bridge=window.desktop,$=id=>document.getElementById(id);
const surface=$('surface'),bar=$('glass'),seek=$('seek'),volume=$('volume'),panel=$('session-list'),list=$('video-items');
let packet={},session=null,timer,hideListTimer,renderFrame=0,held=false,menu=false,nativeMenu=false,listOpen=false,listHovered=false,chaptersKey='',lastPoint,drag,renderKey='',activeId;
const clock=value=>{const time=Math.max(0,Math.floor(Number(value)||0)),hours=Math.floor(time/3600),minutes=Math.floor(time/60)%60;return (hours?hours+':'+String(minutes).padStart(2,'0'):minutes)+':'+String(time%60).padStart(2,'0')};
const clear=()=>{clearTimeout(timer);timer=null};
const keyboardInteraction=()=>document.activeElement?.matches('input:focus-visible,button:focus-visible')&&Boolean(document.activeElement.closest('.glass,#session-list'));
function schedule(){clear();if(!packet.fullscreen||held||menu||nativeMenu||listOpen||keyboardInteraction())return;timer=setTimeout(()=>{if(!held&&!menu&&!nativeMenu&&!listOpen&&!keyboardInteraction()){document.body.classList.remove('active');$('hover-time').hidden=true}},3000)}
function show(){document.body.classList.add('active');schedule()}
function command(action,value){return bridge.mediaCommand(action,value).catch(()=>{})}
async function openMenu(kind){if(menu)return;menu=true;show();try{await bridge.mediaMenu(kind)}finally{menu=false;show()}}
function language(code){const aliases={fra:'fr',fre:'fr',eng:'en',jpn:'ja',mlg:'mg',deu:'de',ger:'de',spa:'es'};if(!code||code==='und')return '';try{return new Intl.DisplayNames(['fr'],{type:'language'}).of(aliases[code]||code)}catch{return code}}
function renderList(){
 cancelAnimationFrame(renderFrame);renderFrame=0;const items=filterVideoItems(session?.items||[],$('list-search').value),windowed=videoWindow(items,list.scrollTop,list.clientHeight||600);
 $('list-count').textContent=items.length+' vidéo'+(items.length>1?'s':'')+' · rien n’est sauvegardé';
 const key=JSON.stringify([session?.id,$('list-search').value,windowed.before,windowed.after,windowed.rows.map(item=>item.analysis)]);
 if(key!==renderKey){
  renderKey=key;const fragment=document.createDocumentFragment();
  const spacer=height=>{const node=document.createElement('div');node.style.height=height+'px';fragment.append(node)};
  spacer(windowed.before);
  for(const item of windowed.rows){const row=document.createElement('button');row.type='button';row.className='session-row';row.dataset.id=item.id;row.title=(item.fileName||item.title)+(item.analysis==='ready'?' · Analysé':item.analysis==='error'?' · Réessayer l’analyse':' · Analyse à la demande');row.textContent=item.fileName||item.title;row.addEventListener('click',()=>command('playVideo',item.id));fragment.append(row)}
  spacer(windowed.after);list.replaceChildren(fragment);
 }
 for(const row of list.querySelectorAll('button')){row.classList.toggle('selected',row.dataset.id===activeId);if(row.dataset.id===activeId)row.setAttribute('aria-current','true');else row.removeAttribute('aria-current')}
}
function queueRender(){if(!renderFrame)renderFrame=requestAnimationFrame(renderList)}
function focusCurrent(){const items=filterVideoItems(session?.items||[],$('list-search').value),index=items.findIndex(item=>item.id===activeId);if(index>=0)list.scrollTop=Math.max(0,index*44-list.clientHeight/2);queueRender()}
function setList(open,keyboard=false){
 clearTimeout(hideListTimer);listOpen=Boolean(open&&packet.fullscreen&&session?.items.length);panel.hidden=!listOpen;
 document.body.classList.toggle('session-open',listOpen);$('list-toggle').setAttribute('aria-expanded',String(listOpen));
 if(listOpen){focusCurrent();renderList();if(keyboard)$('list-search').focus()}
 else if(panel.contains(document.activeElement))document.activeElement.blur();
 show();
}
function hideListSoon(){clearTimeout(hideListTimer);hideListTimer=setTimeout(()=>{if(!listHovered&&!menu&&!nativeMenu&&!panel.contains(document.activeElement))setList(false)},180)}
function setSession(value){session=value;renderKey='';$('folder-name').textContent=value?.folderName||'Vidéos';$('list-search').value='';list.scrollTop=0;setList(false);renderList()}
function update(data){
 const changed=packet.fullscreen!==data.fullscreen||packet.mode!==data.mode;packet=data;
 const props=data.native?.properties||{},state=data.state||{};document.documentElement.dataset.theme=state.theme==='dark'?'dark':'light';
 const time=Number(props['time-pos']??state.time)||0,duration=Number(props.duration??state.duration)||0,tracks=props['track-list']||[];
 document.body.classList.toggle('fullscreen',Boolean(data.fullscreen));document.body.classList.toggle('compact',data.mode==='video');
 if(changed){held=false;drag=null;lastPoint=null;clear();setList(false);show()}
 $('elapsed').textContent=clock(time);$('duration').textContent=clock(duration);seek.disabled=!duration;seek.max=duration||1;
 if(!held)seek.value=time;seek.style.setProperty('--progress',Math.min(100,100*time/(duration||1))+'%');
 if(!held)volume.value=state.volume??.68;volume.style.setProperty('--progress',100*volume.value+'%');
 $('play').textContent=state.playing?'Ⅱ':'▶';$('play').setAttribute('aria-label',state.playing?'Pause':'Lire');
 const audio=tracks.find(track=>track.type==='audio'&&track.selected),subtitle=tracks.find(track=>track.type==='sub'&&track.selected);
 $('audio').textContent=language(audio?.lang)||'Audio';$('audio').title=audio?[language(audio.lang),audio.title,audio.codec,audio['demux-channels']].filter(Boolean).join(' · '):'Audio désactivé ou absent';
 $('subtitle').textContent=subtitle?(language(subtitle.lang)||'CC actif'):'CC';$('subtitle').title=subtitle?[language(subtitle.lang),subtitle.title,subtitle.codec].filter(Boolean).join(' · '):'Sous-titres désactivés';
 $('speed').textContent=(Number(props.speed||state.speed)||1).toLocaleString('fr')+'×';$('full').title=data.fullscreen?'Quitter le plein écran':'Plein écran';
 $('float').dataset.command=data.mode==='video'?'normal':'video';$('float').title=data.mode==='video'?'Restaurer la fenêtre':'Vidéo flottante';
 if(activeId!==state.track?.id){activeId=state.track?.id;if(listOpen)focusCurrent();else queueRender()}
 const chapters=data.chapters||[],key=JSON.stringify([duration,chapters]);
 if(chaptersKey!==key){chaptersKey=key;$('marks').replaceChildren();if(duration)for(const chapter of chapters){const marker=document.createElement('button');marker.type='button';marker.className='chapter';marker.style.left=Math.min(100,100*chapter.time/duration)+'%';marker.title=chapter.title+' · '+clock(chapter.time);marker.setAttribute('aria-label',marker.title);marker.addEventListener('click',()=>command('seek',chapter.time));$('marks').append(marker)}}
}
bridge.onVideoItem?.(item=>{const value=session?.items.find(value=>value.id===item.id);if(value){Object.assign(value,item);queueRender()}});bridge.onOverlay(update);bridge.onVideoSession(setSession);bridge.videoSession().then(setSession).catch(()=>{});
bridge.onMenuLock(locked=>{nativeMenu=locked;show()});
surface.addEventListener('pointerdown',event=>{if(packet.mode!=='video'||event.button!==0||event.target.closest('button,input,.glass,#session-list'))return;drag={id:event.pointerId,x:event.screenX,y:event.screenY};surface.setPointerCapture(event.pointerId)});
surface.addEventListener('lostpointercapture',()=>{drag=null});
surface.addEventListener('pointermove',event=>{
 if(drag&&event.pointerId===drag.id){bridge.mediaDrag(event.screenX-drag.x,event.screenY-drag.y);drag.x=event.screenX;drag.y=event.screenY}
 if(!lastPoint||event.screenX!==lastPoint[0]||event.screenY!==lastPoint[1]){lastPoint=[event.screenX,event.screenY];show()}
 if(packet.fullscreen){if(event.clientX>=innerWidth-18&&!listOpen)setList(true);else if(listOpen&&!panel.contains(event.target))hideListSoon()}
});
surface.addEventListener('pointerenter',show);surface.addEventListener('contextmenu',event=>{event.preventDefault();openMenu('main')});
surface.addEventListener('dblclick',event=>{if(!event.target.closest('button,input,.glass,#session-list'))command('fullscreen')});
surface.addEventListener('wheel',event=>{if(!packet.fullscreen||!packet.wheel||event.target.closest('input,#session-list'))return;event.preventDefault();show();command('volume',Math.max(0,Math.min(1,Number(volume.value)-Math.sign(event.deltaY)*.04)))},{passive:false});
bar.addEventListener('pointerdown',()=>{held=true;show()});bar.addEventListener('focusin',show);bar.addEventListener('focusout',schedule);
window.addEventListener('pointerup',()=>{if(held){held=false;show()}});window.addEventListener('pointercancel',()=>{held=false;show()});window.addEventListener('blur',()=>{held=false;schedule()});
window.addEventListener('pagehide',()=>{clear();clearTimeout(hideListTimer);cancelAnimationFrame(renderFrame)});
window.addEventListener('keydown',event=>{
 if(event.ctrlKey||event.altKey||event.metaKey)return;show();
 if(event.code==='Escape'){event.preventDefault();if(listOpen)setList(false);else if(packet.fullscreen)command('fullscreen');else if(packet.mode==='video')command('normal');return}
 if(event.code==='F6'){event.preventDefault();if(packet.fullscreen)setList(!listOpen,true);else command('queue');return}
 if(event.code==='F10'&&event.shiftKey){event.preventDefault();openMenu('main');return}
 if(event.target.closest('input,button'))return;
 if(event.code==='Space'){event.preventDefault();command('toggle')}
 if(event.code==='ArrowLeft'||event.code==='ArrowRight'){event.preventDefault();command('seek',Math.max(0,Math.min(Number(seek.max),Number(seek.value)+(event.code==='ArrowLeft'?-5:5))))}
 if(event.code==='ArrowUp'||event.code==='ArrowDown'){event.preventDefault();command('volume',Math.max(0,Math.min(1,Number(volume.value)+(event.code==='ArrowUp' ? .04 : -.04))))}
 if(event.code==='KeyM')command('mute');
 if(event.code==='KeyF'||event.code==='Enter'){event.preventDefault();command('fullscreen')}
});
$('back').addEventListener('click',()=>command(packet.mode==='video'?'normal':'library'));
for(const button of document.querySelectorAll('[data-command]'))button.addEventListener('click',()=>command(button.dataset.command));
for(const button of document.querySelectorAll('[data-menu]'))button.addEventListener('click',()=>openMenu(button.dataset.menu));
$('list-toggle').addEventListener('click',()=>packet.fullscreen?setList(!listOpen):command('queue'));$('list-close').addEventListener('click',()=>setList(false));
panel.addEventListener('pointerenter',()=>{listHovered=true;clearTimeout(hideListTimer);show()});panel.addEventListener('pointerleave',()=>{listHovered=false;hideListSoon()});panel.addEventListener('focusout',hideListSoon);
$('list-search').addEventListener('input',()=>{list.scrollTop=0;renderKey='';queueRender()});list.addEventListener('scroll',queueRender,{passive:true});window.addEventListener('resize',queueRender);
seek.addEventListener('input',()=>{show();seek.style.setProperty('--progress',(100*seek.value/seek.max)+'%')});seek.addEventListener('change',()=>command('seek',Number(seek.value)));
volume.addEventListener('input',()=>{show();command('volume',Number(volume.value))});
$('timeline').addEventListener('pointermove',event=>{const rect=seek.getBoundingClientRect(),fraction=Math.max(0,Math.min(1,(event.clientX-rect.left)/rect.width)),tooltip=$('hover-time');tooltip.hidden=seek.disabled;tooltip.textContent=clock(fraction*Number(seek.max));tooltip.style.left=(fraction*100)+'%'});
$('timeline').addEventListener('pointerleave',()=>{$('hover-time').hidden=true});
