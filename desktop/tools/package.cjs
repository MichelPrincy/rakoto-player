const fs = require('node:fs');
const path = require('node:path');
require('./compile.cjs');

const root=path.resolve(__dirname,'..');
const runtime=path.dirname(require('electron'));
const dist=path.join(root,'dist');
const output=path.join(dist,'RAKOTO Player');
const lock=require('../package-lock.json');
const sources=['package.json','main.cjs','preload.cjs','advanced.cjs','native-player.cjs','media-probe.cjs','media-menu.cjs',
  'win-video.cjs','media-analyzer.cjs','video-session.cjs','native','ui','tools/verify.cjs','tools/verify-advanced.cjs','tools/verify-native.cjs','tools/verify-session.cjs','tools/test.webm'];

function dependencies(){
  const found=new Set();
  function locate(name,base){
    while(true){
      const key=(base?base+'/':'')+'node_modules/'+name;
      if(lock.packages[key])return key;
      if(!base)return null;
      const parent=base.lastIndexOf('/node_modules/');
      base=parent<0?'':base.slice(0,parent);
    }
  }
  function add(name,base='',optional=false){
    const key=locate(name,base);
    if(!key||!fs.existsSync(path.join(root,key))){
      if(optional)return;
      throw new Error('Dépendance native indisponible : '+name);
    }
    if(found.has(key))return;
    found.add(key);
    const entry=lock.packages[key];
    // yargs is used only by MediaInfo's command-line interface, which is not shipped.
    for(const child of Object.keys(entry.dependencies||{})){
      if(name==='mediainfo.js'&&child==='yargs')continue;
      add(child,key);
    }
    for(const child of Object.keys(entry.optionalDependencies||{}))add(child,key,true);
  }
  add('koffi');
  add('music-metadata');
  add('mediainfo.js');
  return found;
}

function removeGenerated(directory,prefix){
  const resolved=path.resolve(directory);
  if(path.dirname(resolved)!==dist||!path.basename(resolved).startsWith(prefix))
    throw new Error('Dossier de construction inattendu : '+resolved);
  fs.rmSync(resolved,{recursive:true,force:true});
}

for(const name of ['mpv.exe'])
  if(!fs.existsSync(path.join(root,'native',name)))throw new Error('Binaire natif manquant : '+name);
const packages=dependencies();
fs.mkdirSync(dist,{recursive:true});
const stage=fs.mkdtempSync(path.join(dist,'.rakoto-build-'));
const backup=path.join(dist,'.rakoto-old-'+path.basename(stage).split('-').at(-1));
const app=path.join(stage,'resources','app');
let swapped=false;
try{
  const locales=new Set(['fr.pak','en-US.pak']);
  fs.cpSync(runtime,stage,{recursive:true,filter:file=>{
    const relative=path.relative(runtime,file);
    return path.dirname(relative)!=='locales'||locales.has(path.basename(relative));
  }});
  fs.renameSync(path.join(stage,'electron.exe'),path.join(stage,'RAKOTO Player.exe'));
  for(const name of sources){
    const target=path.join(app,name);
    fs.mkdirSync(path.dirname(target),{recursive:true});
    fs.cpSync(path.join(root,name),target,{recursive:true,filter:file=>path.basename(file)!=='ffprobe.exe'});
  }
  fs.copyFileSync(path.join(root,'node_modules','vue','LICENSE'),path.join(app,'ui','VUE-LICENSE.txt'));
  for(const name of packages){
    const source=path.join(root,name),target=path.join(app,name);
    fs.mkdirSync(path.dirname(target),{recursive:true});
    fs.cpSync(source,target,{recursive:true,filter:file=>{
      const relative=path.relative(source,file),first=relative.split(path.sep)[0];
      if(file.endsWith('.map'))return false;
      if(name==='node_modules/koffi'&&['vendor','doc'].includes(first))return false;
      if(name==='node_modules/mediainfo.js'){
        if(path.basename(file)==='cli.cjs')return false;
        if(first==='src')return false;
        if(first==='dist'&&relative.split(path.sep).length>1){
          const second=relative.split(path.sep)[1];
          if(!['cjs','MediaInfoModule.wasm'].includes(second))return false;
        }
      }
      return true;
    }});
  }
  if(!process.argv.includes('--no-runtime-checks')){
    require('node:child_process').execFileSync(process.execPath,['-e','require(process.argv[1])',path.join(app,'win-video.cjs')],{stdio:'inherit',windowsHide:true});
    require('node:child_process').execFileSync(process.execPath,['-e',
      'require(process.argv[1]).probe(process.argv[2]).then(info=>{if(!info.video.length)process.exitCode=1}).catch(error=>{console.error(error);process.exitCode=1})',
      path.join(app,'media-probe.cjs'),path.join(root,'tools','test.webm')],{stdio:'inherit',windowsHide:true});
  }
  if(fs.existsSync(output))fs.renameSync(output,backup);
  try{fs.renameSync(stage,output);swapped=true;}
  catch(error){if(fs.existsSync(backup))fs.renameSync(backup,output);throw error;}
  if(fs.existsSync(backup))removeGenerated(backup,'.rakoto-old-');
  console.log(path.join(output,'RAKOTO Player.exe'));
}finally{
  if(!swapped&&fs.existsSync(stage))removeGenerated(stage,'.rakoto-build-');
}

