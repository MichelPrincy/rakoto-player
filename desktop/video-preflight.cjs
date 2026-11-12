/** One bounded background job at a time; foreground selection always takes priority. */
function createPreflight({analyze,update,delay=700}){
 let session=null,timer=null,generation=0,running=false,allowed=false;
 const schedule=()=>{clearTimeout(timer);if(allowed&&session&&!running){timer=setTimeout(tick,delay);timer.unref?.()}};
 const tick=async()=>{
  timer=null;if(!allowed||!session||running)return;
  const owner=session,token=generation,item=owner.items.find(item=>!item.analysis||item.analysis==='pending');
  if(!item)return;
  running=true;item.analysis='checking';update(owner,item);
  try{await analyze(item.id,{background:true});if(token===generation)item.analysis='ready'}
  catch(error){if(token===generation){item.analysis=['ANALYSIS_PREEMPTED','ANALYSIS_BUDGET','ANALYSIS_TIMEOUT'].includes(error.code)?'deferred':'error';item.analysisError=error.message}}
  finally{running=false;if(token===generation)update(owner,item);schedule()}
 };
 return {
  setSession(value){generation++;clearTimeout(timer);session=value;allowed=false},
  playback(ready){allowed=Boolean(ready);if(!allowed)clearTimeout(timer);else if(!timer)schedule()},
  pause(){allowed=false;clearTimeout(timer)},
  dispose(){generation++;clearTimeout(timer);session=null;allowed=false}
 };
}
module.exports={createPreflight};
