const { app, BaseWindow, BrowserWindow, dialog, ipcMain, Menu, protocol, screen, session } = require('electron');
const fs = require('node:fs/promises');
const { createReadStream } = require('node:fs');
const { Readable } = require('node:stream');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { probe, types } = require('./media-probe.cjs');
const { installAdvanced } = require('./advanced.cjs');

protocol.registerSchemesAsPrivileged([{ scheme: 'rakoto', privileges: {
  standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true
} }]);

app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
app.setName('RAKOTO Player');
const demo = process.argv.includes('--demo');
const advancedVerify = process.argv.includes('--verify-advanced');
const verify = process.argv.includes('--verify') || advancedVerify;
if (verify) {
  app.commandLine.appendSwitch('mute-audio');
  app.setPath('userData', path.join(app.getPath('temp'), 'rakoto-verify-' + process.pid));
}
const ui = path.join(__dirname, 'ui');
const base = 'rakoto://app';
const files = new Map();
const catalog = new Map();
const assets = new Set(['index.html', 'style.css', 'desktop.css', 'logo.svg', 'organic.css', 'organic.js', 'hautes-terres.svg', 'vue.js', 'generated.js', 'renderer.js', 'library.js', 'audio.js', 'logo.png', 'overlay.html', 'overlay.js', 'media-windows.css', 'media-dialog.html', 'media-dialog.js']);
const metadata = import('music-metadata');
const icon = path.join(ui, 'rakoto.ico');
let main, floating, floatRevision=0, closing = false, mode = 'normal', lastState;
const windowDrags=new Map();
let skipDrawerMinimize=false, posterId, poster, posterAttempts=0, posterNext=0, posterBusy=false;
const drawerMode=()=>['drawer','mini'].includes(mode);
const drawerPinned=()=>memory.drawerPinned!==false;
const emitMotion=(win,value)=>{
  if(win&&!win.isDestroyed()&&lastState?.motion!=='off')win.webContents.send('window:motion',value);
};
function watchMotion(win){
  let previous=win.getBounds(), timer;
  const schedule=()=>{
    if(timer)return;
    timer=setTimeout(()=>{
      timer=null;
      if(win.isDestroyed()||windowDrags.has(win.webContents.id))return;
      const bounds=win.getBounds();
      const movement={x:Math.max(-17,Math.min(17,previous.x-bounds.x)),
        y:Math.max(-17,Math.min(17,previous.y-bounds.y)),
        scale:Math.max(-1,Math.min(1,(bounds.width-previous.width+bounds.height-previous.height)/110))};
      previous=bounds;
      if(movement.x||movement.y||movement.scale)emitMotion(win,movement);
    },32);
  };
  win.on('move',schedule);win.on('resize',schedule);
  win.on('will-resize',(_event,bounds)=>{
    emitMotion(win,{scale:Math.max(-1,Math.min(1,(bounds.width-previous.width+bounds.height-previous.height)/90))});
    previous=bounds;
  });
  const id=win.webContents.id;
  win.on('closed',()=>{clearTimeout(timer);windowDrags.delete(id);});
}

function forwardMeter(levels,native,id){
  if(!drawerMode()||!floating?.isVisible()||lastState?.drawerMeter===false||!lastState?.playing||lastState.muted||
    Boolean(lastState.nativePlayback)!==native||(id&&lastState.track?.id!==id)||
    !Array.isArray(levels)||levels.length!==5||!levels.every(Number.isFinite))return;
  floating.webContents.send('player:meter',levels.map(level=>Math.min(1,Math.max(0,level))));
}

function refreshPoster(){
  const track=lastState?.track;
  if(!drawerMode()||!floating?.isVisible()||track?.type!=='video'||!advanced)return;
  if(posterId!==track.id){posterId=track.id;poster=null;posterAttempts=0;posterNext=0;posterBusy=false;}
  if(poster){
    if(!poster.sent){
      floating.webContents.send('player:poster',{id:track.id,url:poster.url});
      poster.sent=true;
    }
    return;
  }
  if(posterBusy||posterAttempts>=3||Date.now()<posterNext||lastState.time<.25)return;
  posterBusy=true;posterAttempts++;posterNext=Date.now()+900;
  advanced.poster(track.id).then(url=>{
    if(posterId!==track.id)return;
    posterBusy=false;
    if(!url)return;
    poster={url,sent:false};
    if(lastState?.track?.id===track.id)refreshPoster();
  }).catch(()=>{if(posterId===track.id)posterBusy=false;});
}
let settings = {}, memory = {}, writeChain = Promise.resolve(), moveTimer;
let stateFile, advanced;
const startup = verify ? [] : process.argv.slice(app.isPackaged ? 1 : 2);
const primary = verify || app.requestSingleInstanceLock();
const waiting = [];
if(!primary)app.quit();
if(!verify&&primary)app.on('second-instance',(_event,args)=>{
  const incoming=args.slice(app.isPackaged ? 1 : 2);
  if(!main||main.webContents.isLoading())waiting.push(...incoming);
  else showStartup(incoming).catch(console.error);
});
async function showStartup(args){
  if(args.includes('--settings'))main.webContents.send('player:command',{action:'settings'});
  const paths=args.filter(argument=>path.isAbsolute(argument));
  if(paths.length){
    const items=await registerList(paths);
    if(items.length){await store();main.webContents.send('library:opened',items);}
  }
  if(mode!=='normal'||main.isMinimized())await changeWindow('normal');
  else {main.show();main.focus();}
}
const requestFrom = (event, win) => Boolean(win && !win.isDestroyed() &&
  event.sender === win.webContents && event.senderFrame === win.webContents.mainFrame &&
  event.senderFrame.url.startsWith(base + '/'));

function store() {
  const data = JSON.stringify({ memory, settings, paths: Array.from(files.values(), item => item.path) });
  const next = stateFile + '.tmp';
  writeChain = writeChain.catch(() => {}).then(async () => {
    await fs.writeFile(next, data);
    await fs.rename(next, stateFile);
  }).catch(error => console.error('Sauvegarde RAKOTO', error.message));
  return writeChain;
}

async function register(file, enrich = true) {
  if (typeof file !== 'string' || !path.isAbsolute(file)) return null;
  const ext = path.extname(file).slice(1).toLowerCase();

  const stat = await fs.stat(file).catch(() => null);
  if (!stat?.isFile()) return null;
  const id = createHash('sha256').update(path.normalize(file).toLowerCase()).digest('hex').slice(0, 32);
  if (catalog.has(id)) return catalog.get(id);
  const name = path.basename(file, path.extname(file));
  const [artist, title] = name.includes(' - ') ? name.split(/ - (.+)/) : ['', name];
  const analysis = await probe(file).catch(() => null);
  if (!analysis) return null;
  const mime = analysis.webMime || types[ext] || 'application/octet-stream';
  const record = { path: file, mime, probe: analysis };
  files.set(id, record);
  const item = { id, title, artist: artist || 'Artiste inconnu', album: '', genre: '',
    type: analysis.video.length ? 'video' : 'music', native:analysis.native, url: '/media/' + id,
    download: isUnder(file, app.getPath('downloads')), ext: ext.toUpperCase() };
  if (enrich && item.type === 'music') {
    try {
      const info = await (await metadata).parseFile(file, { duration: false });
      const tags = info.common;
      Object.assign(item, { title: tags.title || title, artist: tags.artist || item.artist,
        album: tags.album || '', genre: tags.genre?.[0] || '',
        type: tags.podcast || /podcast/i.test(tags.genre?.join(' ') || name) ? 'podcast' : 'music' });
      const picture = tags.picture?.find(value => /^image\/(jpeg|png|webp)$/.test(value.format) && value.data.length < 8_000_000);
      if (picture) {
        record.cover = picture;
        item.cover = '/cover/' + id;
      }
    } catch { /* Files without readable tags keep their filenames. */ }
  }
  catalog.set(id, item);
  return item;
}

function isUnder(file, directory) {
  const relative = path.relative(directory, file);
  return relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative);
}

async function registerList(paths) {
  const result = [];
  for (let offset=0;offset<Math.min(paths.length,600);offset+=4) {
    const batch=await Promise.all(paths.slice(offset,offset+4).map(file=>register(file)));
    result.push(...batch.filter(Boolean));
  }
  return result;
}

async function streamFile(request, file, mime) {
  const stat = await fs.stat(file).catch(() => null);
  if (!stat?.isFile()) return new Response(null, { status: 404 });
  const headers = { 'Content-Type': mime, 'Accept-Ranges': 'bytes' };
  const range = request.headers.get('range');
  let start = 0, end = stat.size - 1, status = 200;
  if (range) {
    const parts = /^bytes=(\d*)-(\d*)$/.exec(range);
    if (!parts || (!parts[1] && !parts[2])) return new Response(null, { status: 416, headers: { 'Content-Range': 'bytes */' + stat.size } });
    if (parts[1]) {
      start = Number(parts[1]);
      if (parts[2]) end = Math.min(Number(parts[2]), end);
    } else start = Math.max(0, stat.size - Number(parts[2]));
    if (!Number.isSafeInteger(start) || start > end) return new Response(null, { status: 416, headers: { 'Content-Range': 'bytes */' + stat.size } });
    status = 206;
    headers['Content-Range'] = 'bytes ' + start + '-' + end + '/' + stat.size;
  }
  headers['Content-Length'] = String(Math.max(0, end - start + 1));
  const body = request.method === 'HEAD' || end < 0 ? null : Readable.toWeb(createReadStream(file, { start, end }));
  return new Response(body, { status, headers });
}

function makeWindow({ webPreferences = {}, ...options }) {
  const win = new BrowserWindow({
    show: false, icon, autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'preload.cjs'), nodeIntegration: false,
      sandbox: true, contextIsolation: true, backgroundThrottling: true, ...webPreferences },
    ...options
  });
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', event => event.preventDefault());
  return win;
}

function geometry(kind, anchor) {
  const size = {pill:[218,72],drawer:[310,96],mini:[420,152],video:[420,266]}[kind];
  const display = anchor ? screen.getDisplayMatching(anchor) : screen.getDisplayNearestPoint(screen.getCursorScreenPoint());
  const work = display.workArea;
  const margin = 12;
  const maxX = Math.max(work.x, work.x + work.width - size[0] - margin);
  const maxY = Math.max(work.y, work.y + work.height - size[1] - margin);
  return { width: size[0], height: size[1],
    x: anchor ? Math.round(Math.min(maxX, Math.max(work.x, anchor.x))) : maxX,
    y: anchor ? Math.round(Math.min(maxY, Math.max(work.y, anchor.y))) : maxY };
}

function rememberPosition(win) {
  if (win !== floating || mode === 'normal') return;
  const box = win.getBounds();
  const area = screen.getDisplayMatching(box).workArea;
  const x = [area.x + 12, area.x + area.width - box.width - 12].find(edge => Math.abs(edge - box.x) <= 34) ?? box.x;
  const y = [area.y + 12, area.y + area.height - box.height - 12].find(edge => Math.abs(edge - box.y) <= 34) ?? box.y;
  if (x !== box.x || y !== box.y) win.setPosition(x, y);
  if (settings[mode]?.x === x && settings[mode]?.y === y) return;
  settings[mode] = { ...box, x, y };
  store();
}

function sendMode() {
  main.webContents.send('window:changed', mode);
  if (floating && !floating.isDestroyed()) floating.webContents.send('window:changed', mode);
}

async function changeWindow(kind, opacity) {
  if (!main || main.isDestroyed()) return;
  if (kind === 'fullscreen') {
    if (mode!=='normal') await changeWindow('normal');
    main.setFullScreen(!main.isFullScreen());
    return;
  }
  if (kind === 'maximize') { main.isMaximized() ? main.unmaximize() : main.maximize(); return; }
  if (kind === 'exit') { app.quit(); return; }
  if (kind === 'hide-drawer') {
    await changeWindow('normal');
    if(!main.isMinimized()){skipDrawerMinimize=true;main.minimize();}
    return;
  }
  if (kind === 'normal') {
    floatRevision++;
    const old=floating;
    if(old&&!old.isDestroyed()){
      rememberPosition(old);
      old.hide();
    }
    floating=null;
    mode=kind;
    if(main.isMinimized())main.restore();
    main.show();
    main.focus();
    emitMotion(main,{scale:1});
    sendMode();
    advanced?.place();
    if(old&&!old.isDestroyed()){
      advanced?.detachWindow(old);
      old.destroy();
    }
    return;
  }
  if (!['drawer', 'mini', 'pill', 'video'].includes(kind)) return;
  if (mode===kind && floating?.isVisible()) return;
  if (main.isFullScreen()) main.setFullScreen(false);
  const revision=++floatRevision;
  const old=floating;
  if(old&&!old.isDestroyed()&&mode!=='normal')rememberPosition(old);
  const fallback=old&&!old.isDestroyed()?old.getBounds():null;
  // Each Windows layered surface keeps its original size and is presented only after its first paint.
  const next=makeWindow({...geometry(kind,settings[kind]||fallback),frame:false,transparent:true,
    resizable:false,alwaysOnTop:drawerPinned(),skipTaskbar:true,backgroundColor:'#00000000'});
  watchMotion(next);
  advanced?.watchWindow(next);
  next.on('system-context-menu',event=>{
    event.preventDefault();
    if(next===floating)advanced?.popup(next,'main').catch(console.error);
  });
  next.on('move',()=>{
    if(next!==floating)return;
    clearTimeout(moveTimer);
    moveTimer=setTimeout(()=>!next.isDestroyed()&&!windowDrags.has(next.webContents.id)&&rememberPosition(next),220);
  });
  next.on('closed',()=>{
    if(next!==floating)return;
    floating=null;
    if(!closing&&!verify)changeWindow('normal').catch(console.error);
  });
  let timeout;
  const painted=new Promise(resolve=>{
    next.once('ready-to-show',()=>resolve(true));
    next.once('closed',()=>resolve(false));
    timeout=setTimeout(()=>resolve(false),5000);
  });
  try {
    const [ready]=await Promise.all([painted,next.loadURL(base+'/?window=float&mode='+kind)]);
    if(!ready||revision!==floatRevision||closing||main.isDestroyed()) {
      if(!next.isDestroyed())next.destroy();
      return;
    }
  }catch(error){
    if(!next.isDestroyed())next.destroy();
    throw error;
  }finally{clearTimeout(timeout);}
  clearTimeout(moveTimer);
  floating=next;
  mode=kind;
  sendMode();
  if (lastState) floating.webContents.send('player:state', lastState);
  floating.showInactive();
  emitMotion(floating,{y:kind==='drawer'?5:-5,scale:kind==='mini'?1:-1});
  if(poster)poster.sent=false;
  refreshPoster();
  main.hide();
  advanced?.place();
  if(old&&!old.isDestroyed()){
    advanced?.detachWindow(old);
    old.destroy();
  }
}

function installIpc() {
  ipcMain.on('window:drag',(event,value)=>{
    const win=[main,floating].find(window=>requestFrom(event,window));
    if(!win||!value||typeof value!=='object')return;
    const id=event.sender.id;
    if(value.phase==='end'){windowDrags.delete(id);if(win===floating)rememberPosition(win);return;}
    if(!Number.isFinite(value.x)||!Number.isFinite(value.y)||Math.abs(value.x)>100000||Math.abs(value.y)>100000)return;
    if(value.phase==='start'){
      if(win===main&&main.isFullScreen())return;
      if(win.isMaximized()){
        const bounds=win.getBounds(),normal=win.getNormalBounds();
        const fraction=Math.max(.1,Math.min(.9,(value.x-bounds.x)/bounds.width));
        win.unmaximize();win.setPosition(Math.round(value.x-normal.width*fraction),Math.round(value.y-22));
      }
      windowDrags.set(id,{x:value.x,y:value.y,bounds:win.getBounds()});
    }else if(value.phase==='move'){
      const drag=windowDrags.get(id);if(!drag)return;
      win.setPosition(Math.round(drag.bounds.x+value.x-drag.x),Math.round(drag.bounds.y+value.y-drag.y));
    }
  });

  ipcMain.handle('library:load', event => requestFrom(event, main)
    ? { items: [...catalog.values()], memory } : null);
  ipcMain.handle('library:import', async event => {
    if (!requestFrom(event, main)) return [];
    const picked = await dialog.showOpenDialog(main, { title: 'Importer des médias',
      properties: ['openFile', 'multiSelections'], filters: [{ name: 'Audio et vidéo', extensions: Object.keys(types) }, { name:'Tous les fichiers', extensions:['*'] }] });
    if (picked.canceled) return [];
    const items = await registerList(picked.filePaths);
    await store();
    return items;
  });
  ipcMain.handle('library:drop', async (event, paths) => {
    if (!requestFrom(event, main) || !Array.isArray(paths)) return [];
    const items = await registerList(paths);
    await store();
    return items;
  });
  ipcMain.handle('library:captions', async (event, id, ext, pick) => {
    if (!requestFrom(event, main) || !['srt', 'lrc'].includes(ext) || !files.has(id)) return '';
    const file = files.get(id).path;
    const selected = pick ? await dialog.showOpenDialog(main, { properties: ['openFile'],
      filters: [{ name: ext.toUpperCase(), extensions: [ext] }] }) : null;
    if (selected?.canceled) return '';
    const target = selected?.filePaths[0] || file.slice(0, -path.extname(file).length) + '.' + ext;
    const stat = await fs.stat(target).catch(() => null);
    if (!stat?.isFile() || stat.size > 3_000_000) return '';
    const bytes = await fs.readFile(target);
    if (bytes[0] === 0xff && bytes[1] === 0xfe) return bytes.toString('utf16le', 2);
    try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
    catch { return new TextDecoder('windows-1252').decode(bytes); }
  });
  ipcMain.on('library:save', (event, value) => {
    if (!requestFrom(event, main) || !value || typeof value !== 'object') return;
    const pinned=drawerPinned();
    memory = value;
    if(pinned!==drawerPinned()&&floating?.isVisible()){
      floating.setAlwaysOnTop(drawerPinned());
      advanced?.place();
    }
    store();
  });
  ipcMain.on('window:mode', (event, kind, opacity) => {
    if (requestFrom(event, main) || requestFrom(event, floating)) changeWindow(kind, opacity).catch(console.error);
  });
  ipcMain.on('player:state', (event, value) => {
    if (!requestFrom(event, main)) return;
    lastState = value;
    advanced?.publish();
    if (floating?.isVisible()) floating.webContents.send('player:state', value);
    refreshPoster();
  });
  ipcMain.on('player:meter',(event,value)=>{if(requestFrom(event,main))forwardMeter(value,false);});
  ipcMain.on('player:command', (event, action, value) => {
    if (!requestFrom(event, floating) || !['toggle', 'next', 'previous', 'seek'].includes(action)) return;
    main.webContents.send('player:command', { action, value });
  });

}

app.whenReady().then(async () => {
  Menu.setApplicationMenu(null);
  if (process.platform === 'win32') app.setAppUserModelId('Rakoto.Player');
  session.defaultSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
  stateFile = path.join(app.getPath('userData'), 'library.json');
  await fs.mkdir(path.dirname(stateFile), { recursive: true });
  const saved = await fs.readFile(stateFile, 'utf8').then(JSON.parse).catch(() => ({}));
  memory = saved.memory || {};
  settings = saved.settings || {};
  await registerList(Array.isArray(saved.paths) ? saved.paths : []);
  protocol.handle('rakoto', async request => {
    const url = new URL(request.url);
    if (url.host !== 'app') return new Response(null, { status: 404 });
    const [kind, id] = url.pathname.slice(1).split('/');
    if (kind === 'media' && files.has(id)) {
      const file = files.get(id);
      return streamFile(request, file.path, file.mime);
    }
    if (kind === 'cover' && files.get(id)?.cover) {
      const cover = files.get(id).cover;
      return new Response(cover.data, { headers: { 'Content-Type': cover.format } });
    }
    const name = url.pathname === '/' ? 'index.html' : url.pathname.slice(1);
    if (!assets.has(name) && !/^art\/\d+\.svg$/.test(name)) return new Response(null, { status: 404 });
    const extension = path.extname(name);
    const mime = { '.js': 'application/javascript', '.css': 'text/css', '.html': 'text/html; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png' }[extension];
    return streamFile(request, path.join(ui, name), mime);
  });

  installIpc();
  main = makeWindow({ width: 1460, height: 920, minWidth: 1020, minHeight: 670, frame: false, backgroundColor: '#F6EEDC',
    webPreferences:{backgroundThrottling:false} });
  advanced=installAdvanced({
    ipcMain,Menu,dialog,app,makeWindow,makeVideoHost:options=>new BaseWindow({show:false,...options}),base,windows:()=>({main,floating,mode,pinned:drawerPinned()}),requestFrom,files,catalog,store,settings,
    command:(action,value)=>main.webContents.send('player:command',{action,value}),
    changeWindow,lastState:()=>lastState,quiet:verify,
    meter:(levels,id)=>forwardMeter(levels,true,id)
  });
  advanced.watchWindow(main);
  watchMotion(main);
  main.on('minimize',()=>{
    if(skipDrawerMinimize){skipDrawerMinimize=false;return;}
    if(!closing&&mode==='normal')changeWindow('drawer',lastState?.opacity).catch(console.error);
  });
  main.on('close', () => {
    closing=true;
    advanced?.savePosition();
    advanced?.shutdown();
    if(floating&&!floating.isDestroyed())floating.destroy();
  });
  main.on('closed', () => { if (!verify) app.quit(); });
  main.on('enter-full-screen', () => main.webContents.send('window:changed', 'fullscreen'));
  main.on('leave-full-screen', sendMode);
  await main.loadURL(base + '/' + (verify ? '?demo=1&verify=1' : demo ? '?demo=1' : ''));
  if (advancedVerify) await require('./tools/verify-advanced.cjs')({main,app,register,probe,advanced,changeWindow,current:()=>({mode,floating,lastState})});
  else if (verify) await require('./tools/verify.cjs')({ main, changeWindow, current: () => ({ mode, floating, lastState }),
    register, base, app, advanced });
  else await showStartup([...startup,...waiting]);
}).catch(error => { console.error(error); app.exit(1); });

let flushed = false;
app.on('before-quit', event => {
  closing = true;
  if (flushed) return;
  event.preventDefault();
  clearTimeout(moveTimer);
  advanced?.savePosition();
  if (floating && !floating.isDestroyed() && mode !== 'normal') rememberPosition(floating);
  writeChain.finally(() => {
    flushed = true;
    app.quit();
  });
});








