import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,stat} from 'node:fs/promises';
import {build} from 'esbuild';
import {gzipSync} from 'node:zlib';
import {fileURLToPath} from 'node:url';
const html=await readFile(new URL('../docs/index.html',import.meta.url),'utf8');
const manifest=JSON.parse(await readFile(new URL('../public/release.json',import.meta.url),'utf8'));
const module=await build({entryPoints:[fileURLToPath(new URL('../src/content.ts',import.meta.url))],bundle:true,format:'esm',platform:'node',write:false});
const {fr,mg}=await import('data:text/javascript;base64,'+Buffer.from(module.outputFiles[0].text).toString('base64'));

test('une seule page, traductions complètes et ancres cohérentes',()=>{
 assert.equal([...html.matchAll(/<h1\b/g)].length,1);
 assert.deepEqual(Object.keys(mg).sort(),Object.keys(fr).sort());
 for(const match of html.matchAll(/data-i18n(?:-label|-alt|-title)?="([^"]+)"/g))assert.ok(fr[match[1]]&&mg[match[1]],match[1]);
 for(const match of html.matchAll(/href="#([^"]+)"/g))assert.ok(html.includes(`id="${match[1]}"`),match[1]);
 assert.ok(!html.includes('{{')&&!html.includes('undefined'));
});
test('SEO statique et versions réelles, sans avis ni disponibilité inventés',()=>{
 assert.match(html,/<link rel="canonical" href="https:\/\/michelprincy.github.io\/rakoto-player\/">/);
 const graph=JSON.parse(html.match(/<script type="application\/ld\+json" data-software>(.*?)<\/script>/s)[1])['@graph'];
 const windows=graph.find(item=>item['@id'].endsWith('#windows')),android=graph.find(item=>item['@id'].endsWith('#android'));
 assert.equal(windows.softwareVersion,manifest.version);assert.equal(windows.downloadUrl,manifest.offline.url);assert.equal(windows.offers.price,0);
 assert.equal(android.softwareVersion,manifest.android.version);assert.equal(android.downloadUrl,manifest.android.url);
 assert.ok(!graph.some(item=>item.aggregateRating||item.review||/iOS/.test(item.operatingSystem||'')));
 assert.ok(!html.includes('apps.apple.com'));
 for(const token of ['og:title','og:description','og:image','twitter:card','twitter:title','twitter:description'])assert.ok(html.includes(token),token);
});
test('ressources optimisées, métadonnées de partage et indexation dans le sous-dossier',async()=>{
 for(const image of ['share.jpg','rakoto-hero-malagasy.webp','rakoto-hero-malagasy-small.webp','fonts/fraunces-regular.woff2','fonts/fraunces-italic.woff2','fonts/Fraunces-OFL.txt','screens/windows.webp','screens/equalizer.webp','screens/drawer.webp'])assert.ok((await stat(new URL('../docs/assets/'+image,import.meta.url))).size>0,image);
 assert.match(await readFile(new URL('../docs/sitemap.xml',import.meta.url),'utf8'),/<loc>https:\/\/michelprincy.github.io\/rakoto-player\/<\/loc>/);
 assert.match(await readFile(new URL('../docs/robots.txt',import.meta.url),'utf8'),/Sitemap: https:\/\/michelprincy.github.io\/rakoto-player\/sitemap.xml/);
 for(const file of ['app.js','style.css'])assert.ok(gzipSync(await readFile(new URL('../docs/'+file,import.meta.url))).length<20000,file+' gzip budget');
});
