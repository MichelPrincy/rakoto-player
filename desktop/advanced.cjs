const { NativePlayer } = require('./native-player.cjs');
const { hideVideo,placeVideo } = require('./win-video.cjs');
const { buildMenu } = require('./media-menu.cjs');
const path = require('node:path');
const fs = require('node:fs/promises');
const { randomUUID } = require('node:crypto');
const subtitleExtensions=['srt','ass','ssa','vtt','sub','idx'];
const number = value => typeof value==='number'&&Number.isFinite(value);

function installAdvanced({ ipcMain, Menu, dialog, app, makeWindow, makeVideoHost, base, windows, requestFrom, files, catalog,
  store, settings, command, changeWindow, lastState, quiet, meter,ensureAnalyzed,videoSession=()=>null }) {
  let host, overlay, details, player, opening, rect, updateTimer, positionTimer, popup;
  let type, generation=0;
  let lastUpdate=null, videoHandle=0, shuttingDown=false;
  const pending = new Map();
  let releaseTimer;
  const mediaSettings=()=>{
    settings.media ||= {};
    settings.media.positions ||= {};
    settings.media.defaults ||= {};
    return settings.media;
  };
  const valid=(event,extra=[])=>[windows().main,windows().floating,overlay,...extra].some(win=>requestFrom(event,win));
  const state=()=>player?.snapshot('state');
  const send=(window,channel,value)=>{
    if(window&&!window.isDestroyed()) window.webContents.send(channel,value);
  };
  const publish=()=>{
    const packet={state:lastState(),native:lastUpdate,fullscreen:windows().main?.isFullScreen() && windows().mode==='normal',mode:windows().mode,
      wheel:mediaSettings().wheel!==false,chapters:files.get(lastState()?.track?.id)?.probe?.chapters||[]};
    send(overlay,'media:overlay',packet);
  };
  function flush() {
    clearTimeout(updateTimer);
    updateTimer=null;
    if(!lastUpdate) return;
    send(windows().main,'media:update',lastUpdate);
    publish();
  }
  function savePosition() {
    const current=lastUpdate;
    if(!current?.id || !current.loaded || files.get(current.id)?.persistent!==true) return;
    const time=current.properties['time-pos'], length=current.properties.duration;
    if(!number(time)||time<2||!number(length)||time>length-2) return;
    const positions=mediaSettings().positions;
    positions[current.id]=time;
    for(const id of Object.keys(positions).slice(0,Math.max(0,Object.keys(positions).length-400)))delete positions[id];
    store();
  }
  function onUpdate(value) {
    const nextHandle=Number(value.properties['window-id'])||0;
    const changed=nextHandle!==videoHandle;
    if(changed){hideVideo(videoHandle,player?.child?.pid);videoHandle=nextHandle;}
    lastUpdate=value;
    if(value.event==='eof') {
      if(files.get(value.id)?.persistent===true){delete mediaSettings().positions[value.id];store();}
    }
    if(changed || value.event==='loaded' || value.event==='stopped' || value.event==='error' || value.event==='eof') {
      flush();
      place();
    } else if(!updateTimer) updateTimer=setTimeout(flush,70);
  }

  function place() {
    if(!host||host.isDestroyed()||!overlay||overlay.isDestroyed()) return;
    const {main,floating,mode,pinned}=windows();
    const inFloat=mode==='video';
    const owner=inFloat?floating:main;
    const show=type==='video'&&lastUpdate?.loaded&&owner?.isVisible()&&!owner.isMinimized()&&
      (inFloat || (mode==='normal' && rect?.width>30 && rect?.height>30));
    if(!show){hideVideo(videoHandle,player?.child?.pid);overlay.hide();host.hide();publish();return;}
    if(host.getParentWindow()!==owner) host.setParentWindow(owner);
    const parent=owner.getContentBounds();
    const local=inFloat?{x:5,y:5,width:parent.width-10,height:parent.height-10}:main.isFullScreen()
      ?{x:0,y:0,width:parent.width,height:parent.height}:rect;
    const x=Math.min(Math.max(0,local.x),parent.width-1), y=Math.min(Math.max(0,local.y),parent.height-1);
    const bounds={ x:parent.x+x,y:parent.y+y,width:Math.min(local.width,parent.width-x),height:Math.min(local.height,parent.height-y)};
    host.setBounds(bounds);
    overlay.setBounds(bounds);
    if(!host.isVisible())host.showInactive();
    if(!overlay.isVisible())overlay.showInactive();
    const topmost=inFloat&&pinned;
    if(overlay.isAlwaysOnTop()!==topmost)overlay.setAlwaysOnTop(topmost);
    placeVideo(videoHandle,player?.child?.pid,host,overlay,topmost);
    publish();
  }

  async function prepare() {
    if(player) return;
    if(opening) return opening;
    opening=(async()=>{
      host=makeVideoHost({parent:windows().main,frame:false,focusable:false,resizable:false,skipTaskbar:true,
        width:200,height:100,backgroundColor:'#000000'});
      overlay=makeWindow({parent:host,frame:false,transparent:true,resizable:false,skipTaskbar:true,
        width:200,height:100,backgroundColor:'#00000000',hasShadow:false,webPreferences:{backgroundThrottling:!quiet,offscreen:Boolean(quiet)}});
      await overlay.loadURL(base+'/overlay.html');
      send(overlay,'video:session',videoSession());
      player=new NativePlayer({muted:quiet,decoder:mediaSettings().decoder});

      player.on('update',onUpdate);
      player.on('meter',levels=>meter(levels,player.id));
    })().finally(()=>opening=null);
    return opening;
  }

  async function open(id,options={}) {
    const token=++generation;
    clearTimeout(releaseTimer);
    if(ensureAnalyzed)await ensureAnalyzed(id);
    if(token!==generation)return false;
    const file=files.get(id), item=catalog.get(id);
    if(!file||!item||!file.probe) throw new Error('Fichier non reconnu par l’analyse média.');
    savePosition();
    await prepare();
    if(token!==generation)return false;
    type=item.type;

    const saved=mediaSettings();
    const request={time:options.time || (file.persistent===true&&options.resume && saved.positions[id]) || 0,paused:options.paused,
      aid:saved.defaults[id],sid:'auto'};
    // Serialize loads on the same mpv core without letting a delayed command supersede a new selection.
    const previous=pending.get('open')||Promise.resolve();
    const task=previous.catch(()=>{}).then(async()=>{
      if(token!==generation)return false;
      await player.open(id,file.path,request);
      if(token!==generation)return false;
      await sync(options);
      return true;
    });
    pending.set('open',task);
    return task;
  }

  async function sync(options) {
    if(shuttingDown||!player?.socket)return;
    const entries=[
      ['volume',Math.round(Math.min(1,Math.max(0,Number(options.volume)||0))*100)],
      ['mute',quiet||Boolean(options.muted)],['speed',Math.min(2,Math.max(.25,Number(options.speed)||1))]
    ];
    try{
      await Promise.all(entries.map(([key,value])=>player.set(key,value)));
      await player.equalizer({...options,normalize:mediaSettings().normalize===true});
    }catch(error){if(!shuttingDown)throw error;}
  }

  async function stop() {
    generation++;
    savePosition();
    await pending.get('open')?.catch(()=>{});
    if(player?.socket && (player.loaded || type)) await player.transport('stop');
    type=null;
    place();
    const stoppedGeneration=generation;
    clearTimeout(releaseTimer);
    releaseTimer=setTimeout(()=>{
      if(shuttingDown||generation!==stoppedGeneration||player?.loaded)return;
      hideVideo(videoHandle,player?.child?.pid);videoHandle=0;
      player?.close();player=null;lastUpdate=null;
      if(overlay&&!overlay.isDestroyed())overlay.destroy();overlay=null;
      if(host&&!host.isDestroyed())host.destroy();host=null;
    },1500);
  }

  async function subtitle(owner) {
    if(!player?.loaded) return;
    const picked=await dialog.showOpenDialog(owner,{title:'Charger des sous-titres',properties:['openFile'],
      filters:[{name:'Sous-titres',extensions:subtitleExtensions}]});
    if(picked.canceled) return;
    const target=picked.filePaths[0];
    if(!subtitleExtensions.includes(path.extname(target).slice(1).toLowerCase())) throw new Error('Extension de sous-titres inconnue.');
    await player.addSubtitle(target);
  }

  async function poster(id){
    if(!player?.loaded||player.id!==id||type!=='video')return null;
    const file=path.join(app.getPath('temp'),'rakoto-poster-'+randomUUID()+'.jpg');
    try{
      await player.send(['screenshot-to-file',file,'video']);
      const stat=await fs.stat(file);
      if(stat.size>450000||player.id!==id)return null;
      return 'data:image/jpeg;base64,'+(await fs.readFile(file)).toString('base64');
    }catch{return null;}
    finally{await fs.unlink(file).catch(()=>{});}
  }

  async function capture(owner) {
    if(!player?.loaded||type!=='video')return;
    const result=await dialog.showSaveDialog(owner,{title:'Enregistrer une image',defaultPath:'RAKOTO-capture.png',
      filters:[{name:'Image PNG',extensions:['png']}]});
    if(!result.canceled && result.filePath) await player.screenshot(result.filePath);
  }

  async function dialogWindow(owner,kind) {
    const current=lastState();
    const report=files.get(current?.track?.id)?.probe;
    if(kind==='info'&&!report)return;
    if(details&&!details.isDestroyed())details.destroy();
    details=makeWindow({parent:owner,modal:true,width:kind==='info'?690:390,height:kind==='info'?660:240,
      minWidth:380,minHeight:230,resizable:kind==='info',title:kind==='info'?'Informations média':'Format d’image personnalisé',
      backgroundColor:current?.theme==='dark'?'#1C1410':'#F6EEDC'});
    const dialogRef=details;
    details.on('closed',()=>{if(details===dialogRef)details=null;});
    await details.loadURL(base+'/media-dialog.html');
    send(details,'media:details',{kind,report,theme:current?.theme||'light',
      value:state()?.properties['video-aspect-override'],hardware:state()?.properties['hwdec-current'],
      output:state()?.properties['current-vo']});
    details.show();
  }

  const menuCommand=(owner,kind)=>{
    const current=lastState();
    if(!current?.track)return Promise.resolve();
    const report=files.get(current.track.id)?.probe;
    const snapshot=state();
    const props=snapshot?.properties||{};
    const menu=buildMenu({
      kind, current, report, native: snapshot?.loaded && snapshot.id===current.track.id,
      props, flips:snapshot?.flips||{}, normalize:mediaSettings().normalize===true,
      defaultAudio:mediaSettings().defaults[current.track.id],
      wheel:mediaSettings().wheel!==false, fullscreen:windows().main?.isFullScreen(),
      action:action=>command(action), value:(action,value)=>command(action,value),
      set:(key,value)=>player.set(key,value),
      delay:(key,delta)=>player.set(key,Math.max(-10,Math.min(10,+(Number(props[key]||0)+delta).toFixed(2)))),
      flip:axis=>player.flip(axis),
      decoder:mediaSettings().decoder||'auto-copy',
      setDecoder:async decoder=>{
        await player.set('hwdec',decoder);
        mediaSettings().decoder=decoder;
        store();
      },
      normalizeAudio:()=>{mediaSettings().normalize=!mediaSettings().normalize;store();command('sync');},
      defaultTrack:()=>{
        if(Number.isInteger(props.aid))mediaSettings().defaults[current.track.id]=props.aid;
        else delete mediaSettings().defaults[current.track.id];
        store();
      },
      clearDefault:()=>{delete mediaSettings().defaults[current.track.id];store();},
      setWheel:()=>{mediaSettings().wheel=mediaSettings().wheel===false;store();publish();},
      subtitle:()=>subtitle(owner),capture:()=>capture(owner),info:()=>dialogWindow(owner,'info'),
      aspect:()=>dialogWindow(owner,'aspect'),full:()=>changeWindow('fullscreen'),
      float:()=>changeWindow('video',1),normal:()=>changeWindow('normal'),
      step:direction=>player.transport(direction), chapter:time=>command('seek',time),
      error:error=>send(windows().main,'media:notice',error.message)
    });
    return new Promise(resolve=>{
      const nativeMenu=Menu.buildFromTemplate(menu);
      popup={menu:nativeMenu,owner};
      send(overlay,'media:menu-lock',true);
      nativeMenu.popup({window:owner,callback:()=>{
        if(popup?.menu===nativeMenu){
          popup=null;
          send(overlay,'media:menu-lock',false);
        }
        resolve();
      }});
    });
  };

  ipcMain.handle('media:open',(event,id,opts)=>requestFrom(event,windows().main)?open(id,opts):false);
  ipcMain.handle('media:sync',(event,opts)=>requestFrom(event,windows().main)&&opts?sync(opts):false);
  ipcMain.handle('media:release',event=>requestFrom(event,windows().main)?stop():false);
  ipcMain.handle('media:transport',(event,action,value)=>{
    if(!requestFrom(event,windows().main)||!player)return false;
    if(action==='stop')return stop();
    return player.transport(action,value);
  });
  ipcMain.handle('media:select',(event,kind,id)=>valid(event)&&player?.loaded&&['aid','sid'].includes(kind)?player.set(kind,id):false);
  ipcMain.handle('media:menu',(event,kind)=>valid(event)?menuCommand([overlay,windows().floating,windows().main]
    .find(win=>requestFrom(event,win)),kind):false);
  ipcMain.handle('media:command',(event,action,value)=>{
    if(!valid(event)||!['toggle','next','previous','seek','volume','speed','fullscreen','normal','video','mini','library','queue','playVideo','mute'].includes(action))return false;
    if(action==='playVideo'&&(!videoSession()?.items.some(item=>item.id===value)||typeof value!=='string'))return false;
    if(['fullscreen','normal','video','mini'].includes(action))return changeWindow(action,1);
    if((action==='seek'||action==='volume'||action==='speed')&&!number(value))return false;
    return command(action,value);
  });
  ipcMain.handle('media:external',event=>valid(event)?subtitle([overlay,windows().floating,windows().main].find(win=>requestFrom(event,win))):false);
  ipcMain.handle('media:info',event=>valid(event)?dialogWindow([overlay,windows().floating,windows().main].find(win=>requestFrom(event,win)),'info'):false);
  ipcMain.handle('media:aspect',(event,value)=>{
    if(!requestFrom(event,details)||!player?.loaded||!number(value)||value<.5||value>4)return false;
    return player.set('video-aspect-override',value).then(()=>details?.close());
  });
  ipcMain.on('media:drag',(event,dx,dy)=>{
    const {mode,floating}=windows();
    if(!requestFrom(event,overlay)||mode!=='video'||!floating?.isVisible()||
      !number(dx)||!number(dy)||Math.abs(dx)>1000||Math.abs(dy)>1000)return;
    const [x,y]=floating.getPosition();
    floating.setPosition(Math.round(x+dx),Math.round(y+dy));
  });
  ipcMain.on('media:stage',(event,value)=>{
    if(!requestFrom(event,windows().main))return;
    rect=value&&['x','y','width','height'].every(key=>number(value[key]))?
      Object.fromEntries(['x','y','width','height'].map(key=>[key,Math.round(value[key])])):null;
    place();
  });
  const watchWindow=win=>{
    for(const event of ['move','resize','minimize','restore','show','hide','enter-full-screen','leave-full-screen'])
      win.on(event,place);
  };
  function detachWindow(window){
    const main=windows().main;
    if(host&&!host.isDestroyed()&&main&&!main.isDestroyed()&&host.getParentWindow()===window)
      host.setParentWindow(main);
  }
  positionTimer=setInterval(savePosition,12000);
  return {watchWindow,detachWindow,place,publish,poster,savePosition,popup:menuCommand,shutdown(){
    shuttingDown=true;
    clearTimeout(updateTimer);clearInterval(positionTimer);clearTimeout(releaseTimer);
    if(popup?.owner&&!popup.owner.isDestroyed())popup.menu.closePopup(popup.owner);
    player?.close();
    if(overlay&&!overlay.isDestroyed())overlay.destroy();
    if(host&&!host.isDestroyed())host.destroy();
  }, get overlay(){return overlay;}, get host(){return host;}, get player(){return player;}, get details(){return details;},
    verifySubtitle:file=>player.addSubtitle(file), verifyScreenshot:file=>player.screenshot(file)};
}

module.exports={installAdvanced};










