import {readFile} from 'node:fs/promises';
import {setDefaultResultOrder} from 'node:dns';
setDefaultResultOrder('ipv4first');
const release=JSON.parse(await readFile(new URL('../public/release.json',import.meta.url),'utf8'));
const items=[release.offline,release.online,release.android,...release.android.variants];
const results=await Promise.all(items.map(async item=>{
 const response=await fetch(item.url,{method:'HEAD',signal:AbortSignal.timeout(45000)});
 const bytes=Number(response.headers.get('content-length'));
 if(!response.ok||bytes!==item.bytes)throw new Error(item.file+' : '+response.status+', '+bytes+' / '+item.bytes);
 return {file:item.file,status:response.status,bytes};
}));
console.log(JSON.stringify(results,null,2));
