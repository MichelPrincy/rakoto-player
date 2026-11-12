const {app,BrowserWindow}=require('electron');
const fs=require('node:fs/promises'),path=require('node:path'),{createHash}=require('node:crypto');
const base=process.env.RAKOTO_SITE_URL||'http://127.0.0.1:4173/rakoto-player/';
const output=path.join(__dirname,'../build-output/campfire-audit');
app.disableHardwareAcceleration();app.setPath('userData',path.join(output,'profile'));
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
app.whenReady().then(async()=>{
 await fs.mkdir(output,{recursive:true});
 const win=new BrowserWindow({show:false,width:1440,height:1000,useContentSize:true,webPreferences:{sandbox:true,contextIsolation:true,nodeIntegration:false,offscreen:true,backgroundThrottling:false}});
 const checks=[],errors=[];
 win.webContents.debugger.attach('1.3');
 const run=code=>win.webContents.executeJavaScript(code,true);
 const check=(pass,name,detail)=>{checks.push({name,pass:!!pass,...(detail?{detail}:{})});if(!pass)throw Error(name)};
 win.webContents.on('console-message',(_event,details)=>{if(details.level==='error')errors.push(details.message)});
 const snapshot=async name=>fs.writeFile(path.join(output,name+'.png'),(await win.webContents.capturePage()).toPNG());
 try{
  await win.loadURL(base);await run('document.fonts.ready');
  await run(`localStorage.setItem('rakoto-motion','full')`);await win.reload();await wait(1500);
  check(await run(`document.querySelector('[data-camp-fire]').dataset.fire==='running'`),'Real WebGL fire renders',await run(`({state:document.querySelector('[data-camp-fire]').dataset.fire,fonts:document.fonts.status})`));
  const box=await run(`(()=>{const r=document.querySelector('[data-camp-fire]').getBoundingClientRect();return {x:Math.ceil(r.x),y:Math.ceil(r.y),width:Math.floor(r.width),height:Math.floor(r.height)}})()`);
  const first=await win.webContents.capturePage(box);await wait(330);const second=await win.webContents.capturePage(box);
  await fs.writeFile(path.join(output,'fire-frame-a.png'),first.toPNG());await fs.writeFile(path.join(output,'fire-frame-b.png'),second.toPNG());
  const hash=image=>createHash('sha256').update(image.toPNG()).digest('hex');
  check(hash(first)!==hash(second),'Flame pixels actually change over time');
  const character={x:1060,y:380,width:250,height:280},stillA=await win.webContents.capturePage(character);await wait(250);const stillB=await win.webContents.capturePage(character);
  check(hash(stillA)===hash(stillB),'Characters stay still while the flame moves');
  await snapshot('hero');
  await run(`scrollTo({top:document.querySelector('#accueil').offsetHeight+130,behavior:'instant'})`);await wait(300);
  check(await run(`document.querySelector('[data-camp-fire]').hidden&&document.querySelector('[data-camp-fire]').dataset.fire==='paused'`),'Fire stops outside the hero viewport');
  await run(`document.querySelector('#univers').scrollIntoView({behavior:'instant'})`);await wait(1200);await snapshot('universe');
  for(const name of ['audio','video','fonctionnalites','experience','plateformes','telecharger','questions']){
   await run(`document.getElementById('${name}').scrollIntoView({behavior:'instant'})`);await wait(1300);await snapshot(name);
  }
  await run(`scrollTo({top:0,behavior:'instant'});document.querySelector('[data-motion-toggle]').click()`);await wait(250);
  check(await run(`document.documentElement.dataset.effects==='lite'&&document.querySelector('[data-camp-fire]').hidden`),'Lightweight mode removes the animation');
  await win.webContents.debugger.sendCommand('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  await run(`document.querySelector('[data-motion-toggle]').click()`);
  check(await run(`document.querySelector('[data-camp-fire]').hidden`),'Reduced motion cannot be overridden by the effects button');
  await win.webContents.debugger.sendCommand('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
  // Simulate a device without WebGL; the original artwork and all links must survive.
  await win.webContents.debugger.sendCommand('Page.enable');
  await win.webContents.debugger.sendCommand('Page.addScriptToEvaluateOnNewDocument',{source:`window.__webglDisabled=true;const original=HTMLCanvasElement.prototype.getContext;HTMLCanvasElement.prototype.getContext=function(type,...args){return type==='webgl'?null:original.call(this,type,...args)}`});
  await run(`localStorage.setItem('rakoto-motion','full')`);await win.loadURL(base+'?fire-test=fallback');await run('document.fonts.ready');await wait(1200);
  check(await run(`window.__webglDisabled&&document.querySelector('[data-camp-fire]').hidden&&document.querySelector('[data-camp-image]').naturalWidth===992&&document.querySelector('[data-download="offline"]').href.startsWith('https:')`),'Static fallback works when WebGL is unavailable',await run(`({injected:window.__webglDisabled,hidden:document.querySelector('[data-camp-fire]').hidden,state:document.querySelector('[data-camp-fire]').dataset.fire,width:document.querySelector('[data-camp-image]').naturalWidth,href:document.querySelector('[data-download="offline"]').href})`));
  check(errors.length===0,'No console errors',errors);
 }catch(error){console.error(error);process.exitCode=1}
 finally{await fs.writeFile(path.join(output,base.startsWith('http://127.')?'report-local.json':'report-published.json'),JSON.stringify({base,checks,errors},null,2));console.log(JSON.stringify({checks,errors}));win.destroy();app.exit(process.exitCode||0)}
});
