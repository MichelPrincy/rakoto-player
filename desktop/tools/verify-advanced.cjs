const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {until}=require('./verify-native.cjs');
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));

module.exports=async({main,app,register,advanced,changeWindow,current})=>{
  const root=path.resolve(process.env.RAKOTO_FIXTURES||'tools/media-work/fixtures');
  const log=path.resolve(process.env.RAKOTO_TEST_LOG||path.join(app.getPath('userData'),'verification-avancee.txt'));
  const run=code=>main.webContents.executeJavaScript(code,true);
  const state=()=>run('rakotoTest.state()');
  const step=[];
  let result=0;
  try{
    await until(()=>run('Boolean(window.rakotoTest)'),'Vue');
    main.show();
    await until(()=>main.isVisible(),'fenêtre principale visible');
    await until(()=>run('document.querySelector(".chrome-brand img")?.naturalWidth===1536'),'logo fourni');
    assert.equal(await run('getComputedStyle(document.querySelector(".desktop"),"::after").content'),'none','aucun cadre intérieur superposé');
    if(process.env.RAKOTO_REAL_MEDIA){
      const directory=process.env.RAKOTO_REAL_MEDIA;
      const files=(await fs.readdir(directory)).filter(name=>name.toLowerCase().endsWith('.mkv'));
      for(const name of files){
        const item=await register(path.join(directory,name));
        assert.ok(item?.native && item.type==='video',name);
        await run('rakotoTest.add('+JSON.stringify([item])+')');
        await run('rakotoTest.play('+JSON.stringify(item.id)+')');
        await until(async()=>{
          const snapshot=await state();
          return snapshot.id===item.id&&snapshot.loaded&&snapshot.time>.8&&advanced.host?.isVisible();
        },'lecture réelle '+name);
        if(process.env.RAKOTO_PRESENTATION_SAMPLE){
          const sample=async()=>{
            const {promisify}=require('node:util');
            const runSample=promisify(require('node:child_process').execFile);
            return (await runSample(process.env.RAKOTO_PRESENTATION_SAMPLE,
              [String(advanced.host.getNativeWindowHandle().readUInt32LE(0))],{windowsHide:true})).stdout;
          };
          await advanced.player.transport('seek',120);
          await until(async()=>(await state()).time>120.3,'seek to picture');
          step.push('Fenêtre mpv : '+await advanced.player.send(['get_property','window-id']).catch(()=>null));
          step.push('Avec commandes : '+await sample());
          advanced.overlay.hide();
          await delay(450);
          step.push('Sans commandes : '+await sample());
          advanced.overlay.showInactive();
          step.push('Commandes réaffichées : '+await sample());
          await changeWindow('fullscreen');
          await until(()=>main.isFullScreen(),'plein écran natif');
          await delay(650);
          step.push('Plein écran : '+await sample());
          await changeWindow('video',1);
          await until(()=>advanced.host.isVisible()&&advanced.host.getParentWindow()===current().floating,'vidéo flottante');
          await delay(450);
          step.push('Vidéo flottante : '+await sample());
          await changeWindow('normal');
          await delay(350);
          step.push('Retour à la fenêtre normale : '+await sample());
          return;
        }
        const engine=advanced.player;
        const values=Object.fromEntries(await Promise.all(['vid','hwdec-current','video-params','video-out-params','current-vo',
          'vo-configured','estimated-frame-number','frame-drop-count','decoder-frame-drop-count'].map(async property=>[
          property,await engine.send(['get_property',property]).catch(()=>null)
        ])));
        step.push(name+' '+JSON.stringify(values));
        assert.ok(Number(values['estimated-frame-number'])>0 && values['video-out-params']?.w>0 && values['vo-configured'],name);
        assert.equal(await engine.send(['get_property','options/d3d11-flip']),false);
        if(name===files[0]){
          const before=(await state()).time;
          await engine.set('hwdec','no');
          await until(async()=>{
            const decoder=await engine.send(['get_property','hwdec-current']).catch(()=>null);
            return (!decoder||decoder==='no')&&(await state()).time>before+.25;
          },'décodage logiciel sans interruption');
          await engine.set('hwdec','auto-copy');
          step.push('Bascule vers le décodage logiciel vérifiée.');
        }
      }
      return;
    }
    const items=await Promise.all(['MULTi.1080p.DSNP.WEB.H265-BYOR.mkv','mp4-hevc.mp4','musique.flac','musique.mp3','pcm.wav']
      .map(name=>register(path.join(root,name))));
    assert.ok(items.every(Boolean));
    assert.equal(await register(path.join(root,'endommagé.mkv')),null);
    const [multi,hevc,flac,mp3,wav]=items;
    await run('rakotoTest.add('+JSON.stringify(items)+')');
    await run('rakotoTest.play('+JSON.stringify(multi.id)+')');
    await until(async()=>{
      const s=await state();
      return s.id===multi.id&&s.native&&s.loaded&&s.playing&&s.time>.25&&s.resolution==='320 × 180'&&advanced.host?.isVisible();
    },'lecture intégrée MKV et HWND mpv');
    assert.equal(advanced.player.snapshot().properties['current-vo'],'gpu');
    const trackList=advanced.player.snapshot().properties['track-list'];
    const english=trackList.find(track=>track.type==='audio'&&track.lang==='eng');
    const japanese=trackList.find(track=>track.type==='sub'&&track.lang==='jpn');
    const serial=advanced.player.serial;
    await run('desktop.nativeSelect("aid",'+english.id+')');
    await run('desktop.nativeSelect("sid",'+japanese.id+')');
    await until(async()=>{
      const s=await state();return s.audio.includes('anglais')&&s.subtitle.includes('japonais');
    },'pistes choisies depuis Vue');
    assert.equal(advanced.player.serial,serial);
    assert.ok((await state()).time>.25);
    step.push('MKV intégré et sélection audio / sous-titres par IPC sans rechargement.');

    await run('desktop.mediaInfo()');
    await until(async()=>advanced.details?.isVisible()&&await advanced.details.webContents.executeJavaScript(
      'document.body.textContent.includes("Matroska") && document.body.textContent.includes("malgache") && document.body.textContent.includes("Sous-titres intégrés · 3")',true),
      'fenêtre des métadonnées techniques');
    advanced.details.close();
    const capture=path.join(app.getPath('userData'),'controle-capture.png');
    await advanced.verifyScreenshot(capture);
    const png=await fs.readFile(capture);
    assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
    await fs.unlink(capture);
    step.push('Informations MediaInfo et commande de capture PNG vérifiées sans inspection visuelle.');

    await changeWindow('fullscreen');
    await until(()=>main.isFullScreen(),'plein écran natif');
    const overlay=advanced.overlay;
    const over=code=>overlay.webContents.executeJavaScript(code,true);
    await until(async()=>overlay.isVisible()&&await over('document.body.classList.contains("fullscreen")'),'superposition plein écran');
    assert.deepEqual(advanced.host.getBounds(),main.getContentBounds());
    await over('document.body.dispatchEvent(new PointerEvent("pointermove",{bubbles:true,screenX:200,screenY:200}))');
    assert.equal(await over('document.body.classList.contains("active")'),true);
    await until(()=>over('!document.body.classList.contains("active")'),'masquage après inactivité');
    await over('document.getElementById("glass").dispatchEvent(new PointerEvent("pointerdown",{bubbles:true}))');
    await delay(3200);
    assert.equal(await over('document.body.classList.contains("active")'),true);
    await over('document.getElementById("glass").dispatchEvent(new PointerEvent("pointerup",{bubbles:true}))');
    await over('document.body.dispatchEvent(new KeyboardEvent("keydown",{code:"Escape",bubbles:true}))');
    await until(()=>!main.isFullScreen(),'sortie Échap');
    step.push('Plein écran natif, géométrie vidéo, réveil souris, masquage, maintien pendant interaction et Échap.');

    await run('rakotoTest.seek(1)');
    await changeWindow('video',1);
    await until(()=>advanced.host.isVisible()&&advanced.host.getParentWindow()===current().floating,'vidéo flottante');
    assert.equal(advanced.player.serial,serial);
    await changeWindow('mini',1);
    assert.equal(advanced.host.isVisible(),false);
    const before=(await state()).time;
    await until(async()=>(await state()).time>before+.25,'lecture native derrière le mini-lecteur');
    await current().floating.webContents.executeJavaScript('desktop.command("toggle")',true);
    await until(async()=>!(await state()).playing,'pause depuis le mini-lecteur');
    await changeWindow('normal');
    step.push('Vidéo flottante et mini-lecteur sans redémarrage du flux.');

    await run('rakotoTest.play('+JSON.stringify(hevc.id)+')');
    await until(async()=>{const s=await state();return s.id===hevc.id&&s.native&&s.loaded&&s.playing&&s.time>.15;},'MP4 HEVC intégré');
    await run('rakotoTest.play('+JSON.stringify(flac.id)+')');
    await until(async()=>{const s=await state();return s.id===flac.id&&!s.native&&s.playing&&s.graph&&s.time>.15;},'FLAC et égaliseur Web Audio');
    await run('rakotoTest.seek(2.2)');
    await until(async()=>(await state()).caption.includes('Paroles synchronisées'),'paroles LRC conservées');
    main.webContents.send('player:command',{action:'audioEngine',value:'native'});
    await until(async()=>{const s=await state();return s.id===flac.id&&s.native&&s.loaded&&s.playing&&s.time>=2;},'bascule audio avancée');
    main.webContents.send('player:command',{action:'audioEngine',value:'web'});
    await until(async()=>{const s=await state();return s.id===flac.id&&!s.native&&s.playing&&s.time>=2;},'retour à Web Audio');
    await run('rakotoTest.crossfade(1.5); rakotoTest.seek(4.8)');
    await until(async()=>{const s=await state();return s.id===mp3.id&&s.playing&&s.time>.5;},'fondu FLAC vers MP3');
    await until(async()=>{const s=await state();return s.id===mp3.id&&s.playing&&s.time>.15;},'MP3 intégré');
    await run('rakotoTest.play('+JSON.stringify(wav.id)+')');
    await until(async()=>{const s=await state();return s.id===wav.id&&s.playing&&s.time>.15;},'WAV intégré');
    step.push('HEVC natif et FLAC / MP3 / WAV intégrés. Égaliseur Web Audio, fondu, LRC et sélection du moteur audio conservés.');
    await changeWindow('drawer',1);
    const drawer=code=>current().floating.webContents.executeJavaScript(code,true);
    const bars=()=>drawer('Array.from(document.querySelectorAll(".drawer-meter i"),b=>parseFloat(b.style.transform.slice(7)))');
    await until(async()=>{const x=await bars();return x.length===5&&Math.max(...x)>.32&&Math.min(...x)<.22;},'spectre Web Audio dans le tiroir');
    for(let turn=0;turn<3;turn++){
      const compact=current().floating;
      await drawer('document.querySelector(".drawer-art").click()');
      await until(()=>compact.isDestroyed()&&current().mode==='mini','remplacement du tiroir compact');
      assert.equal(current().floating.getBounds().width,420);
      assert.equal(current().floating.isVisible(),true);
      await until(()=>drawer('Boolean(document.querySelector(".drawer-volume input"))'),'tiroir étendu');
      if(turn<2){
        const expanded=current().floating;
        await drawer('document.querySelector(".drawer-art").click()');
        await until(()=>expanded.isDestroyed()&&current().mode==='drawer','retour au tiroir compact');
        assert.equal(current().floating.getBounds().width,310);
      }
    }
    step.push('Trois déploiements successifs sans conserver la fenêtre flottante précédente.');
    if(!await drawer('matchMedia("(prefers-reduced-motion: reduce)").matches')){
      const win=current().floating;
      const [x,y]=win.getPosition();
      await run('rakotoTest.appearance("nature","full")');
      await until(()=>drawer('document.querySelector(".app").dataset.motion==="full"'),'mouvement activé');
      win.setPosition(x-55,y);
      await until(()=>drawer('Math.abs(parseFloat(document.querySelector(".app").style.getPropertyValue("--sway-x")))>.1'),'impulsion du ressort');
      await until(()=>drawer('Math.abs(parseFloat(document.querySelector(".app").style.getPropertyValue("--sway-x")))<.03'),'retour au repos');
      const start=win.getPosition();
      const input=(type,x)=>{
        const [left,top]=win.getPosition();
        win.webContents.sendInputEvent({type,x,y:23,globalX:left+x,globalY:top+23,button:'left',clickCount:1});
      };
      input('mouseMove',130);
      input('mouseDown',130);
      await until(()=>drawer('document.querySelector(".drawer-shell").hasPointerCapture(1)'),'capture du pointeur');
      let peak=0,paintedMovement=0;
      for(let tick=0;tick<8;tick++){
        input('mouseMove',144+tick*4);
        await delay(35);
        const sway=await drawer('parseFloat(document.querySelector(".app").style.getPropertyValue("--sway-x"))');
        peak=Math.max(peak,Math.abs(sway));
        paintedMovement=Math.max(paintedMovement,Math.abs(await drawer('parseFloat(getComputedStyle(document.querySelector(".drawer-shell")).translate)')));
      }
      assert.ok(win.getPosition()[0]>start[0]+40,'la fenêtre suit le glissement');
      assert.ok(peak>2,'déformation du ressort pendant le glissement réel');
      assert.ok(paintedMovement>1,'fond et commandes déplacés ensemble');
      assert.equal(await drawer('document.querySelector(".app").classList.contains("is-dragging")'),true);
      assert.match(await drawer('getComputedStyle(document.querySelector(".drawer-shell")).backgroundImage'),/^linear-gradient/,'fond peint sur le tiroir lui-même');
      assert.equal(await drawer('getComputedStyle(document.querySelector(".drawer-shell"),"::before").content'),'none','aucune peau indépendante');
      assert.equal(await drawer('getComputedStyle(document.querySelector(".drawer-shell")).opacity'),'1','commandes lisibles');
      input('mouseUp',172);
      await until(()=>drawer('!document.querySelector(".app").classList.contains("is-dragging") && Math.abs(parseFloat(document.querySelector(".app").style.getPropertyValue("--sway-x")))<.03'),'relâchement du tiroir');
      step.push('Glissement par pointeur : fenêtre déplacée, fond et commandes sur une surface unique, ressort '+peak.toFixed(2)+' px et retour au repos.');
      await run('rakotoTest.appearance("forest","off")');
      await until(()=>drawer('document.querySelector(".app").dataset.motion==="off" && document.querySelector(".app").dataset.theme==="forest"'),'thème et animations désactivées');
      win.setPosition(x-70,y);
      await delay(250);
      assert.equal(await drawer('parseFloat(document.querySelector(".app").style.getPropertyValue("--sway-x"))'),0);
      await run('rakotoTest.appearance("nature","full")');
      step.push('Déplacement, ressort, retour au repos et désactivation des effets vérifiés.');
    }
    await drawer('desktop.mediaCommand("volume",.37)');
    await until(()=>drawer('Math.abs(+document.querySelector(".drawer-volume input").value-.37)<.01'),'volume du tiroir');
    const expanded=current().floating;
    await drawer('document.querySelector(".drawer-actions button:last-child").click()');
    await until(()=>expanded.isDestroyed()&&current().mode==='normal'&&main.isVisible(),'restauration sans fenêtre résiduelle');
    const mainStart=main.getBounds();
    main.focus();
    await delay(150);
    const mainInput=(type,x)=>{
      const [left,top]=main.getPosition();
      main.webContents.sendInputEvent({type,x,y:24,globalX:left+x,globalY:top+24,button:'left',clickCount:1});
    };
    mainInput('mouseMove',400);mainInput('mouseDown',400);
    await until(()=>run('document.querySelector(".chrome").hasPointerCapture(1)'),'capture de la barre de titre');
    await delay(35);
    const dragStart=main.getPosition();
    mainInput('mouseMove',430);
    await until(()=>main.getPosition()[0]>dragStart[0]+20,'déplacement de la fenêtre principale');
    mainInput('mouseUp',430);
    main.setBounds(mainStart);
    if(!await run('matchMedia("(prefers-reduced-motion: reduce)").matches')){
      main.setSize(mainStart.width-60,mainStart.height-30);
      await until(()=>run('Math.abs(parseFloat(document.querySelector(".app").style.getPropertyValue("--stretch"))-1)>.001'),'ressort du redimensionnement');
      main.setBounds(mainStart);
    }
    step.push('Barre de titre déplaçable et ressort lors du redimensionnement vérifiés.');
    await run('rakotoTest.play('+JSON.stringify(multi.id)+')');
    await until(async()=>{const s=await state();return s.id===multi.id&&s.native&&s.loaded&&s.playing;},'vidéo pour analyse audio');
    await run('rakotoTest.seek(2)');
    await changeWindow('drawer',1);
    await until(async()=>{const x=await bars();return x.length===5&&Math.max(...x)>.32;},'spectre réel mpv');
    await until(()=>drawer('document.querySelector(".drawer-art img").src.startsWith("data:image/jpeg;")'),'miniature vidéo');
    await drawer('desktop.command("toggle")');
    await until(async()=>{const x=await bars();return x.length===5&&Math.max(...x)<=.14;},'barres au repos en pause');
    await changeWindow('normal');
    await run('desktop.save({drawerPinned:false})');
    await delay(80);
    await changeWindow('drawer',1);
    assert.equal(current().floating.isAlwaysOnTop(),false);
    await changeWindow('hide-drawer');
    await until(()=>main.isMinimized()&&!current().floating?.isVisible(),'masquage du tiroir');
    await changeWindow('normal');
    const memory=app.getAppMetrics().reduce((kb,proc)=>kb+proc.memory.workingSetSize,0)/1024;
    step.push('Tiroir compact/étendu, volume, Web Audio, mpv, miniature et retour au repos vérifiés.');
    step.push('RAM système : '+(require('node:os').totalmem()/1073741824).toFixed(2)+' Gio ; processus Electron : '+memory.toFixed(1)+' Mio de mémoire résidente cumulée.');
    step.push('Mémoire par processus (Mio, pages partagées incluses) : '+JSON.stringify(app.getAppMetrics().map(proc=>({type:proc.type,resident:+(proc.memory.workingSetSize/1024).toFixed(1),private:+(proc.memory.privateBytes/1024).toFixed(1)}))));

  }catch(error){step.push(error.stack);result=1;}
  finally{
    advanced.shutdown();
    await fs.writeFile(log,step.join('\n')+'\n');
    app.exit(result);
  }
};







