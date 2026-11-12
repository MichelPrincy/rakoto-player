const path=require('node:path');
const {Worker}=require('node:worker_threads');

const types={
  mp3:'audio/mpeg',wav:'audio/wav',flac:'audio/flac',ogg:'audio/ogg',opus:'audio/ogg',m4a:'audio/mp4',aac:'audio/aac',
  aif:'audio/aiff',aiff:'audio/aiff',ac3:'audio/ac3',dts:'audio/vnd.dts',wma:'audio/x-ms-wma',mka:'audio/x-matroska',
  mp4:'video/mp4',m4v:'video/mp4',webm:'video/webm',mkv:'video/x-matroska',mov:'video/quicktime',
  avi:'video/x-msvideo',wmv:'video/x-ms-wmv',flv:'video/x-flv',mpeg:'video/mpeg',mpg:'video/mpeg',
  ts:'video/mp2t',m2ts:'video/mp2t',mts:'video/mp2t','3gp':'video/3gpp','3g2':'video/3gpp2'
};
const toolPath=name=>path.join(__dirname,'native',name+'.exe');
let worker,idle,pending,serial=0;
const jobs=[],byPath=new Map();let analyses=0;
const failure=(message,code)=>Object.assign(new Error(message),{code});
function release(){const old=worker;worker=null;if(old)old.terminate().catch(()=>{})}
function complete(error,result){
 const job=pending;if(!job)return;pending=null;clearTimeout(job.timer);byPath.delete(job.file);
 if(error)job.reject(error);else job.resolve(result);
 if(worker){worker.unref();clearTimeout(idle);idle=setTimeout(release,1500);idle.unref()}
 queueMicrotask(run);
}
function run(){
 if(pending||!jobs.length)return;clearTimeout(idle);
 const job=pending=jobs.shift();
 if(!worker){
  const current=worker=new Worker(path.join(__dirname,'media-analyzer.cjs'));
  current.on('error',error=>{if(worker===current){release();complete(error)}});
  current.on('exit',()=>{if(worker===current){worker=null;complete(new Error('Le service d’analyse média a été arrêté.'))}});
  current.on('message',message=>{if(worker!==current||pending?.id!==message.id)return;complete(message.error?failure(message.error,message.code):null,message.result)});
 }
 analyses++;worker.ref();job.timer=setTimeout(()=>{if(pending!==job)return;release();complete(failure('Délai d’analyse média dépassé.','ANALYSIS_TIMEOUT'))},job.background?3500:20000);
 worker.postMessage({id:job.id,file:job.file,budget:job.background?16*1024*1024:0});
}
function probe(file,{background=false}={}){
 file=path.resolve(file);
 const existing=byPath.get(file);
 if(existing){
  if(!background&&existing.background){
   existing.background=false;
   if(existing===pending){clearTimeout(existing.timer);existing.timer=setTimeout(()=>{release();complete(failure('Délai d’analyse média dépassé.','ANALYSIS_TIMEOUT'))},20000)}
   else{jobs.splice(jobs.indexOf(existing),1);jobs.unshift(existing)}
  }
  return existing.promise;
 }
 const job={id:++serial,file,background};job.promise=new Promise((resolve,reject)=>Object.assign(job,{resolve,reject}));byPath.set(file,job);
 if(!background&&pending?.background){release();complete(failure('Analyse différée pour donner priorité à la lecture.','ANALYSIS_PREEMPTED'))}
 if(background)jobs.push(job);else jobs.unshift(job);
 run();return job.promise;
}
function cancelBackground(except){if(pending?.background&&pending.file!==except){release();complete(failure('Analyse différée.','ANALYSIS_PREEMPTED'))}}
module.exports={probe,types,toolPath,cancelBackground,diagnostics:()=>({analyses})};
