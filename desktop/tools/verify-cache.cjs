const fs=require('node:fs/promises');
const assert=require('node:assert/strict');
module.exports=async({app,main,catalog,diagnostics})=>{
 let result={};
 try{
  await new Promise(resolve=>setTimeout(resolve,500));
  const loaded=await main.webContents.executeJavaScript('rakotoTest.state()',true);
  result={restored:catalog.size,analyses:diagnostics().analyses,electronWorkingSetKiB:app.getAppMetrics().reduce((sum,p)=>sum+p.memory.workingSetSize,0),loaded};
  assert.ok(catalog.size>0);assert.equal(result.analyses,0,'cached library must not probe files at startup');
  result.passed=true;
 }catch(error){result.error=error.stack;process.exitCode=1}
 finally{await fs.writeFile(process.env.RAKOTO_CACHE_LOG,JSON.stringify(result,null,2));app.exit(process.exitCode||0)}
};
