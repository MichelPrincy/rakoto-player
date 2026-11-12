import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,stat} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const manifest=JSON.parse(await readFile(path.join(root,'docs/release.json'),'utf8'));
test('toutes les ressources fonctionnent à la racine et sous /rakoto-player/',async()=>{
 const html=await readFile(path.join(root,'docs/index.html'),'utf8');
 assert.match(html,/<html lang="fr">/);
 for(const match of html.matchAll(/(?:href|src)="(\.\/[^"]+)"/g)){
  const relative=match[1].slice(2);assert.ok((await stat(path.join(root,'docs',relative))).isFile(),relative);
  const url=new URL(match[1],'https://michelprincy.github.io/rakoto-player/');assert.ok(url.pathname.startsWith('/rakoto-player/'));
 }
 assert.doesNotMatch(html,/(?:href|src)="\/(?!\/)/);
 assert.match(html,/data-download="offline"/);assert.match(html,/data-download="online"/);
 assert.match(html,/data-download="android"/);
 assert.ok((await stat(path.join(root,'docs/.nojekyll'))).isFile());
});
test('le client compilé active les téléchargements avec les vraies URL et tailles',async()=>{
 const nodes=new Map();
 nodes.set('[data-android-fallback]',[{href:'',attributes:{'aria-disabled':'true'},removeAttribute(name){delete this.attributes[name]},setAttribute(name,value){this.attributes[name]=value}}]);
 nodes.set('[data-android-file-size]',[{textContent:''}]);nodes.set('[data-android-binary-size]',[{textContent:''}]);
 for(const type of ['offline','online','android']){
  nodes.set(`[data-download="${type}"]`,[{href:'',attributes:{'aria-disabled':'true'},removeAttribute(name){delete this.attributes[name]},setAttribute(name,value){this.attributes[name]=value},addEventListener(){}}]);
  nodes.set(`[data-size="${type}"]`,[{textContent:''}]);
 }
 let requested;
 const context=vm.createContext({URL,Intl,document:{querySelectorAll:selector=>nodes.get(selector)||[],querySelector:()=>null},
  fetch:async url=>{requested=new URL(url,'https://michelprincy.github.io/rakoto-player/').href;return {ok:true,json:async()=>manifest}}});
 vm.runInContext(await readFile(path.join(root,'docs/app.js'),'utf8'),context);
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(requested,'https://michelprincy.github.io/rakoto-player/release.json');
 for(const type of ['offline','online','android']){
  const link=nodes.get(`[data-download="${type}"]`)[0];assert.equal(link.href,manifest[type].url);
  assert.equal(link.attributes['aria-disabled'],undefined);
  assert.match(nodes.get(`[data-size="${type}"]`)[0].textContent,/(Mo|Ko)/);
 }
 assert.equal(nodes.get('[data-android-fallback]')[0].href,manifest.android.fallbackUrl);
 assert.equal(nodes.get('[data-android-fallback]')[0].attributes['aria-disabled'],undefined);
 assert.equal(nodes.get('[data-android-file-size]')[0].textContent.replace(/\D/g,''),String(manifest.android.bytes));
 assert.match(nodes.get('[data-android-binary-size]')[0].textContent,/Mio/);
});

test('une URL Android invalide reste désactivée sans bloquer Windows',async()=>{
 const nodes=new Map();
 for(const type of ['offline','online','android'])nodes.set(`[data-download="${type}"]`,[{href:'',attributes:{'aria-disabled':'true'},removeAttribute(name){delete this.attributes[name]},setAttribute(name,value){this.attributes[name]=value},addEventListener(){}}]);
 const invalid=structuredClone(manifest);invalid.android.url='javascript:alert(1)';
 const context=vm.createContext({URL,Intl,document:{querySelectorAll:selector=>nodes.get(selector)||[],querySelector:()=>null},fetch:async()=>({ok:true,json:async()=>invalid})});
 vm.runInContext(await readFile(path.join(root,'docs/app.js'),'utf8'),context);await new Promise(resolve=>setImmediate(resolve));
 assert.equal(nodes.get('[data-download="android"]')[0].attributes['aria-disabled'],'true');
 assert.equal(nodes.get('[data-download="offline"]')[0].href,manifest.offline.url);
 assert.equal(nodes.get('[data-download="online"]')[0].href,manifest.online.url);
});
