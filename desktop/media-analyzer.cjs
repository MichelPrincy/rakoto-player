const fs=require('node:fs/promises');
const path=require('node:path');
const {parentPort}=require('node:worker_threads');
let analyzer;
const positive=value=>Number.isFinite(Number(value))&&Number(value)>0?Number(value):null;
const languages={fr:'fra',en:'eng',ja:'jpn',mg:'mlg',de:'deu',es:'spa',it:'ita',pt:'por',zh:'zho',ko:'kor',ar:'ara',ru:'rus',hi:'hin',nl:'nld'};
const codecs={AVC:'h264',HEVC:'hevc',AV1:'av1',VP8:'vp8',VP9:'vp9','MPEG-4 Visual':'mpeg4',
  'Sorenson Spark':'flv1',AAC:'aac','AC-3':'ac3','E-AC-3':'eac3',DTS:'dts','MLP FBA':'truehd',
  FLAC:'flac',ALAC:'alac',Opus:'opus',Vorbis:'vorbis','UTF-8':'subrip',ASS:'ass',SSA:'ssa',
  PGS:'hdmv_pgs_subtitle',VobSub:'dvd_subtitle',WebVTT:'webvtt'};
const containers={Matroska:'matroska',WebM:'webm','MPEG-4':'mov',AVI:'avi','Windows Media':'asf',
  'Flash Video':'flv','MPEG-PS':'mpeg','MPEG-TS':'mpegts',BDAV:'mpegts','MPEG Audio':'mp3',
  FLAC:'flac',Wave:'wav',ADTS:'aac',ADIF:'aac',Ogg:'ogg',AIFF:'aiff'};
const webAudio={mp3:['mp3'],wav:['pcm_u8','pcm_s16le','pcm_s24le','pcm_s32le','pcm_f32le'],
  flac:['flac'],ogg:['vorbis','opus','flac'],mov:['aac','alac'],aac:['aac']};
const webMime={mp3:'audio/mpeg',wav:'audio/wav',flac:'audio/flac',ogg:'audio/ogg',mov:'audio/mp4',aac:'audio/aac'};

function codec(track){
  if(track.Format==='MPEG Video')return track.Format_Version==='1'?'mpeg1video':'mpeg2video';
  if(track.Format==='MPEG Audio')return /3/.test(track.Format_Profile)?'mp3':/2/.test(track.Format_Profile)?'mp2':'mp1';
  if(track.Format==='PCM'){
    const bits=positive(track.BitDepth);
    if(!bits)return 'pcm';
    const format=track.Format_Settings_Float==='Float'||track.CodecID==='3'?'f':track.Format_Settings_Sign==='Unsigned'||bits===8?'u':'s';
    return 'pcm_'+format+bits+(bits>8?(track.Format_Settings_Endianness==='Big'?'be':'le'):'');
  }
  if(track.Format==='WMA')return track.Format_Version==='2'?'wmav2':'wmav1';
  return codecs[track.Format]||String(track.Format||track.CodecID||'').toLowerCase();
}

function report(data,file,size){
  const tracks=data.media?.track||[],general=tracks.find(t=>t['@type']==='General')||{};
  const streams=tracks.filter(t=>['Video','Audio','Text'].includes(t['@type']));
  const clean=(track,index)=>({
    // StreamOrder is interleaved. Keep the PID too for transport streams.
    index:/^\d+$/.test(track.StreamOrder??'')?Number(track.StreamOrder):null,
    streamId:track.ID||'',ordinal:index,type:{Video:'video',Audio:'audio',Text:'subtitle'}[track['@type']],
    codec:codec(track),codecName:track.Format_Commercial_IfAny||track.Format||'',profile:track.Format_Profile||'',
    language:languages[track.Language]||track.Language||'',title:track.Title||'',
    channels:positive(track.Channels),layout:({1:'1.0',2:'2.0'})[track.Channels]||
      (/\bLFE\b/.test(track.ChannelLayout||'')?({6:'5.1',8:'7.1'})[track.Channels]:'')||track.ChannelLayout||'',
    sampleRate:positive(track.SamplingRate),width:positive(track.Width),height:positive(track.Height),
    fps:positive(track.FrameRate),bitrate:positive(track.BitRate),
    default:/^Yes\b/.test(track.Default||''),forced:/^Yes\b/.test(track.Forced||'')
  });
  const video=streams.filter(t=>t['@type']==='Video').map(clean);
  const audio=streams.filter(t=>t['@type']==='Audio').map(clean);
  if(!video.length&&!audio.length)throw new Error('Aucune piste audio ou vidéo détectée.');
  const kind=containers[general.Format]||String(general.Format||'').toLowerCase();
  const simple=!video.length&&audio.length===1&&webAudio[kind]?.includes(audio[0].codec);
  const chapters=tracks.filter(t=>t['@type']==='Menu').flatMap(t=>Object.entries(t.extra||{}).flatMap(([key,title])=>{
    const time=key.match(/^_?(\d+)_(\d{2})_(\d{2})_(\d{3})$/);
    return time?[{time:Number(time[1])*3600+Number(time[2])*60+Number(time[3])+Number(time[4])/1000,title:String(title)}]:[];
  })).sort((a,b)=>a.time-b.time);
  return {filename:path.basename(file),container:general.Format||'',formats:[kind],
    duration:positive(general.Duration)||Math.max(...streams.map(t=>positive(t.Duration)||0))||null,
    bitrate:positive(general.OverallBitRate),size,video,audio,
    subtitles:streams.filter(t=>t['@type']==='Text').map(clean),chapters,native:!simple,webMime:simple?webMime[kind]:null};
}

async function probe(file){
  const handle=await fs.open(file);
  try{
    const stat=await handle.stat();
    if(!stat.isFile()||!stat.size)throw new Error('Fichier média vide ou invalide.');
    analyzer ||= require('mediainfo.js').default({format:'object',chunkSize:128*1024,
      locateFile:()=>require.resolve('mediainfo.js/MediaInfoModule.wasm')});
    const media=await analyzer;
    const result=await media.analyzeData(stat.size,async(size,offset)=>{
      if(!Number.isSafeInteger(size)||size<0||size>8*1024*1024||!Number.isSafeInteger(offset)||offset<0||offset>stat.size)
        throw new Error('Lecture média invalide.');
      const buffer=Buffer.allocUnsafe(Math.min(size,stat.size-offset));
      const {bytesRead}=await handle.read(buffer,0,buffer.length,offset);
      return buffer.subarray(0,bytesRead);
    });
    return report(result,file,stat.size);
  }finally{await handle.close();}
}
parentPort.on('message',async({id,file})=>{
  try{parentPort.postMessage({id,result:await probe(file)});}
  catch(error){parentPort.postMessage({id,error:String(error.message).slice(0,500)});}
});
