const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const wave = (seconds,hz) => {
  const count=16000*seconds;
  const data=Buffer.alloc(44+count*2);
  data.write('RIFF');data.writeUInt32LE(data.length-8,4);data.write('WAVEfmt ',8);
  data.writeUInt32LE(16,16);data.writeUInt16LE(1,20);data.writeUInt16LE(1,22);
  data.writeUInt32LE(16000,24);data.writeUInt32LE(32000,28);data.writeUInt16LE(2,32);data.writeUInt16LE(16,34);
  data.write('data',36);data.writeUInt32LE(count*2,40);
  for(let index=0;index<count;index++)data.writeInt16LE(Math.round(Math.sin(index*hz*Math.PI/8000)*3500),44+index*2);
  return data;
};

module.exports = async ({ main, changeWindow, current, register, app, advanced }) => {
  const destination = path.resolve(process.env.RAKOTO_CAPTURE || app.getPath('userData'));
  await fs.mkdir(destination,{recursive:true});
  const run = js => main.webContents.executeJavaScript(js,true);
  const snapshot = () => run('rakotoTest.state()');
  const waitFor = async (condition,label) => {
    const end=Date.now()+9000;
    while(!await condition()) {
      if(Date.now()>end)throw new Error('Vérification expirée : '+label+' '+JSON.stringify(await snapshot()));
      await pause(70);
    }
  };
  const capture = async (win,name) => {
    await pause(260);
    const image=await win.webContents.capturePage(undefined,{stayHidden:true});
    assert.ok(!image.isEmpty(), name);
    await fs.writeFile(path.join(destination,name+'.png'),image.toPNG());
  };
  try {
    await waitFor(()=>run('Boolean(window.rakotoTest)'), 'démarrage Vue');
    await capture(main,'rakoto-accueil');
    await changeWindow('mini',1);
    let state=current();
    assert.equal(state.mode,'mini');
    assert.equal(state.floating.isAlwaysOnTop(),true);
    assert.equal(state.floating.isVisible(),true);
    assert.equal(main.isVisible(),false);
    await capture(state.floating,'rakoto-mini');
    await changeWindow('pill',1);
    assert.equal(current().mode,'pill');
    assert.equal(current().floating.getBounds().width,218);
    await capture(current().floating,'rakoto-pill');
    await changeWindow('normal');
    await waitFor(()=>main.isVisible(),'restauration de la fenêtre principale');
    assert.ok(!current().floating?.isVisible());
    await run("rakotoTest.scene('eq')");
    await capture(main,'rakoto-egaliseur');
    await run("rakotoTest.scene('immersive')");
    await capture(main,'rakoto-immersif');
    await run("rakotoTest.scene('home')");

    const tmp=app.getPath('userData');
    const first=path.join(tmp,'Rakoto - Test audio.wav');
    const second=path.join(tmp,'Rakoto - Piste suivante.wav');
    await fs.writeFile(first,wave(6,440));
    await fs.writeFile(second,wave(6,330));
    await fs.writeFile(first.replace(/\.wav$/,'.lrc'),'[00:00.00]Début\n[00:02.00]Deuxième ligne\n');
    const video=path.join(tmp,'Test vidéo [360p].webm');
    await fs.copyFile(process.env.RAKOTO_TEST_VIDEO || path.join(__dirname,'test.webm'),video);
    const items=await Promise.all([first,second,video].map(file=>register(file)));
    assert.ok(items.every(Boolean));
    await run('rakotoTest.add('+JSON.stringify(items)+')');
    await run('rakotoTest.play('+JSON.stringify(items[0].id)+')');
    await waitFor(async()=>{const v=await snapshot();return v.playing&&v.time>.15&&v.graph&&v.duration===6;},'lecture WAV et graphe audio');
    assert.ok((await snapshot()).gain>2);
    await changeWindow('mini',.93);
    const before=(await snapshot()).time;
    await waitFor(async()=> (await snapshot()).time>before+.4,'lecture en arrière-plan');
    await current().floating.webContents.executeJavaScript("document.querySelector('.float-row .primary').click()",true);
    await waitFor(async()=>!(await snapshot()).playing,'commande pause via IPC');
    await changeWindow('pill',.93);
    await current().floating.webContents.executeJavaScript("document.querySelector('.float-row .primary').click()",true);
    await waitFor(async()=> (await snapshot()).playing,'reprise depuis le micro-lecteur');
    await changeWindow('normal');
    assert.equal((await snapshot()).id,items[0].id);
    await run('rakotoTest.seek(2.2)');
    await waitFor(async()=>(await snapshot()).caption==='Deuxième ligne','paroles LRC synchronisées');
    await run('rakotoTest.crossfade(1.5); rakotoTest.seek(4.8)');
    await waitFor(async()=>{const v=await snapshot();return v.id===items[1].id&&v.playing&&v.time>.5;},'transition audio');
    await run('rakotoTest.play('+JSON.stringify(items[2].id)+')');
    await waitFor(async()=>(await snapshot()).resolution==='360 × 202','lecture WebM');
    await changeWindow('video',1);
    await waitFor(()=>advanced.host?.isVisible(),'surface vidéo native');
    await changeWindow('normal');
    await fs.writeFile(path.join(destination,'rakoto-verification.txt'),
      'Vue, lecture WAV et WebM, égaliseur, fondu, LRC, quatre fenêtres et commandes IPC vérifiés.\n');
    app.exit(0);
  } catch(error) {
    await fs.writeFile(path.join(destination,'rakoto-verification.txt'),error.stack);
    console.error(error);
    app.exit(1);
  }
};




