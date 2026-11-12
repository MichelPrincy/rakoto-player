const {app,BrowserWindow}=require('electron');
const fs=require('node:fs/promises');const path=require('node:path');
const base=process.env.RAKOTO_SITE_URL||'http://127.0.0.1:4173/rakoto-player/';
const output=path.join(__dirname,'../build-output/browser-audit');
app.disableHardwareAcceleration();app.setPath('userData',path.join(output,'profile'));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
app.whenReady().then(async()=>{
 await fs.mkdir(output,{recursive:true});const errors=[],checks=[];
 const win=new BrowserWindow({show:false,width:1440,height:1000,useContentSize:true,webPreferences:{sandbox:true,contextIsolation:true,nodeIntegration:false,backgroundThrottling:false,offscreen:true}});
 win.webContents.debugger.attach('1.3');
 win.webContents.on('console-message',(_event,details)=>{if(details.level==='error')errors.push(details.message)});
 win.webContents.session.webRequest.onErrorOccurred(d=>errors.push(d.url+' '+d.error));
 const run=code=>Promise.race([win.webContents.executeJavaScript(code,true),new Promise((_,reject)=>setTimeout(()=>reject(new Error('Browser interaction timed out')),12000))]);
 win.webContents.on('render-process-gone',(_event,details)=>errors.push('Renderer '+details.reason));
 const check=(condition,name,detail)=>{checks.push({name,pass:!!condition,...(detail?{detail}: {})});if(!condition)throw new Error(name+': '+JSON.stringify(detail||''))};
 const snapshot=async(name)=>{const image=await win.webContents.capturePage();await fs.writeFile(path.join(output,name+'.png'),image.toPNG())};
 try{
  await win.webContents.session.clearCache();await win.webContents.session.clearStorageData();
  await win.loadURL(base);await run("document.fonts.ready");
  await wait(1500);
  check(await run('document.documentElement.lang==="fr"&&!document.documentElement.classList.contains("sound-active")'),'French first load and silent audio');
  check(await run('document.querySelectorAll("h1").length===1&&document.querySelector("script[data-software]")!==null'),'One H1 and SoftwareApplication metadata');
  check(await run(`Array.from(document.querySelectorAll('[data-download]')).every(a=>a.href.startsWith('https://rakoto-player.exquisitecurl1.chatgpt.site/downloads/')&&!a.hasAttribute('aria-disabled'))`),'Official Windows and Android downloads active');
  check(await run(`!Array.from(document.links).some(a=>/apps\.apple\.com/.test(a.href))`),'No invented App Store download');
  await snapshot('desktop');
  for(const width of [1440,1024,768,390,360]){
   await win.webContents.debugger.sendCommand('Emulation.setDeviceMetricsOverride',{width,height:900,deviceScaleFactor:1,mobile:width<600});await wait(160);
   for(let attempt=0;attempt<40;attempt++){if(await run(`Array.from(document.images).filter(i=>i.getAttribute('src')&&i.getAttribute('loading')!=='lazy').every(i=>i.complete&&i.naturalWidth)`))break;await wait(100)}
   const layout=await run(`({width:innerWidth,scroll:document.documentElement.scrollWidth,missing:Array.from(document.images).filter(i=>i.getAttribute('src')&&i.getAttribute('loading')!=='lazy'&&(!i.complete||!i.naturalWidth)).map(i=>i.src),overflow:Array.from(document.querySelectorAll('body *')).flatMap(e=>{const r=e.getBoundingClientRect();return r.right>innerWidth+1&&r.width>0?[{tag:e.tagName,name:e.className?.baseVal??e.className,right:Math.round(r.right),width:Math.round(r.width)}]:[]}).slice(-15)})`);
   check(layout.scroll<=layout.width+1&&Math.abs(layout.width-width)<=1,'No horizontal page overflow at '+width,layout);check(!layout.missing.length,'Hero image loads at '+width,layout.missing);
  }
  await win.webContents.debugger.sendCommand('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await run('scrollTo(0,0)');await wait(150);await snapshot('mobile');
  await run(`document.querySelector('[data-mobile-toggle]').click()`);check(await run(`document.querySelector('#navigation').classList.contains('open')`),'Mobile menu opens');
  await run(`document.querySelector('#navigation a[href="#fonctionnalites"]').click()`);await wait(200);check(await run(`!document.querySelector('#navigation').classList.contains('open')`),'Anchor closes mobile menu');
  await run(`document.querySelector('[data-language="mg"]').click()`);check(await run(`document.documentElement.lang==='mg'&&document.querySelector('h1').textContent.includes('Ny player-nao')&&document.title.includes('maimaim-poana')`),'Malagasy title, hero and metadata');
  check(await run(`localStorage.getItem('rakoto-language')==='mg'&&location.search==='?lang=mg'`),'Language choice persists and is shareable');
  check(await run(`Array.from(document.querySelectorAll('[data-i18n]')).every(e=>!e.textContent.includes('undefined')&&!e.textContent.includes('{{'))`),'No missing translated copy');
  await run(`scrollTo(0,0)`);await wait(250);await snapshot('mobile-malagasy');
  await win.loadURL(base);await run("document.fonts.ready");await wait(300);check(await run(`document.documentElement.lang==='mg'`),'Malagasy survives reload without query');
  await run(`document.querySelector('[data-language="fr"]').click();document.querySelector('[data-motion-toggle]').click()`);check(await run(`document.documentElement.dataset.effects==='lite'`),'Manual lightweight mode');
  await win.webContents.debugger.sendCommand('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});await wait(100);
  check(await run(`document.documentElement.dataset.effects==='lite'&&getComputedStyle(document.documentElement).scrollBehavior==='auto'`),'Reduced motion preference respected');
  await win.webContents.debugger.sendCommand('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
  await run(`document.querySelector('[data-capture="windows"]').click()`);check(await run(`document.querySelector('#capture-dialog').open`),'Real screenshot opens in accessible dialog');
  await run(`document.querySelector('[data-close-capture]').click()`);check(await run(`!document.querySelector('#capture-dialog').open`),'Screenshot close control');
  await run(`document.querySelector('.faq-list summary').click()`);check(await run(`document.querySelector('.faq-list details').open`),'FAQ accordion');
  await run(`document.querySelector('[data-android-variant]').value='arm64-v8a';document.querySelector('[data-android-variant]').dispatchEvent(new Event('change'))`);check(await run(`document.querySelector('[data-download="android"]').href.endsWith('-arm64-v8a.apk')`),'Real Android ABI selection');
  await run(`if(document.documentElement.dataset.effects==='lite')document.querySelector('[data-motion-toggle]').click();document.querySelector('#audio').scrollIntoView({behavior:'instant'});document.querySelector('[data-sound-toggle]').click()`);await wait(600);
  check(await run(`document.querySelector('[data-sound-toggle]').getAttribute('aria-pressed')==='true'&&document.querySelector('[data-visualizer-label]').textContent.includes('Analyse en direct')`),'Explicit sound activation and real analyser label');
  const signalBefore=await run(`document.querySelector('#spectrum').toDataURL()`);await wait(250);
  check(signalBefore!==await run(`document.querySelector('#spectrum').toDataURL()`),'Canvas changes with the actual generated audio signal');
  await win.loadURL(base);await run("document.fonts.ready");await wait(150);
  check(await run(`!document.documentElement.classList.contains('sound-active')&&localStorage.getItem('rakoto-sound')==='on'`),'Remembered sound opt-in never bypasses fresh user gesture');
  await run(`document.querySelector('[data-sound-toggle]').click()`);await wait(150);
  await run(`document.querySelector('[data-sound-toggle]').click()`);await wait(100);check(await run(`!document.documentElement.classList.contains('sound-active')`),'Sound mute control');
  await win.loadURL(base);await run("document.fonts.ready");await wait(200);check(await run(`!document.documentElement.classList.contains('sound-active')`),'Audio never autoplays after reload');
  check(await run(`document.querySelector('a[href="tel:+261382994693"]')&&document.querySelector('a[href="https://wa.me/261382994693"]')&&document.querySelector('a[href="https://www.linkedin.com/in/princy-rakotozafimalala-ba1022327/"]')`),'Owner contact links');
  await run(`Object.defineProperty(navigator,'share',{configurable:true,value:async data=>{window.__sharePayload=data}});document.querySelector('[data-share]').click()`);await wait(50);
  check(await run(`window.__sharePayload?.url==='https://michelprincy.github.io/rakoto-player/'`),'Share button passes the official URL without sending a message');
  await run(`Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async value=>{window.__copiedHashes=value}}});document.querySelector('[data-copy-hashes]').click()`);await wait(50);
  check(await run(`window.__copiedHashes?.includes('offline: ')&&window.__copiedHashes?.includes('android: ')`),'Checksum copy button');
  await run(`document.querySelectorAll('img[loading="lazy"]').forEach(i=>i.loading='eager')`);
  for(let attempt=0;attempt<60;attempt++){if(await run(`Array.from(document.images).filter(i=>i.getAttribute('src')).every(i=>i.complete&&i.naturalWidth)`))break;await wait(100)}
  const missing=await run(`Array.from(document.images).filter(i=>i.getAttribute('src')&&(!i.complete||!i.naturalWidth)).map(i=>i.src)`);check(!missing.length,'All screenshot and illustration assets load',missing);
  await win.webContents.debugger.sendCommand('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});await run(`document.querySelector('#plateformes').scrollIntoView({behavior:'instant'})`);await wait(1400);await snapshot('platforms');
  await win.webContents.debugger.sendCommand('Emulation.setDeviceMetricsOverride',{width:1200,height:630,deviceScaleFactor:1,mobile:false});
  await run(`document.documentElement.dataset.effects='lite';scrollTo({top:0,left:0,behavior:'instant'})`);await wait(120);
  if(base.startsWith('http://127.0.0.1'))await fs.writeFile(path.join(__dirname,'../assets/share.jpg'),(await win.webContents.capturePage()).toJPEG(88));
  check(errors.length===0,'No browser errors',errors);
 }catch(error){errors.push(error.message);console.error(error.message);process.exitCode=1}
 finally{await fs.writeFile(path.join(output,base.startsWith('http://127.')?'report-local.json':'report-published.json'),JSON.stringify({base,checks,errors},null,2));console.log(JSON.stringify({base,passed:checks.filter(c=>c.pass).length,failed:checks.filter(c=>!c.pass),errors}));win.destroy();app.exit(process.exitCode||0)}
});
