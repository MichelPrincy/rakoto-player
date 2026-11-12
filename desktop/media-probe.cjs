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
let worker,idle,pending,serial=0,queue=Promise.resolve();

function analyze(file){
  clearTimeout(idle);
  if(!worker){
    const current=worker=new Worker(path.join(__dirname,'media-analyzer.cjs'));
    const fail=error=>{
      if(worker!==current)return;
      worker=null;
      if(pending){clearTimeout(pending.timer);pending.reject(error);pending=null;}
      current.terminate().catch(()=>{});
    };
    current.on('error',fail);
    current.on('exit',()=>fail(new Error('Le service d’analyse média a été arrêté.')));
    current.on('message',message=>{
      if(worker!==current||!pending||message.id!==pending.id)return;
      const job=pending;pending=null;clearTimeout(job.timer);
      current.unref();
      // Release the WASM heap when the library is no longer being scanned.
      idle=setTimeout(()=>{if(worker===current&&!pending){worker=null;current.terminate().catch(()=>{});}},15000);
      idle.unref();
      if(message.error)job.reject(new Error(message.error));else job.resolve(message.result);
    });
  }
  worker.ref();
  return new Promise((resolve,reject)=>{
    const id=++serial,current=worker;
    const timer=setTimeout(()=>{
      if(pending?.id!==id)return;
      pending=null;worker=null;
      current.terminate().catch(()=>{});
      reject(new Error('Délai d’analyse média dépassé.'));
    },20000);
    pending={id,resolve,reject,timer};
    worker.postMessage({id,file});
  });
}

function probe(file){
  // A single lazy analyzer: no duplicated WASM heaps or simultaneous decoding.
  const job=queue.then(()=>analyze(path.resolve(file)));
  queue=job.catch(()=>{});
  return job;
}

module.exports={probe,types,toolPath};
