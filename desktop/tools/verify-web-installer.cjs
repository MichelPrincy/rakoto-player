const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const {execFile}=require('node:child_process');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const release=require('../installer/release.json');
const work=path.join(root,'tools','media-work','bootstrap-tests');
fs.mkdirSync(work,{recursive:true});
const runner=path.join(work,'run.ps1');
fs.writeFileSync(runner,"$ErrorActionPreference='Stop'\n$p=Start-Process -FilePath $env:RAKOTO_INSTALLER_TEST -ArgumentList $env:RAKOTO_INSTALLER_ARGUMENTS -PassThru -WindowStyle Hidden\nif (-not $p.WaitForExit(120000)) {$p.Kill(); throw 'Délai de test dépassé'}\nexit $p.ExitCode\n");
const server=http.createServer((request,response)=>{
  if(request.url==='/valid.exe'){
    const file=path.join(root,'dist','installateurs',release.offline);response.writeHead(200,{'content-type':'application/octet-stream','content-length':fs.statSync(file).size});fs.createReadStream(file).pipe(response);
  }else if(request.url==='/corrupted.exe'){
    const bytes=Buffer.from('MZ-RAKOTO-CORRUPTED-PAYLOAD');
    response.writeHead(200,{'content-type':'application/octet-stream','content-length':bytes.length});response.end(bytes);
  }else {response.writeHead(404);response.end('absent');}
});
(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const origin='http://127.0.0.1:'+server.address().port;
  try{
    for(const [file,expected] of (process.argv.includes('--success')?[['valid.exe',0]]:[['corrupted.exe',4],['absent.exe',3]])){
      const target=path.join(work,'never-installed-'+file);
      assert.equal(fs.existsSync(target),false);
      const result=await new Promise(resolve=>execFile('pwsh.exe',['-NoProfile','-File',runner],{
        windowsHide:true,env:{...process.env,RAKOTO_INSTALLER_TEST:path.join(root,'dist','installateurs',release.online),
          RAKOTO_INSTALLER_ARGUMENTS:'/S /TESTMODE /PAYLOADURL="'+origin+'/'+file+'" /D='+target}
      },error=>resolve(error?.code||0)));
      assert.equal(result,expected,file);
      if(file==='valid.exe'){
        assert.equal(JSON.parse(fs.readFileSync(path.join(target,'resources/app/package.json'),'utf8')).version,release.version);
        assert.equal(path.dirname(path.resolve(target)),path.resolve(work));
        await new Promise((resolve,reject)=>execFile('pwsh.exe',['-NoProfile','-File',runner],{windowsHide:true,env:{...process.env,RAKOTO_INSTALLER_TEST:path.join(target,'Désinstaller RAKOTO Player.exe'),RAKOTO_INSTALLER_ARGUMENTS:'/S'}},error=>error?reject(error):resolve()));
        for(let attempt=0;attempt<50&&fs.existsSync(target);attempt++)await new Promise(resolve=>setTimeout(resolve,100));
      }
      assert.equal(fs.existsSync(target),false);
      console.log(file+(file==='valid.exe'?': téléchargement, SHA-256, installation et désinstallation réussis.':': rejet attendu, code '+result+', aucune installation exécutée.'));
    }
  }finally{await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1});
