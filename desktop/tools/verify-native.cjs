const assert=require('node:assert/strict');
const path=require('node:path');
const {NativePlayer}=require('../native-player.cjs');
const {probe}=require('../media-probe.cjs');
const {trackText}=require('../media-menu.cjs');

const root=path.resolve(process.argv[2]||'tools/media-work/fixtures');
const fixtures=[
  ['mkv-simple.mkv','h264',1,0],['MULTi.1080p.DSNP.WEB.H265-BYOR.mkv','h264',4,3],
  ['mp4-h264.mp4','h264',1,0],['mp4-hevc.mp4','hevc',1,0],['xvid.avi','mpeg4',1,0],
  ['vp8.webm','vp8',1,0],['musique.flac',null,1,0],['musique.mp3',null,1,0],
  ['sans-audio.mkv','h264',0,0],['contenu-sans-extension','hevc',1,0],
  ['movie.mov','h264',1,0],['video.m4v','h264',1,0],['video.3gp','h264',1,0],
  ['windows.wmv','wmv2',1,0],['flash.flv','flv1',1,0],['mpeg1.mpeg','mpeg1video',1,0],
  ['mpeg2.mpg','mpeg2video',1,0],['transport.ts','h264',1,0],['bluray.m2ts','h264',1,0],
  ['audio.aac',null,1,0],['audio.ogg',null,1,0],['audio.m4a',null,1,0],['audio.opus',null,1,0]
];
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(predicate,label,timeout=8000){
  const end=Date.now()+timeout;
  while(!await predicate()){
    if(Date.now()>end)throw new Error('Délai dépassé : '+label);
    await pause(60);
  }
}
async function verifyNative(directory=root){
  const player=new NativePlayer({headless:true});
  let state={};
  player.on('update',value=>state=value);
  const prop=(name)=>player.send(['get_property',name]).catch(()=>null);
  try{
    for(const [name,codec,audio,subs] of fixtures){
      const file=path.join(directory,name);
      const info=await probe(file);
      assert.equal(info.video[0]?.codec??null,codec,name);
      assert.equal(info.audio.length,audio,name);
      assert.equal(info.subtitles.length,subs,name);
      await player.open(name,file,{});
      await until(()=>state.id===name&&state.loaded&&Number(state.properties['time-pos'])>.13,name);
      assert.equal(state.properties['track-list'].filter(track=>track.type==='audio').length,audio);
      if(codec)assert.equal(state.properties['track-list'].find(track=>track.type==='video')?.codec,codec);
      else assert.ok(state.properties['track-list'].find(track=>track.type==='audio')?.codec);
      if(subs){
        assert.deepEqual(info.audio.map(track=>track.language),['fra','eng','jpn','mlg']);
        assert.deepEqual(info.audio.map(track=>track.codec),['aac','ac3','dts','aac']);
        assert.equal(info.audio[1].channels,6);
        assert.deepEqual(info.subtitles.map(track=>track.language),['fra','eng','jpn']);
        assert.equal(info.chapters.length,2);
        const serial=state.serial;
        const tracks=state.properties['track-list'];
        const english=tracks.find(track=>track.type==='audio'&&track.lang==='eng');
        const japanese=tracks.find(track=>track.type==='sub'&&track.lang==='jpn');
        assert.match(trackText(english,info),/Anglais.*AC3.*5\.1/);
        await player.transport('seek',2);
        await until(async()=>Number(await prop('time-pos'))>=1.8,'recherche exacte');
        await player.set('aid',english.id);
        await player.set('sid',japanese.id);
        await until(async()=>await prop('aid')===english.id&&await prop('sid')===japanese.id,'sélection des pistes');
        assert.equal(state.serial,serial);
        assert.ok(Number(await prop('time-pos'))>=1.8);
        await until(async()=>String(await prop('sub-text')).includes('Japanese ASS'),'sous-titre ASS intégré');
        await player.set('sid','no');
        assert.equal(await prop('sid'),false);
        await player.addSubtitle(path.join(directory,'external.vtt'));
        await until(async()=>String(await prop('sub-text')).includes('externe VTT'),'sous-titre externe');
        await player.set('audio-delay',.15);assert.equal(await prop('audio-delay'),.15);
        await player.set('sub-delay',.2);assert.equal(await prop('sub-delay'),.2);
        await player.set('speed',.25);assert.equal(await prop('speed'),.25);
        await player.set('speed',1);
        await player.set('sub-ass-override','force');
        await player.set('sub-font','Arial');
        await player.set('sub-font-size',42);
        await player.set('sub-color','#7DD3FC');
        await player.set('sub-pos',92);
        assert.equal(await prop('sub-font-size'),42);
        await player.set('video-aspect-override',4/3);
        assert.ok(Math.abs(Number(await prop('video-aspect-override'))-4/3)<.01);
        await player.set('video-zoom',Math.log2(1.5));
        await player.set('video-rotate',90);
        await player.set('deinterlace',true);
        await player.flip('h');await player.flip('v');
        await player.flip('h');await player.flip('v');
        await player.set('deinterlace',false);
        await player.set('video-rotate',0);
        await player.set('audio-channels','stereo');
        await player.equalizer({enabled:true,bands:[2,0,0,0,0,0,0,0,0,0],bass:3,normalize:true});
        assert.match(JSON.stringify(await prop('af')),/dynaudnorm/);
        await player.equalizer({enabled:false});
        await player.set('aid','no');assert.equal(await prop('aid'),false);
        await player.set('aid',english.id);
        await player.transport('step');
        await until(async()=>await prop('pause')===true,'lecture image par image');
        await player.transport('backstep');
        await player.set('pause',false);
      }
      console.log('Lecture native OK : '+name);
    }
    await assert.rejects(probe(path.join(directory,'endommagé.mkv')));
    await assert.rejects(player.open('corrupt',path.join(directory,'endommagé.mkv'),{}));
    await until(()=>state.id==='corrupt'&&state.event==='error','fichier endommagé');
    console.log('Fichier endommagé rejeté par MediaInfo et mpv.');
  }finally{player.close();}
}
if(require.main===module)verifyNative().catch(error=>{console.error(error);process.exitCode=1;});
module.exports={verifyNative,until,fixtures};





