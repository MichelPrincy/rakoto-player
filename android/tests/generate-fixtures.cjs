const fs=require('node:fs/promises');
const path=require('node:path');
const {execFile}=require('node:child_process');
const {promisify}=require('node:util');
const ffmpeg=process.argv[2]||process.env.FFMPEG||'ffmpeg';
const output=path.resolve(process.argv[3]||path.join(__dirname,'../app/src/androidTest/assets/fixtures'));
const run=promisify(execFile);
const image=['-f','lavfi','-i','testsrc2=size=320x180:rate=24:duration=14'];
const tone=(hz,layout='mono')=>['-f','lavfi','-i',layout==='mono'
  ? 'sine=frequency='+hz+':sample_rate=48000:duration=14'
  : 'aevalsrc=0.13*sin(2*PI*'+hz+'*t)|0.13*sin(2*PI*'+hz+'*t)|0.13*sin(2*PI*'+hz+'*t)|0.04*sin(2*PI*80*t)|0.13*sin(2*PI*'+hz+'*t)|0.13*sin(2*PI*'+hz+'*t):s=48000:d=14:c=5.1'];
const timed=(text)=>'1\n00:00:00,250 --> 00:00:12,800\n'+text+'\n';
const h264=['-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p','-threads','2'];
async function render(name,args) {
  await run(ffmpeg,['-hide_banner','-loglevel','error','-nostdin','-y',...args,path.join(output,name)],
    {windowsHide:true,timeout:90000,maxBuffer:1024*1024});
  console.log(name);
}
(async()=>{
  await fs.mkdir(output,{recursive:true});
  const write=(name,text)=>fs.writeFile(path.join(output,name),text);
  await write('fr.srt',timed('Sous-titre français'));
  await write('en.srt',timed('English subtitle'));
  await write('ja.ass','[Script Info]\nScriptType: v4.00+\nPlayResX: 320\nPlayResY: 180\n\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Segoe UI,24,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,12,12,12,1\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:00.25,0:00:12.80,Default,,0,0,0,,Japanese ASS\n');
  await write('external.vtt','WEBVTT\n\n00:00:00.250 --> 00:00:12.800\nSous-titre externe VTT\n');
  await write('chapters.txt',';FFMETADATA1\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=0\nEND=5500\ntitle=Ouverture\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=5500\nEND=14000\ntitle=Deuxième partie\n');
  await render('mkv-simple.mkv',[...image,...tone(330),...h264,'-c:a','aac','-ac','2','-t','5']);
  await render('MULTi.1080p.DSNP.WEB.H265-BYOR.mkv',[
    ...image,...tone(360),...tone(480,'5.1'),...tone(600,'5.1'),...tone(720),
    '-i',path.join(output,'fr.srt'),'-i',path.join(output,'en.srt'),'-i',path.join(output,'ja.ass'),
    '-i',path.join(output,'chapters.txt'),'-map_chapters','8',
    '-map','0:v','-map','1:a','-map','2:a','-map','3:a','-map','4:a','-map','5:s','-map','6:s','-map','7:s',
    ...h264,'-c:a:0','aac','-ac:a:0','2','-c:a:1','ac3','-c:a:2','dca','-strict','-2','-c:a:3','aac','-c:s','copy',
    '-metadata:s:a:0','language=fra','-metadata:s:a:1','language=eng','-metadata:s:a:2','language=jpn',
    '-metadata:s:a:3','language=mlg','-metadata:s:s:0','language=fra','-metadata:s:s:1','language=eng',
    '-metadata:s:s:2','language=jpn','-t','14']);
  await render('mp4-h264.mp4',[...image,...tone(330),...h264,'-c:a','aac','-t','5']);
  await render('mp4-hevc.mp4',[...image,...tone(440),'-c:v','libx265','-preset','ultrafast','-x265-params','pools=1:frame-threads=1:log-level=error',
    '-tag:v','hvc1','-pix_fmt','yuv420p','-c:a','aac','-t','5']);
  await render('xvid.avi',[...image,...tone(550),'-c:v','mpeg4','-vtag','XVID','-c:a','libmp3lame','-t','5']);
  await render('vp8.webm',[...image,...tone(660),'-c:v','libvpx','-deadline','realtime','-cpu-used','5','-c:a','libopus','-t','5']);
  await render('sans-audio.mkv',[...image,...h264,'-an','-t','5']);
  await render('musique.flac',[...tone(330),'-c:a','flac','-t','6']);
  await render('musique.mp3',[...tone(440),'-c:a','libmp3lame','-t','6']);
  await render('pcm.wav',[...tone(550),'-c:a','pcm_s16le','-t','6']);
  for(const [name,videoCodec,audioCodec,extra] of [
    ['movie.mov','libx264','aac',[]],['video.m4v','libx264','aac',['-f','mp4']],['video.3gp','libx264','aac',[]],
    ['windows.wmv','wmv2','wmav2',[]],['flash.flv','flv','libmp3lame',['-ar','44100']],
    ['mpeg1.mpeg','mpeg1video','mp2',[]],['mpeg2.mpg','mpeg2video','mp2',[]],
    ['transport.ts','libx264','aac',[]],['bluray.m2ts','libx264','aac',['-mpegts_m2ts_mode','1']]
  ]) await render(name,[...image,...tone(330),'-c:v',videoCodec,'-c:a',audioCodec,...extra,'-threads','2','-t','3']);
  for(const [name,codec] of [['audio.aac','aac'],['audio.ogg','libvorbis'],['audio.m4a','aac'],['audio.opus','libopus']])
    await render(name,[...tone(330),'-c:a',codec,'-t','3']);
  await write('musique.lrc','[00:00.00]Ouverture\n[00:02.00]Paroles synchronisées\n');
  await fs.copyFile(path.join(output,'mp4-hevc.mp4'),path.join(output,'contenu-sans-extension'));
  await write('endommagé.mkv','Contenu volontairement invalide pour la vérification.');
})().catch(error=>{console.error(error.stderr||error);process.exitCode=1;});
