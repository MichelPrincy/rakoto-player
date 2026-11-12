const { EventEmitter } = require('node:events');
const { spawn } = require('node:child_process');
const { createConnection } = require('node:net');
const { randomUUID } = require('node:crypto');
const { createInterface } = require('node:readline');
const { existsSync } = require('node:fs');
const { toolPath } = require('./media-probe.cjs');

const watched = ['pause','time-pos','duration','track-list','aid','sid','audio-delay','sub-delay','speed','hwdec-current',
  'video-params','audio-params','current-vo','window-id','video-aspect-override','video-zoom','video-rotate','deinterlace','audio-channels',
  'volume','mute','sub-font','sub-font-size','sub-color','sub-pos','sub-ass-override','sub-text'];
const ranges = {
  volume:[0,100],speed:[.25,2],'audio-delay':[-10,10],'sub-delay':[-10,10],'sub-font-size':[12,90],
  'sub-pos':[0,100],'video-zoom':[-1,1],'video-rotate':[0,270],'video-aspect-override':[-1,4]
};
const spectrumFrequencies=[90,310,1100,3400,7900];
function spectrumGraph(){
  const feeds=spectrumFrequencies.map((_,i)=>'[meter'+i+']').join('');
  const analysis=spectrumFrequencies.map((frequency,i)=>
    '[meter'+i+']bandpass=f='+frequency+':width_type=o:width=2,'+
    'astats=metadata=1:reset=1:measure_perchannel=none:measure_overall=RMS_level,'+
    'ametadata=mode=add:key=rakoto.band:value='+i+',ametadata=mode=print:file=-:direct=1,anullsink').join(';');
  return 'asplit=2[out][tap];[tap]aresample=24000,asetnsamples=n=2048:p=0,asplit=5'+feeds+';'+analysis;
}
const fonts = ['Segoe UI','Arial','Verdana','Tahoma','Consolas','Georgia'];
const allowed = {
  hwdec:v=>['auto-copy','no'].includes(v),
  pause:v=>typeof v==='boolean',mute:v=>typeof v==='boolean',deinterlace:v=>typeof v==='boolean',
  aid:v=>v==='no'||v==='auto'||(Number.isInteger(v)&&v>0),
  sid:v=>v==='no'||v==='auto'||(Number.isInteger(v)&&v>0),
  'audio-channels':v=>['auto','stereo','mono','5.1','7.1'].includes(v),
  'sub-font':v=>fonts.includes(v),'sub-color':v=>typeof v==='string'&&/^#[0-9a-f]{6}$/i.test(v),
  'sub-ass-override':v=>['no','force'].includes(v),
  ...Object.fromEntries(Object.entries(ranges).map(([key,[min,max]])=>[key,v=>typeof v==='number'&&Number.isFinite(v)&&v>=min&&v<=max]))
};

class NativePlayer extends EventEmitter {
  constructor({ headless=false, muted=false, decoder }={}) {
    super();

    this.headless=headless;
    this.muted=muted;
    this.decoder=allowed.hwdec(decoder)?decoder:'auto-copy';
    this.pending=new Map();
    this.filterWork=Promise.resolve();
    this.nextId=1;
    this.state={};
    this.id=null;
    this.serial=0;
    this.loaded=false;
    this.flips={h:false,v:false};
    this.filters='';
    this.meter=Array(5).fill(0);this.meterDb=null;this.meterOn=false;
  }

  async ready() {
    if(this.socket && !this.socket.destroyed) return;
    if(!this.starting) this.starting=this.start().finally(()=>{this.starting=null;});
    return this.starting;
  }

  async start() {
    this.filters='';
    this.meter=Array(5).fill(0);this.meterDb=null;this.meterOn=false;
    this.flips={h:false,v:false};
    const executable=toolPath('mpv');
    if(!existsSync(executable)) throw new Error('Le moteur mpv est absent du dossier native.');
    const pipe='\\\\.\\pipe\\rakoto-'+randomUUID();
    const args=['--no-config','--load-scripts=no','--ytdl=no','--access-references=no','--load-unsafe-playlists=no',
      '--demuxer-max-bytes=32MiB','--demuxer-max-back-bytes=4MiB','--demuxer-readahead-secs=1',
      '--idle=yes','--terminal=no','--input-default-bindings=no','--input-vo-keyboard=no',
      '--osc=no','--osd-level=0','--sub-auto=exact','--audio-file-auto=no','--cursor-autohide=no',
      '--demuxer-lavf-o=protocol_whitelist=file','--input-ipc-server='+pipe,
      ...(this.headless?['--vo=null','--ao=null']:['--vo=gpu','--d3d11-flip=no','--hwdec='+this.decoder,'--border=no',
        '--keepaspect-window=no','--auto-window-resize=no','--window-minimized=yes'])];
    if(this.muted)args.push('--mute=yes');
    if(process.env.RAKOTO_MPV_LOG)args.push('--log-file='+process.env.RAKOTO_MPV_LOG);
    const child=this.child=spawn(executable,args,{windowsHide:true,stdio:['ignore','pipe','ignore']});
    this.meterReader=createInterface({input:child.stdout,crlfDelay:Infinity});
    this.meterReader.on('line',line=>this.receiveMeter(line));
    let failed;
    child.on('error',error=>failed=error);
    child.on('exit',()=>{if(this.child===child)this.disconnect();});
    for(let attempt=0;attempt<100;attempt++){
      if(failed||child.exitCode!==null) throw failed || new Error('mpv s’est arrêté au démarrage.');
      try {
        this.socket=await new Promise((resolve,reject)=>{
          const socket=createConnection(pipe);
          socket.once('error',reject);
          socket.once('connect',()=>{
            socket.removeListener('error',reject);
            socket.on('error',()=>this.disconnect());
            socket.on('close',()=>{if(this.socket===socket)this.disconnect();});
            resolve(socket);
          });
        });
        break;
      } catch {
        await new Promise(resolve=>setTimeout(resolve,50));
      }
    }
    if(!this.socket) {child.kill();throw new Error('Le canal local mpv ne répond pas.');}
    this.reader=createInterface({input:this.socket,crlfDelay:Infinity});
    this.reader.on('line',line=>this.receive(line));
    await Promise.all(watched.map((name,index)=>this.send(['observe_property',index+1,name])));
  }

  disconnect() {
    this.reader?.close();
    this.meterReader?.close();
    this.socket?.destroy();
    this.socket=null;
    this.loadWait?.reject(new Error('Connexion mpv fermée.'));this.loadWait=null;
    for(const request of this.pending.values()) request.reject(new Error('Connexion mpv fermée.'));
    this.pending.clear();
    if(this.loaded){this.loaded=false;this.emit('update',this.snapshot('error'));}
  }

  close() {
    this.loaded=false;
    this.disconnect();
    if(this.child && this.child.exitCode===null) this.child.kill();
    this.child=null;
  }

  send(command) {
    if(!this.socket || this.socket.destroyed) return Promise.reject(new Error('Le moteur mpv est indisponible.'));
    const id=this.nextId++;
    // Decoder/output reconfiguration can take longer on integrated GPUs or
    // while the machine is busy. Keep transport commands responsive separately.
    const reconfigure=command[0]==='loadfile'||(command[0]==='set_property'&&
      ['hwdec','audio-channels','deinterlace'].includes(command[1]));
    return new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>{
        this.pending.delete(id);
        reject(new Error('mpv n’a pas répondu à '+command[0]+(command[0]==='set_property'?' ('+command[1]+')':'')+'.'));
      },reconfigure?12000:4000);
      const done=fn=>value=>{clearTimeout(timer);fn(value);};
      this.pending.set(id,{resolve:done(resolve),reject:done(reject)});
      this.socket.write(JSON.stringify({command,request_id:id})+'\n');
    });
  }

  snapshot(event) {
    return {id:this.id,serial:this.serial,event,loaded:this.loaded,flips:{...this.flips},
      normalized:this.filters.includes('dynaudnorm'),properties:{...this.state}};
  }

  receive(line) {
    let message;
    try {message=JSON.parse(line);} catch {return;}
    if(message.request_id){
      const request=this.pending.get(message.request_id);
      this.pending.delete(message.request_id);
      if(message.error==='success') request?.resolve(message.data);
      else request?.reject(new Error('mpv : '+message.error));
    }
    if(message.event==='start-file'&&this.loadWait)this.loadWait.started=true;
    if(message.event==='property-change') {
      this.state[message.name]=message.data??null;
      if(this.loaded) this.emit('update',this.snapshot('state'));
    }
    if(message.event==='file-loaded') {
      if(this.loadWait){this.loadWait.resolve();this.loadWait=null}
      else{this.loaded=true;this.emit('update',this.snapshot('loaded'))}
    }
    if(message.event==='end-file' && ['eof','error'].includes(message.reason)) {
      if(this.loadWait&&!this.loadWait.started)return;
      this.loaded=false;
      this.loadWait?.reject(new Error('mpv ne peut pas décoder ce fichier.'));this.loadWait=null;
      this.emit('update',this.snapshot(message.reason));
    }
  }

  async open(id,file,options) {
    await this.ready();
    this.serial++;
    this.id=id;
    this.loaded=false;
    const windowId=this.state['window-id'];
    this.state=windowId?{'window-id':windowId}:{};
    let timer;
    const loaded=new Promise((resolve,reject)=>{
      const finish=fn=>value=>{clearTimeout(timer);fn(value)};
      this.loadWait={started:false,resolve:finish(resolve),reject:finish(reject)};
      timer=setTimeout(()=>{this.loadWait=null;reject(new Error('Chargement vidéo trop long. Réessayez ou choisissez le décodage logiciel.'))},30000);
    });
    // Install a rejection handler immediately, even if loadfile itself rejects first.
    loaded.catch(()=>{});
    try{
      await this.send(['set_property','pause',true]);
      await this.send(['loadfile',file,'replace',-1,{
        start:String(Math.max(0,Math.min(Number(options.time)||0,1e8))),
        aid:allowed.aid(options.aid)?String(options.aid):'auto',
        sid:allowed.sid(options.sid)?String(options.sid):'auto'
      }]);
      await loaded;
      // Observed properties only emit when they change. A reused VO can retain the
      // same handle (and two films the same duration), so refresh the new snapshot.
      await Promise.all(watched.map(async name=>{
        const value=await this.send(['get_property',name]).catch(()=>null);
        if(value!==null||name!=='window-id')this.state[name]=value;
      }));
      this.loaded=true;this.emit('update',this.snapshot('loaded'));
      await this.send(['set_property','pause',Boolean(options.paused)]);
    }catch(error){this.loadWait?.reject(error);this.loadWait=null;throw error}
    finally{clearTimeout(timer)}
    return this.serial;
  }

  async set(name,value) {
    if(!Object.hasOwn(allowed,name)||!allowed[name](value)) throw new Error('Réglage non valide.');
    const result=await this.send(['set_property',name,value]);
    if(name==='hwdec')this.decoder=value;
    return result;
  }

  async transport(action,value) {
    if(action==='toggle') return this.send(['cycle','pause']);
    if(action==='stop') {
      this.loaded=false;
      await this.send(['stop']);
      this.emit('update',this.snapshot('stopped'));
      return;
    }
    if(action==='seek' && Number.isFinite(value) && value>=0 && value<1e8) return this.send(['seek',value,'absolute+exact']);
    if(action==='step') return this.send(['frame-step']);
    if(action==='backstep') return this.send(['frame-back-step']);
    throw new Error('Commande de lecture non valide.');
  }

  async flip(axis) {
    if(!['h','v'].includes(axis)) throw new Error('Axe non valide.');
    const label='@rakoto_'+axis;
    if(this.flips[axis]) await this.send(['vf','remove',label]);
    else await this.send(['vf','add',label+':'+axis+'flip']);
    this.flips[axis]=!this.flips[axis];
    this.emit('update',this.snapshot('state'));
  }

  receiveMeter(line){
    if(!this.meterOn)return;
    if(line.startsWith('lavfi.astats.Overall.RMS_level=')){
      const db=Number(line.slice(line.indexOf('=')+1));
      this.meterDb=Number.isFinite(db)?Math.max(0,Math.min(1,(db+55)/49)):0;
    }else if(line.startsWith('rakoto.band=')){
      const band=Number(line.slice(12));
      if(Number.isInteger(band)&&band>=0&&band<5&&this.meterDb!==null){
        this.meter[band]=this.meterDb;
        this.meterDb=null;
        if(band===4)this.emit('meter',[...this.meter]);
      }
    }
  }

  async equalizer(values) {
    const clamp=(value,min,max)=>Math.max(min,Math.min(max,Number(value)||0));
    const freqs=[31,62,125,250,500,1000,2000,4000,8000,16000];
    const filters=freqs.flatMap((f,index)=>{
      const gain=values.enabled?clamp(values.bands?.[index],-12,12):0;
      return gain ? ['equalizer=f='+f+':width_type=q:width=1.1:g='+gain] : [];
    });
    const bass=clamp(values.bass,0,10);
    if(bass) filters.push('bass=f=100:g='+bass);
    if(values.normalize===true) filters.push('dynaudnorm');
    const meter=values.meter===true;
    const chain=filters.length?['lavfi=['+filters.join(',')+']']:[];
    if(meter)chain.push('lavfi=['+spectrumGraph()+']');
    const next=chain.join(',');
    const apply=this.filterWork.catch(()=>{}).then(async()=>{
      if(next===this.filters)return;
      await this.send(['af','set',next]);
      this.filters=next;
      this.meterOn=meter;
      this.meter.fill(0);this.meterDb=null;
      this.emit('update',this.snapshot('state'));
    });
    this.filterWork=apply;
    return apply;
  }

  addSubtitle(file) { return this.send(['sub-add',file,'select']); }
  screenshot(file) { return this.send(['screenshot-to-file',file,'subtitles']); }
}

module.exports={NativePlayer,fonts};









