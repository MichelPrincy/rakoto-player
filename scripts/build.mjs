import {build} from 'esbuild';
import {readFile,mkdir,writeFile,cp} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.join(root,'docs');
const manifest=JSON.parse(await readFile(path.join(root,'public/release.json'),'utf8'));
if(!/^\d+\.\d+\.\d+$/.test(manifest.version))throw new Error('Version de téléchargement invalide.');
for(const type of ['offline','online']){
  const item=manifest[type];
  if(!item||!Number.isSafeInteger(item.bytes)||item.bytes<1||!/^[a-f0-9]{64}$/i.test(item.sha256)||
    !/^RAKOTO-Player-\d+\.\d+\.\d+-(Offline|Online)-x64\.exe$/.test(item.file)||
    new URL(item.url).protocol!=='https:'||!new URL(item.url).pathname.endsWith('/'+item.file))
    throw new Error('Métadonnées de téléchargement invalides : '+type);
}
if(manifest.android){
  const item=manifest.android;
  if(!/^\d+\.\d+\.\d+-beta\.\d+$/.test(item.version)||
    item.file!==`RAKOTO-Player-Android-${item.version}.apk`||!Number.isSafeInteger(item.bytes)||item.bytes<1||
    !/^[a-f0-9]{64}$/i.test(item.sha256)||new URL(item.url).protocol!=='https:'||
    !new URL(item.url).pathname.endsWith('/'+item.file)||item.minAndroid!=='6.0'||
    JSON.stringify(item.abis)!==JSON.stringify(['arm64-v8a','armeabi-v7a']))throw new Error('Métadonnées Android invalides.');
}
const downloadOrigin=new URL(manifest.offline.url).origin;
let html=await readFile(path.join(root,'src/index.html'),'utf8');
html=html.replaceAll('href="/downloads/','href="'+downloadOrigin+'/downloads/')
  .replaceAll('href="/favicon.png"','href="./assets/logo.png"')
  .replaceAll('href="/','href="./').replaceAll('src="/','src="./');
await mkdir(output,{recursive:true});
await writeFile(path.join(output,'index.html'),html);
await cp(path.join(root,'assets'),path.join(output,'assets'),{recursive:true});
await cp(path.join(root,'src/style.css'),path.join(output,'style.css'));
await writeFile(path.join(output,'release.json'),JSON.stringify(manifest,null,2)+'\n');
await writeFile(path.join(output,'.nojekyll'),'');
await build({entryPoints:[path.join(root,'src/client.ts')],outfile:path.join(output,'app.js'),bundle:true,minify:true,target:'es2022',format:'iife'});
console.log('Site compilé dans docs/ — compatible avec GitHub Pages et les sous-dossiers.');
