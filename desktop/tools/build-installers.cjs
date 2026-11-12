const fs=require('node:fs');
const path=require('node:path');
const {execFileSync}=require('node:child_process');
const {createHash}=require('node:crypto');
const root=path.resolve(__dirname,'..');
const release=require('../installer/release.json');
const onlineOnly=process.argv.includes('--online-only');
const output=path.join(root,'dist','installateurs');
const nativeCache=path.join(process.env.LOCALAPPDATA,'electron-builder','Cache');
const nsis=process.env.NSIS_HOME||path.join(nativeCache,'nsis','nsis-3.0.4.1');
const plugins=path.join(nativeCache,'nsis','nsis-resources-3.4.1','plugins','x86-unicode');
const compiler=path.join(nsis,'makensis.exe');
const source=path.join(root,'dist','RAKOTO Player');
if(!fs.existsSync(path.join(source,'RAKOTO Player.exe')))throw new Error('Construisez le portable avant les installateurs.');
if(!fs.existsSync(compiler))throw new Error('NSIS est absent. Définissez NSIS_HOME vers le dossier contenant makensis.exe.');
if(release.version!==require('../package.json').version)throw new Error('Versions de publication différentes.');
fs.mkdirSync(output,{recursive:true});
fs.mkdirSync(path.join(root,'installer','work'),{recursive:true});
const work=fs.mkdtempSync(path.join(root,'installer','work','build-'));
const app=path.join(work,'application'),generated=path.join(work,'generated');
const run=(exe,args)=>execFileSync(exe,args,{cwd:root,windowsHide:true,stdio:'inherit'});
function inventory(directory){
  return fs.readdirSync(directory,{withFileTypes:true}).flatMap(entry=>{
    const file=path.join(directory,entry.name);
    return entry.isDirectory()?inventory(file):[file];
  });
}
const hash=file=>createHash('sha256').update(fs.readFileSync(file)).digest('hex');
try{
  fs.cpSync(source,app,{recursive:true});
  fs.mkdirSync(generated,{recursive:true});
  run('python',[path.join(root,'installer','artwork.py')]);
  const edit=path.join(nativeCache,'winCodeSign','winCodeSign-2.6.0','rcedit-x64.exe');
  if(!fs.existsSync(edit))throw new Error('rcedit est absent du cache de compilation.');
  run(edit,[path.join(app,'RAKOTO Player.exe'),'--set-icon',path.join(root,'ui','rakoto.ico'),
    '--set-version-string','ProductName','RAKOTO Player','--set-version-string','FileDescription','RAKOTO Player',
    '--set-version-string','CompanyName','RAKOTO Player','--set-version-string','OriginalFilename','RAKOTO Player.exe',
    '--set-product-version',release.version+'.0','--set-file-version',release.version+'.0']);
  const files=inventory(app);
  const directories=new Set();
  for(const file of files){let directory=path.dirname(file);while(directory!==app){directories.add(path.relative(app,directory));directory=path.dirname(directory);}}
  const safe=value=>value.replaceAll('$','$$');
  const uninstall=files.map(file=>'Delete "$INSTDIR\\'+safe(path.relative(app,file))+'"');
  uninstall.push(...[...directories].sort((a,b)=>b.split(path.sep).length-a.split(path.sep).length).map(dir=>'RMDir "$INSTDIR\\'+safe(dir)+'"'));
  fs.writeFileSync(path.join(generated,'uninstall-files.nsh'),uninstall.join('\n'),'utf8');
  const associations=require('../windows-associations.cjs').registration();
  const unregister=require('../windows-associations.cjs').unregistration();
  fs.writeFileSync(path.join(generated,'associations.nsh'),associations.join('\n'),'utf8');
  fs.writeFileSync(path.join(generated,'unregister-associations.nsh'),unregister.join('\n'),'utf8');
  run(path.join(process.env.WINDIR,'Microsoft.NET','Framework64','v4.0.30319','csc.exe'),
    ['/nologo','/optimize+','/target:exe','/out:'+path.join(generated,'HashCheck.exe'),path.join(root,'installer','HashCheck.cs')]);
  const offline=path.join(output,release.offline),online=path.join(output,release.online);
  const common=['/V2','/INPUTCHARSET','UTF8','/DROOT='+root,'/DVERSION='+release.version,
    '/DPLUGINSDIR_BUILD='+plugins,'/DGENERATED='+generated];
  if(onlineOnly){
    const previous=JSON.parse(fs.readFileSync(path.join(output,'release.json'),'utf8'));
    if(previous.version!==release.version||hash(offline)!==previous.offline.sha256)throw new Error('Le programme hors ligne précédemment construit a changé.');
  }else{
    console.log('Construction de l’installation hors ligne...');
    run(compiler,[...common,'/DAPPDIR='+app,'/DINSTALLED_KB='+Math.ceil(files.reduce((sum,file)=>sum+fs.statSync(file).size,0)/1024),
      '/DOUTFILE='+offline,path.join(root,'installer','offline.nsi')]);
  }
  const offlineHash=hash(offline);
  console.log('Construction du petit installateur en ligne...');
  run(compiler,[...common,'/DOUTFILE='+online,'/DPAYLOAD_URL='+release.origin+'/downloads/'+release.offline,
    '/DPAYLOAD_SHA256='+offlineHash,path.join(root,'installer','online.nsi')]);
  const artifact=file=>({file:path.basename(file),bytes:fs.statSync(file).size,sha256:hash(file),url:release.origin+'/downloads/'+path.basename(file)});
  const manifest={version:release.version,publishedAt:new Date().toISOString(),platform:'Windows 10 / 11 x64',
    offline:artifact(offline),online:artifact(online)};
  fs.writeFileSync(path.join(output,'release.json'),JSON.stringify(manifest,null,2)+'\n');
  fs.writeFileSync(path.join(output,'SHA256SUMS.txt'),[offline,online].map(file=>hash(file)+'  '+path.basename(file)).join('\n')+'\n');
  console.log(JSON.stringify(manifest,null,2));
}finally{
  const actual=path.resolve(work),parent=path.join(root,'installer','work');
  if(path.dirname(actual)!==parent||!path.basename(actual).startsWith('build-'))throw new Error('Chemin de construction inattendu.');
  fs.rmSync(actual,{recursive:true,force:true});
}
