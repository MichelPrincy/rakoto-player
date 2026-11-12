import {build,transform} from 'esbuild';
import {readFile,mkdir,writeFile,cp} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {renderPage,escape} from '../src/page.mjs';
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
  if(item.fallbackUrl&&(new URL(item.fallbackUrl).protocol!=='https:'||!new URL(item.fallbackUrl).pathname.endsWith('/'+item.file)))throw new Error('Lien Android de secours invalide.');
  for(const variant of item.variants||[]){
    if(!['arm64-v8a','armeabi-v7a'].includes(variant.abi)||variant.file!==`RAKOTO-Player-Android-${item.version}-${variant.abi}.apk`||!Number.isSafeInteger(variant.bytes)||variant.bytes<1||!/^[a-f0-9]{64}$/i.test(variant.sha256)||new URL(variant.url).protocol!=='https:'||!new URL(variant.url).pathname.endsWith('/'+variant.file))throw new Error('Variante Android invalide.');
    if(variant.fallbackUrl&&(new URL(variant.fallbackUrl).protocol!=='https:'||!new URL(variant.fallbackUrl).pathname.endsWith('/'+variant.file)))throw new Error('Secours Android invalide.');
  }
}
const downloadOrigin=new URL(manifest.offline.url).origin;
const copyModule=await build({entryPoints:[path.join(root,'src/content.ts')],bundle:true,write:false,format:'esm',platform:'node',target:'es2022'});
const {fr}=await import('data:text/javascript;base64,'+Buffer.from(copyModule.outputFiles[0].text).toString('base64'));
let html=await readFile(path.join(root,'src/index.html'),'utf8');
html=html.replace('<!-- landing -->',renderPage(fr,manifest)).replace(/\{\{([^}]+)\}\}/g,(_,key)=>{
 if(!(key in fr))throw new Error('Texte français manquant : '+key);return escape(fr[key]);
});
const canonical='https://michelprincy.github.io/rakoto-player/';
const schema={ '@context':'https://schema.org','@graph':[
 {'@type':'WebSite','@id':canonical+'#website',name:'RAKOTO Player',url:canonical,inLanguage:'fr'},
 {'@type':'SoftwareApplication','@id':canonical+'#windows',name:'RAKOTO Player',url:canonical,description:fr['meta.description'],applicationCategory:'MultimediaApplication',operatingSystem:'Windows 10 / Windows 11 x64',softwareVersion:manifest.version,inLanguage:'fr',downloadUrl:manifest.offline.url,fileSize:manifest.offline.bytes+' bytes',offers:{'@type':'Offer',price:0},featureList:['Lecture audio et vidéo locale','Pistes audio et sous-titres','Bibliothèque et playlists musicales','Sessions vidéo temporaires','Mini-lecteur flottant Windows']},
 ...(manifest.android?[{'@type':'SoftwareApplication','@id':canonical+'#android',name:'RAKOTO Player Android',url:canonical+'#android',description:fr['android.description'],applicationCategory:'MultimediaApplication',operatingSystem:'Android '+manifest.android.minAndroid+' et plus, ARM',softwareVersion:manifest.android.version,inLanguage:'fr',downloadUrl:manifest.android.url,offers:{'@type':'Offer',price:0}}]:[])
]};
html=html.replace('<!-- software-schema -->','<script type="application/ld+json" data-software>'+JSON.stringify(schema).replaceAll('<','\\u003c')+'</script>');
html=html.replace('</head>','<script type="application/json" id="release-data">'+JSON.stringify(manifest).replaceAll('<','\\u003c')+'</script>\n</head>');
html=html.replaceAll('href="/downloads/','href="'+downloadOrigin+'/downloads/')
  .replaceAll('href="/favicon.png"','href="./assets/logo.png"')
  .replaceAll('href="/','href="./').replaceAll('src="/','src="./');
await mkdir(output,{recursive:true});
await writeFile(path.join(output,'index.html'),html);
for(const file of ['robots.txt','sitemap.xml'])await cp(path.join(root,'src',file),path.join(output,file));
await cp(path.join(root,'assets'),path.join(output,'assets'),{recursive:true});
const styles=await Promise.all(['style.css','camp-design.css'].map(file=>readFile(path.join(root,'src',file),'utf8')));
await writeFile(path.join(output,'style.css'),(await transform(styles.join('\n'),{loader:'css',minify:true,target:'es2022'})).code);
await writeFile(path.join(output,'release.json'),JSON.stringify(manifest,null,2)+'\n');
await writeFile(path.join(output,'.nojekyll'),'');
await build({entryPoints:[path.join(root,'src/client.ts')],outfile:path.join(output,'app.js'),bundle:true,minify:true,target:'es2022',format:'iife'});
// Fingerprinted assets prevent an old cached stylesheet from breaking a new page.
for(const file of ['app.js','style.css']){
 const bytes=await readFile(path.join(output,file));
 const hash=createHash('sha256').update(bytes).digest('hex').slice(0,10),extension=path.extname(file);
 const named=path.basename(file,extension)+'.'+hash+extension;
 await writeFile(path.join(output,named),bytes);html=html.replace('./'+file,'./'+named);
}
await writeFile(path.join(output,'index.html'),html);
console.log('Site compilé dans docs/ — compatible avec GitHub Pages et les sous-dossiers.');
