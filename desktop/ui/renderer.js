import { themes, dragSurface } from './organic.js';
import {filterVideoItems,videoWindow} from './video-list.js';
import { AudioDecks, frequencies, presets } from './audio.js';
import { navigation, examples, glyph, parseCaptions } from './library.js';

const { createApp, reactive, computed, watch, onMounted, h } = Vue;
const bridge = window.desktop;
const params = new URLSearchParams(location.search);
const floating = params.get('window') === 'float';
const demo = params.has('demo');
const verifying = params.has('verify');
const rates = [.25,.5,.75,1,1.25,1.5,2];
const labels = Object.fromEntries(navigation.flatMap(group => group.items).map(([id,label]) => [id,label]));
const defaultBars = Array(27).fill(5);
const s = reactive({
  videoSession:null,videoShuffle:false,videoRepeat:'off',videoListOpen:false,videoQuery:'',videoScroll:0,panelKind:'music', panelQuery:'', theme:'nature', motion:'off', drawerPinned:true, drawerMeter:true, spectrum:[0,0,0,0,0], poster:null, mode:floating?(params.get('mode')||'mini'):'normal', page:'home', query:'', notice:'', stage:false,
  backgroundScan:true,loading:false,defaultSuggestion:false,videoProgress:null,items:[], queue:[], favorites:[], history:[], playlists:[], list:'', listName:'', createList:false,
  listEdit:false, queueOpen:true, lyrics:false, track:null, playing:false, time:0, duration:0, shuffle:false,
  repeat:'off', resume:true, positions:{}, nativePlayback:false, nativeLoaded:false, audioName:'', subtitleName:'', muted:false, volume:.68, speed:1, opacity:1, eqOn:true, preset:'Neutre', bands:[...presets.Neutre],
  bass:0, fade:0, bars:defaultBars, theater:false, resolution:'', drag:-1, caption:''
});
if (demo) Object.assign(s, {
  items:examples, track:examples[0], playing:true, time:98, duration:238,
  queue:examples.slice(1,7).map(item=>item.id), history:examples.slice(0,5).map(item=>item.id),
  favorites:[examples[0].id], playlists:[{id:'list-demo',name:'Session nocturne',ids:examples.slice(0,4).map(item=>item.id)}],
  bands:[...presets['Signature bleue']], preset:'Signature bleue', bass:4, fade:4,
  bars:[25,40,66,76,60,42,70,94,78,61,38,49,71,86,60,49,37,64,83,75,57,44,67,51,35,43,27]
});

let engine, cues = [], pendingSeek, noticeTimer, shuffleNext, persistReady = false, loadVersion=0;
const remembered = ['backgroundScan','theme','motion','drawerPinned','drawerMeter','queue','favorites','history','playlists','shuffle','repeat','muted','volume','speed',
  'opacity','eqOn','preset','bands','bass','fade','queueOpen','resume','positions'];
const serialize = value => JSON.parse(JSON.stringify(value));
const itemMap = computed(() => new Map(s.items.map(item=>[item.id,item])));
const byId = id => itemMap.value.get(id);
const byIds = ids => ids.map(byId).filter(Boolean);
const cover = item => item?.cover || '/logo.svg';
const drawerActive=()=>['drawer','mini'].includes(s.mode);
const drawerCover=computed(()=>s.poster?.id===s.track?.id&&s.poster?.url?s.poster.url:cover(s.track));
const meterLevels=computed(()=>s.playing&&!s.muted?s.spectrum:[0,0,0,0,0]);
function drawerVolume(value){bridge.mediaCommand('volume',value).catch(()=>{});}
const artStyle = item => ({ '--art':'url("' + cover(item) + '")' });
const clock = seconds => {
  const value = Math.floor(Math.max(0,seconds||0));
  return Math.floor(value/60) + ':' + String(value%60).padStart(2,'0');
};
const progress = (value,max) => ({ '--fill': Math.min(100,Math.max(0,(value || 0)*100/(max||1))) + '%' });
const pageName = computed(() => labels[s.page] || 'Accueil');
const immersive = computed(() => s.stage || s.mode==='fullscreen');
const selectedList = computed(() => s.playlists.find(list=>list.id===s.list));
const songs = computed(() => s.items.filter(item=>item.type==='music'));
const video = computed(() => s.items.filter(item=>item.type==='video'));
const albums = computed(() => [...new Map(songs.value.filter(item=>item.album).map(item=>[item.artist+'|'+item.album,item])).values()]);
const categories = computed(() => [...new Set(songs.value.map(item=>item.genre).filter(Boolean))].slice(0,8));
const playlistsForHome = computed(() => s.playlists.flatMap(list => {
  const item = byId(list.ids[0]);
  return item ? [{ ...item, title:list.name, artist:list.ids.length+' médias', listId:list.id }] : [];
}));
const shelves = computed(() => [
  {kicker:'REPRENDRE L’ÉCOUTE',title:'Écoutés récemment',page:'history',items:byIds(s.history).slice(0,5),empty:'Vos dernières lectures apparaîtront ici.'},
  {kicker:'DANS VOTRE COLLECTION',title:'Musique recommandée',page:'music',items:[...songs.value].sort((a,b)=>Number(s.favorites.includes(b.id))-Number(s.favorites.includes(a.id))).slice(0,5),empty:'Importez vos titres pour découvrir des suggestions locales.'},
  {kicker:'À EXPLORER',title:'Albums de votre collection',page:'music',items:albums.value.slice(0,5).map(item=>({...item,title:item.album})),empty:'Les albums apparaissent quand vos fichiers contiennent cette information.'},
  {kicker:'VOS SÉLECTIONS',title:'Vos playlists',page:'playlists',items:playlistsForHome.value.slice(0,5),empty:'Créez une playlist pour retrouver vos sélections.'},
  {kicker:'SUR GRAND ÉCRAN',title:'Vidéos à découvrir',page:'video',items:video.value.slice(0,5),empty:'Ajoutez des vidéos à votre collection.'}
]);
const visibleItems = computed(() => {
  switch (s.page) {
    case 'search': {
      const fold = text=>String(text||'').normalize('NFD').replace(/\p{M}/gu,'').toLowerCase();
      const terms = fold(s.query).trim().split(/\s+/);
      return s.items.filter(item=>terms.every(term=>fold([item.title,item.artist,item.album,item.genre].join(' ')).includes(term)));
    }
    case 'music': return songs.value;
    case 'video': return video.value;
    case 'podcast': return s.items.filter(item=>item.type==='podcast');
    case 'favorites': return byIds(s.favorites);
    case 'history': return byIds(s.history);
    case 'playlists': return byIds(selectedList.value?.ids || []);
    case 'downloads': return s.items.filter(item=>item.download);
    default: return s.items;
  }
});

const homeItems = computed(()=>{
  const items=s.panelKind==='queue'?byIds(s.queue):s.items.filter(item=>s.panelKind==='video'?item.type==='video':item.type!=='video');
  const fold=value=>String(value||'').normalize('NFD').replace(/\p{M}/gu,'').toLowerCase();const term=fold(s.panelQuery).trim();
  return term?items.filter(item=>fold(item.title+' '+item.artist).includes(term)):items;
});
const videoItems=computed(()=>filterVideoItems(s.videoSession?.items||[],s.videoQuery));
const visibleVideos=computed(()=>videoWindow(videoItems.value,s.videoScroll,Math.max(400,innerHeight)));
const playbackQueue=computed(()=>s.track?.type==='video'&&s.videoSession?s.videoSession.items.map(item=>item.id):s.queue);
const shuffleOn=computed(()=>s.track?.type==='video'?s.videoShuffle:s.shuffle);
const repeatMode=computed(()=>s.track?.type==='video'?s.videoRepeat:s.repeat);
const repeatLabel = computed(() => ({off:'désactivée',all:'file',one:'média'})[repeatMode.value]);
const curve = computed(() => s.bands.map((gain,i)=>{
  const x=i*100, y=70-gain*5;
  if (!i) return 'M 0 '+y;
  const previous = 70-s.bands[i-1]*5;
  return 'C '+(x-52)+' '+previous+' '+(x-48)+' '+y+' '+x+' '+y;
}).join(' '));
const variantPattern = /\s*(?:[-_]\s*)?[[(]?(?:2160|1440|1080|720|480|360)p[\])]?$/i;
const variants = computed(() => {
  if (s.track?.type !== 'video' || !variantPattern.test(s.track.title)) return [];
  const group = s.track.title.replace(variantPattern,'').trim().toLowerCase();
  return video.value.filter(item=>variantPattern.test(item.title) && item.title.replace(variantPattern,'').trim().toLowerCase()===group);
});
const variantLabel = item => /\d{3,4}p/i.exec(item.title)?.[0] || item.ext;

function notify(message) {
  s.notice = message;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(()=>s.notice='',3200);
}
function go(page) {
  if(s.track?.type==='video'&&s.stage)stopMedia();
  s.page=page; s.stage=false;
  const workspace=document.querySelector('.workspace');
  if (workspace) workspace.scrollTop=0;
}
function theme() { s.theme=s.theme==='dark'?'nature':'dark'; }
function choosePreset() {
  if (presets[s.preset]) s.bands=[...presets[s.preset]];
}
function favorite(item) {
  if (!item) return;
  if(item.type==='video'){notify('Les favoris sont réservés à la musique.');return;}
  const position=s.favorites.indexOf(item.id);
  if(position<0) {s.favorites.push(item.id);notify('Ajouté aux favoris');}
  else {s.favorites.splice(position,1);notify('Retiré des favoris');}
}
function enqueue(item) {
  if (s.queue.includes(item.id)) return;
  s.queue.push(item.id);
  notify('Ajouté à la file d’attente');
}
function moveQueue(target) {
  if (s.drag>=0 && s.drag<s.queue.length && s.drag!==target)
    s.queue.splice(target,0,...s.queue.splice(s.drag,1));
  s.drag=-1;
}
function createPlaylist() {
  const name=s.listName.trim();
  if (!name) return;
  const id=crypto.randomUUID();
  s.playlists.push({id,name,ids:[]});
  s.list=id;s.listName='';s.createList=false;s.listEdit=true;
  notify('Playlist créée');
}
function editPlaylist(id,add) {
  if(byId(id)?.type==='video')return;
  const ids=selectedList.value?.ids;
  if (!ids) return;
  if(add&&!ids.includes(id)) ids.push(id);
  else if(!add&&ids.includes(id)) ids.splice(ids.indexOf(id),1);
}
function deletePlaylist() {
  s.playlists=s.playlists.filter(list=>list.id!==s.list);
  s.list='';s.listEdit=false;
  notify('Playlist supprimée');
}
function include(result,force=false) {
  const items=Array.isArray(result)?result:result?.items||[];
  if(!items.length)return;
  if(result?.session){
    s.videoSession=result.session;s.videoShuffle=false;s.videoRepeat='off';s.videoListOpen=false;s.videoQuery='';s.videoScroll=0;
    const keep=new Set(result.session.items.map(item=>item.id));
    s.items=s.items.filter(item=>item.type!=='video'||keep.has(item.id));
  }
  for(const item of items){const index=s.items.findIndex(value=>value.id===item.id);if(index<0)s.items.push(item);else s.items[index]=item;}
  const music=items.filter(item=>item.type!=='video');
  if(!s.queue.length&&music.length)s.queue=music.map(item=>item.id);
  const selected=byId(result?.selectedId)||items[0];
  if(force||result?.session||!s.track?.url)playItem(selected);
  if(result?.session?.notice)notify(result.session.notice);
}
async function defaultChoice(action){s.defaultSuggestion=false;try{await bridge.defaults(action)}catch{notify('Impossible d’ouvrir les applications par défaut de Windows.')}}
async function openFiles() {
  try { include(await bridge.import()); }
  catch(error) {if(s.track?.type==='video')stopMedia();notify(error.message.replace(/^Error invoking remote method .*?: Error: /,''));}
}
async function dropFiles(event) {
  if(!event.dataTransfer.files.length) return;
  try {
    const items=await bridge.drop(Array.from(event.dataTransfer.files));
    include(items);
    if(!(Array.isArray(items)?items.length:items?.items?.length)) notify('Aucun fichier audio ou vidéo reconnu.');
  } catch { notify('Impossible de lire les fichiers déposés.'); }
}

function initializeTrack(item, fromFade=false) {
  const before=s.track?.type;
  s.track=item;s.caption='';s.resolution='';
  s.duration=0;s.time=0;cues=[];shuffleNext=null;
  if(item.type!=='video')s.history=[item.id,...s.history.filter(id=>id!==item.id)].slice(0,100);
  if(item.type==='video'){s.stage=true;s.panelKind='video';}
  else if(before==='video') s.stage=false;
  if(navigator.mediaSession) navigator.mediaSession.metadata=new MediaMetadata({
    title:item.title,artist:item.artist,album:item.album
  });
  if(item.url&&item.type!=='video') loadCaption('lrc',false);
  if(fromFade) readMedia();
  publish();
}
const audioSettings=()=>serialize({volume:s.volume,muted:s.muted,speed:s.speed,enabled:s.eqOn,bands:s.bands,bass:s.bass,meter:s.drawerMeter&&drawerActive()&&s.track?.type!=='video'});
function nativeAction(promise) { return promise.catch(error=>{notify(error.message.replace(/^Error invoking remote method .*?: Error: /,''));}); }
function stageBounds(){
  if(!bridge?.mediaStage||floating)return;
  const stage=document.querySelector('.immersive-stage');
  if(!s.nativePlayback||s.track?.type!=='video'||!immersive.value||!stage)return bridge.mediaStage(null);
  const {x,y,width,height}=stage.getBoundingClientRect();
  bridge.mediaStage({x,y,width,height});
}
async function playItem(item, startAt=0, paused=false, restart=false) {
  if(!item) return;
  const version=++loadVersion;
  rememberAudioPosition();
  s.loading=true;s.nativeLoaded=false;publish();
  if(item.url){
    try{const prepared=await bridge.prepare(item.id);if(version!==loadVersion)return;item=byId(item.id)||prepared;Object.assign(item,prepared)}
    catch(error){if(version===loadVersion){s.loading=false;stopMedia();notify(error.message.replace(/^Error invoking remote method .*?: Error: /,''))}return}
  }
  if(version!==loadVersion)return;s.loading=false;
  initializeTrack(item);
  s.playing=false;
  s.nativePlayback=Boolean(item.native);
  s.nativeLoaded=false;
  s.audioName='';s.subtitleName='';
  Vue.nextTick(stageBounds);
  if(!item.url) {s.duration=238;s.playing=true;publish();return;}
  const position=startAt || (!s.nativePlayback && s.resume && !restart ? s.positions[item.id] : 0);
  pendingSeek=position>0 ? {id:item.id,time:position} : null;
  if(s.nativePlayback) {
    engine.pause();
    engine.context?.suspend().catch(()=>{});
    try{await bridge.nativeOpen(item.id,{...audioSettings(),time:startAt,paused,resume:s.resume&&!restart});}
    catch(error){if(version===loadVersion){s.playing=false;s.nativeLoaded=false;await bridge.nativeRelease().catch(()=>{});notify(error.message.replace(/^Error invoking remote method .*?: Error: /,''));publish();}}
    return;
  }
  await nativeAction(bridge.nativeRelease());
  if(version!==loadVersion)return;
  try {
    await engine.open(item,s);
    if(paused && s.track?.id===item.id) engine.pause();
  } catch(error) {
    if(error.name!=='AbortError'&&s.track?.id===item.id) fallbackNative(item);
  }
}
function fallbackNative(item){
  if(s.nativePlayback||!item?.url)return;
  s.nativePlayback=true;
  engine.pause();
  Vue.nextTick(stageBounds);
  nativeAction(bridge.nativeOpen(item.id,{...audioSettings(),resume:s.resume}));
}
function trackName(track){
  if(!track)return '';
  const aliases={fra:'fr',fre:'fr',eng:'en',jpn:'ja',mlg:'mg',deu:'de',ger:'de',spa:'es'};
  let lang=track.lang;
  try{if(lang&&lang!=='und')lang=new Intl.DisplayNames(['fr'],{type:'language'}).of(aliases[lang]||lang);}catch{}
  return [track.title,lang&&lang!=='und'?lang:'',String(track.codec||'').toUpperCase()].filter(Boolean).join(' · ');
}
function nativeUpdate(update) {
  if(s.loading||!s.nativePlayback||update.id!==s.track?.id)return;
  const props=update.properties;
  s.nativeLoaded=update.loaded;
  if(update.event==='error'){s.playing=false;notify('mpv n’a pas pu lire ce média.');publish();return;}
  if(update.event==='eof'){s.playing=false;next(true);return;}
  if(update.event==='stopped'){s.time=0;s.playing=false;publish();return;}
  s.time=Number(props['time-pos'])||0;
  s.duration=Number(props.duration)||0;
  s.playing=update.loaded&&props.pause===false;
  const video=props['video-params'];
  s.resolution=video?.w&&video?.h?video.w+' × '+video.h:'';
  const list=props['track-list']||[];
  s.audioName=trackName(list.find(track=>track.type==='audio'&&track.selected));
  s.subtitleName=trackName(list.find(track=>track.type==='sub'&&track.selected));
  publish();
}
function rememberAudioPosition(){
  if(s.nativePlayback||!s.track?.url||s.time<2||s.time>s.duration-2)return;
  const id=s.track.id;
  if(Math.abs((s.positions[id]||0)-s.time)<1)return;
  s.positions[id]=s.time;
  const ids=Object.keys(s.positions);
  if(ids.length>400)delete s.positions[ids[0]];
}
function stopMedia(){
  loadVersion++;s.loading=false;s.playing=false;s.nativeLoaded=false;
  rememberAudioPosition();
  if(s.nativePlayback){nativeAction(bridge.nativeTransport('stop'));return;}
  engine.pause();seek(0);
}
function mediaMenu(kind='main'){
  if(s.track)nativeAction(bridge.mediaMenu(kind));
}
function nativeSettings(){
  if(s.nativePlayback)nativeAction(bridge.nativeSync(audioSettings()));
}
function playFrom(item, items) {
  if(item.listId) {s.list=item.listId;go('playlists');return;}
  if(item.type!=='video')s.queue=[...new Set(items.filter(value=>value.type!=='video').map(value=>value.id))];
  playItem(byId(item.id)||item);
}
function playId(id) { playItem(byId(id)); }
function heroPlay() {
  if(!s.items.length) {openFiles();return;}
  playFrom(byId(s.history[0])||s.items[0],s.items);
}
function upcoming(offset=1,auto=false) {
  const current=s.track?.id,queue=playbackQueue.value;
  if(auto && repeatMode.value==='one') return s.track;
  if(shuffleOn.value && offset>0) {
    if(shuffleNext && queue.includes(shuffleNext.id)) return shuffleNext;
    const candidates=byIds(queue.filter(id=>id!==current));
    if(candidates.length) return shuffleNext=candidates[Math.floor(Math.random()*candidates.length)];
  }
  const index=queue.indexOf(current);
  const candidate=queue[index+offset];
  if(candidate) return byId(candidate);
  if(repeatMode.value==='all') return byId(offset>0?queue[0]:queue.at(-1));
  return null;
}
function next(auto=false) {
  const item=upcoming(1,auto===true);
  if(item) playItem(item);
  else if(auto===true) {s.playing=false;if(s.track?.type==='video')nativeAction(bridge.nativeRelease());publish();}
}
function previous() {
  if(floating){bridge.command('previous');return;}
  if(s.time>3) {seek(0);return;}
  const item=upcoming(-1);
  if(item) playItem(item);
}
function toggle() {
  if(floating){bridge.command('toggle');return;}
  if(!s.track){heroPlay();return;}
  if(!s.track.url){s.playing=!s.playing;publish();return;}
  if(s.nativePlayback) {
    if(s.nativeLoaded) nativeAction(bridge.nativeTransport('toggle'));
    else playItem(s.track,0,false,true);
    return;
  }
  if(!engine.media.paused) engine.pause();
  else {
    if(engine.media.ended) engine.seek(0);
    engine.play().catch(()=>notify('Impossible de reprendre la lecture.'));
  }
}
function seek(value) {
  if(!Number.isFinite(value)) return;
  if(floating) {bridge.command('seek',value);return;}
  s.time=Math.min(s.duration,Math.max(0,value));
  if(s.nativePlayback)nativeAction(bridge.nativeTransport('seek',s.time));
  else engine?.seek(s.time);
}
function toggleShuffle(){if(s.track?.type==='video')s.videoShuffle=!s.videoShuffle;else s.shuffle=!s.shuffle;}
function repeat(){const key=s.track?.type==='video'?'videoRepeat':'repeat';s[key]=s[key]==='off'?'all':s[key]==='all'?'one':'off';}
function toggleQueue() {
  if(s.track?.type==='video'){
    s.videoListOpen=!s.videoListOpen;s.videoScroll=0;
    Vue.nextTick(()=>{const list=document.querySelector('.video-session-list');if(list&&s.videoListOpen){const index=videoItems.value.findIndex(item=>item.id===s.track.id);list.scrollTop=Math.max(0,index*44-list.clientHeight/2);s.videoScroll=list.scrollTop;}});
    return;
  }
  if(immersive.value)leaveStage();s.queueOpen=s.lyrics||!s.queueOpen;s.lyrics=false;
}
function resetVideoSearch(){s.videoScroll=0;const list=document.querySelector('.video-session-list');if(list)list.scrollTop=0;}
function toggleLyrics() {if(immersive.value)leaveStage();s.lyrics=!s.lyrics;s.queueOpen=true;}
function showStage() {s.stage=true;}
function leaveStage() {if(s.mode==='fullscreen'){setMode('fullscreen');if(s.track?.type==='video')return;}if(s.track?.type==='video')stopMedia();s.stage=false;}
function full() {s.stage=s.track?.type==='video'||s.mode!=='fullscreen';setMode('fullscreen');}
function setMode(mode) {
  if (!bridge) return;
  if(s.track?.type==='video'&&['mini','drawer','pill'].includes(mode))mode='video';
  bridge.window(mode,s.opacity);
}
function pip() { if(s.nativePlayback && s.track?.type==='video')setMode('video'); }
function selectVariant(id) {
  const item=byId(id);
  if(item && id!==s.track?.id) playItem(item,s.time,!s.playing);
}

async function loadCaption(ext,pick) {
  const id=s.track?.id;
  if(ext==='srt'&&s.nativePlayback){if(pick)nativeAction(bridge.mediaExternal());return;}
  if(!s.track?.url) {if(pick)notify('Sélectionnez un fichier local.');return;}
  try {
    const text=await bridge.captions(id,ext,pick);
    if(id!==s.track?.id) return;
    if(text) {
      const parsed=parseCaptions(text,ext);
      if(parsed.length) {cues=parsed;if(pick)notify(ext==='srt'?'Sous-titres chargés':'Paroles chargées');}
      else if(pick) notify('Aucun repère temporel reconnu.');
    }
  } catch {if(pick)notify('Impossible de lire ce fichier de texte.');}
}
function readMedia() {
  const media=engine.media;
  s.playing=!media.paused && !media.ended;
  s.time=media.currentTime || 0;
  s.duration=Number.isFinite(media.duration)?media.duration:0;
  publish();
}
function mediaEvent(type) {
  if(!s.track?.url||s.nativePlayback) return;
  if(type==='ended') {delete s.positions[s.track.id];if(!engine.finishFade())next(true);return;}
  if(type==='error') {s.playing=false;fallbackNative(s.track);publish();return;}
  if(type==='loadedmetadata' && pendingSeek?.id===s.track.id) {
    engine.seek(pendingSeek.time);
    pendingSeek=null;
  }
  readMedia();
}
function tick() {
  if(!s.track?.url || !engine) return;
  const media=engine.media;
  const seconds=s.nativePlayback?s.time:media.currentTime||0;
  const changed=Math.abs(seconds-s.time)>.04;
  if(changed) s.time=seconds;
  s.caption=cues.filter(cue=>cue.start<=seconds&&seconds<cue.end).map(cue=>cue.text).join('\n');
  if(!s.nativePlayback&&s.stage&&s.track.type!=='video') s.bars=s.playing?engine.levels():defaultBars;
  if(!s.nativePlayback && s.playing && s.fade && s.repeat!=='one' && s.track.type==='music') {
    const candidate=upcoming(1,true);
    if(candidate?.type==='music'&&candidate.id!==s.track.id&&candidate.url&&!candidate.native) engine.maybeFade(candidate,s.fade);
  }
  if(!s.nativePlayback&&s.drawerMeter&&drawerActive()&&s.playing&&!s.muted)bridge.meter?.(engine.spectrum());
  if(changed)publish();
}
function publish() {
  if(!bridge||floating) return;
  bridge.publish(serialize({track:s.track,playing:s.playing,time:s.time,duration:s.duration,theme:s.theme,motion:s.motion,drawerMeter:s.drawerMeter,nativePlayback:s.nativePlayback,nativeLoaded:s.nativeLoaded&&!s.loading,backgroundScan:s.backgroundScan,opacity:s.opacity,
    speed:s.speed,volume:s.volume,muted:s.muted,repeat:repeatMode.value,shuffle:shuffleOn.value,resume:s.track?.type==='video'?false:s.resume,audioName:s.audioName,subtitleName:s.subtitleName}));
}
function hotkey(event) {
  if(event.defaultPrevented) return;
  if(event.ctrlKey && event.code==='KeyM' && !floating) {event.preventDefault();setMode('mini');return;}
  if(event.ctrlKey && event.code==='KeyK' && !floating) {
    event.preventDefault();go('search');Vue.nextTick(()=>document.querySelector('.search input')?.focus());return;
  }
  if(event.ctrlKey && event.code==='KeyO' && !floating) {event.preventDefault();openFiles();return;}
  if(event.altKey||event.ctrlKey||event.metaKey||/^(INPUT|SELECT|TEXTAREA)$/.test(event.target.tagName))return;
  if(event.code==='Enter'&&event.target.closest('button,a'))return;
  if(event.code==='Space' && event.target.tagName!=='BUTTON') {event.preventDefault();toggle();}
  else if(event.code==='ArrowLeft') seek(s.time-5);
  else if(event.code==='ArrowRight') seek(s.time+5);
  else if(event.code==='ArrowUp'||event.code==='ArrowDown'){event.preventDefault();s.volume=Math.max(0,Math.min(1,s.volume+(event.code==='ArrowUp' ? .04 : -.04)));}
  else if(event.code==='KeyM')s.muted=!s.muted;
  else if((event.code==='KeyF'||event.code==='Enter')&&!floating) {event.preventDefault();full();}
  else if(event.code==='F6'&&!floating){event.preventDefault();toggleQueue();}
  else if(event.code==='F10'&&event.shiftKey){event.preventDefault();mediaMenu();}
  else if(event.code==='Escape') {if(floating)setMode('normal');else if(s.mode==='fullscreen')full();else if(s.track?.type!=='video')leaveStage();}
}

function handleMediaSession() {
  if(!navigator.mediaSession) return;
  const actions={play:()=>{if(!s.playing)toggle();},pause:()=>{if(s.playing)toggle();},
    nexttrack:()=>next(),previoustrack:()=>previous(),seekto:details=>seek(details.seekTime),
    seekforward:details=>seek(s.time+(details.seekOffset||5)),seekbackward:details=>seek(s.time-(details.seekOffset||5))};
  for(const [action,run] of Object.entries(actions))
    try { navigator.mediaSession.setActionHandler(action,run); } catch { /* Optional OS action. */ }
}

const app=createApp({
  components:{glyph:{props:['name'],setup:props=>()=>glyph(h,props.name)}},
  render:window.rakotoRender,
  setup() {
    onMounted(async()=>{
      window.addEventListener('keydown',hotkey);
      const detachDrag=dragSurface(document.querySelector('.app'),bridge.windowDrag,()=>bridge.window('maximize'));
      window.addEventListener('pagehide',detachDrag,{once:true});
      if(floating) {
        bridge.onMode(mode=>{s.mode=mode;});
        bridge.onState(state=>{
          if(s.track?.id!==state.track?.id)s.spectrum=[0,0,0,0,0];
          Object.assign(s,state);
        });
        bridge.onMeter?.(levels=>{s.spectrum=levels;});
        bridge.onPoster?.(poster=>{s.poster=poster;});

        return;
      }
      engine=new AudioDecks(document.getElementById('video-decks'),{event:mediaEvent,promote:item=>initializeTrack(item,true)});
      bridge.onMode(mode=>{
        const previous=drawerActive();
        s.mode=mode;
        Vue.nextTick(stageBounds);
        if(previous!==drawerActive())nativeSettings();
      });
      bridge.onNative(nativeUpdate);
      bridge.onVideoProgress?.(progress=>{if(progress.id===s.videoSession?.id)s.videoProgress=progress});
      bridge.onOpenedFiles(result=>include(result,true));
      bridge.onMediaUpdated?.(item=>{
        const existing=byId(item.id);if(existing)Object.assign(existing,item);
        const listed=s.videoSession?.items.find(value=>value.id===item.id);if(listed)Object.assign(listed,item);
        if(s.track?.id===item.id)Object.assign(s.track,item);
      });
      bridge.onMediaNotice(notify);
      const stageObserver=new ResizeObserver(stageBounds);
      stageObserver.observe(document.querySelector('.immersive-stage'));
      bridge.onCommand(({action,value})=>{
        if(action==='defaultSuggestion')s.defaultSuggestion=true;
        else if(action==='toggle')toggle();
        else if(action==='openError'){stopMedia();notify(value);}
        else if(action==='next')next();
        else if(action==='previous')previous();
        else if(action==='seek')seek(value);
        else if(action==='stop')stopMedia();
        else if(action==='queue')toggleQueue();
        else if(action==='playVideo')playId(value);
        else if(action==='speed')s.speed=value;
        else if(action==='volume')s.volume=Math.max(0,Math.min(1,value));
        else if(action==='mute')s.muted=!s.muted;
        else if(action==='shuffle')toggleShuffle();
        else if(action==='repeat')s[s.track?.type==='video'?'videoRepeat':'repeat']=value;
        else if(action==='resume')s.resume=!s.resume;
        else if(action==='sync')nativeSettings();
        else if(action==='audioEngine'&&s.track?.url&&s.track.type!=='video'){
          const native=value==='native';
          if(native!==s.nativePlayback)playItem({...s.track,native},s.time,!s.playing,true);
        }
        else if(action==='library')leaveStage();
        else if(action==='equalizer'||action==='settings'){
          if(s.mode==='fullscreen')setMode('fullscreen');
          if(['drawer','mini','pill','video'].includes(s.mode))setMode('normal');
          go(action==='equalizer'?'eq':'settings');
        }
      });
      if(!demo) {
        try {
          const {items,memory}=await bridge.load();
          s.items=items;
          for(const key of remembered) if(key in memory) s[key]=memory[key];
          s.motion='off';
          s.queue=s.queue.filter(id=>byId(id));
          s.history=s.history.filter(id=>byId(id));
        } catch {notify('Impossible de charger la bibliothèque.');}
        persistReady=true;
      }
      const tickTimer=setInterval(tick,135);
      const positionTimer=setInterval(rememberAudioPosition,12000);
      window.addEventListener('pagehide',()=>{
        rememberAudioPosition();
        clearInterval(tickTimer);clearInterval(positionTimer);stageObserver.disconnect();
        window.removeEventListener('keydown',hotkey);
      },{once:true});

      handleMediaSession();
      publish();
      if(verifying) window.rakotoTest={
        scene(name) {
          if(name==='home'){s.stage=false;s.mode='normal';s.theme='light';go('home');}
          if(name==='eq'){s.stage=false;s.mode='normal';s.theme='dark';go('eq');}
          if(name==='immersive'){s.stage=true;s.mode='fullscreen';s.theme='dark';s.caption='Là où la nuit rejoint l’océan';}
          publish();
        },
        add:items=>{s.items.push(...items);s.queue=items.map(item=>item.id);},
        play:playId,
        crossfade(seconds){s.fade=seconds;},
        appearance(theme,motion){s.theme=theme;s.motion=motion;},
        state:()=>serialize({ id:s.track?.id,playing:s.playing,time:s.time,duration:s.duration,
          mode:s.mode,stage:s.stage,videoListOpen:s.videoListOpen,session:s.videoSession?.items.map(item=>item.id),queue:[...s.queue],history:[...s.history],playlists:serialize(s.playlists),motion:s.motion,caption:s.caption,resolution:s.resolution,graph:Boolean(engine.context),gain:engine.filters?.[0].gain.value,
          native:s.nativePlayback, loaded:s.nativeLoaded, audio:s.audioName, subtitle:s.subtitleName,
          notice:s.notice, mediaError:engine.media.error?.message, readyState:engine.media.readyState }),
        seek,toggle, menu:mediaMenu
      };
    });
    if(!floating) {
      watch(()=>remembered.map(key=>s[key]),()=>{
        if(persistReady)bridge.save(serialize(Object.fromEntries(remembered.map(key=>[key,s[key]]))));
      },{deep:true});
      watch(()=>[s.volume,s.muted,s.speed,s.bass,s.eqOn,...s.bands],()=>{
        if(s.nativePlayback)nativeSettings();else engine?.sync(s);
        publish();
      });
      watch(()=>[s.stage,s.track?.id,s.mode,s.nativePlayback,s.videoListOpen],()=>Vue.nextTick(stageBounds));
      watch(()=>[s.repeat,s.shuffle,s.resume,s.videoRepeat,s.videoShuffle],publish);
      watch(()=>[s.theme,s.motion,s.opacity,s.drawerMeter],publish);
      watch(()=>s.drawerMeter,nativeSettings);
      watch(()=>s.backgroundScan,publish);
      watch(()=>s.shuffle,()=>shuffleNext=null);
    }
    return {s,floating,themes,homeItems,shuffleOn,repeatMode,toggleShuffle,videoItems,visibleVideos,drawerCover,meterLevels,drawerVolume,navigation,pageName,immersive,shelves,visibleItems,selectedList,categories,presets,frequencies,curve,repeatLabel,
      rates,variants,variantLabel,selectVariant,cover,artStyle,clock,progress,byId,go,theme,choosePreset,favorite,enqueue,moveQueue,createPlaylist,
      editPlaylist,deletePlaylist,defaultChoice,openFiles,dropFiles,playFrom,playId,heroPlay,toggle,seek,repeat,previous,
      next:()=>floating?bridge.command('next'):next(),toggleQueue,resetVideoSearch,toggleLyrics,showStage,leaveStage,full,setMode,pip,loadCaption,mediaMenu};
  }
});
app.mount('#app');





