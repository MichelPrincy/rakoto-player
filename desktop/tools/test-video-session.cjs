const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const {folderSession,persistentState,fileId}=require('../video-session.cjs');

test('le dossier seul, tri naturel, sélection explicite, sans analyse des voisins',async()=>{
 const directory=await fs.mkdtemp(path.join(__dirname,'media-work','session-unit-'));
 for(const file of ['Film 10.MKV','Film 2.mp4','À bientôt.avi','Z.webm','musique.mp3','texte.txt'])await fs.writeFile(path.join(directory,file),'fixture');
 await fs.mkdir(path.join(directory,'sous-dossier'));await fs.writeFile(path.join(directory,'sous-dossier','autre.mkv'),'fixture');
 const selected=path.join(directory,'Z.webm'),described=[];
 const session=await folderSession({path:selected,item:{id:fileId(selected),title:'Z.webm'}},async file=>{described.push(file);return{id:fileId(file),title:path.basename(file)}});
 assert.deepEqual(session.items.map(item=>item.title),['À bientôt.avi','Film 2.mp4','Film 10.MKV','Z.webm']);
 assert.equal(session.selectedId,fileId(selected));assert.equal(described.length,3);assert.equal(session.folderName,path.basename(directory));
});
test('le fichier choisi reste lisible même quand le dossier ne peut pas être parcouru',async()=>{
 const selected=path.join(__dirname,'media-work','dossier-absent','choisi.sans-extension');
 const session=await folderSession({path:selected,item:{id:fileId(selected),title:'choisi'}},()=>{throw new Error('No neighbour expected')});
 assert.equal(session.items.length,1);assert.equal(session.items[0].id,session.selectedId);assert.ok(session.notice);
});
test('migration : aucune trace vidéo sauvegardée ; musique et playlists conservées',()=>{
 const memory={motion:'full',queue:['music','movie'],favorites:['movie','music'],history:['movie','music'],positions:{movie:12,music:45},playlists:[{id:'m',name:'Musique',ids:['music','movie']},{id:'v',name:'Films',ids:['movie']},{id:'empty',name:'À remplir',ids:[]}]};
 const settings={media:{positions:{movie:35,music:20},defaults:{movie:2,music:1},decoder:'auto-copy'}};
 const clean=persistentState(memory,settings,new Set(['movie']));
 assert.deepEqual(clean.memory.queue,['music']);assert.deepEqual(clean.memory.history,['music']);assert.deepEqual(clean.memory.favorites,['music']);assert.deepEqual(clean.memory.positions,{music:45});
 assert.deepEqual(clean.memory.playlists,[{id:'m',name:'Musique',ids:['music']},{id:'empty',name:'À remplir',ids:[]}]);
 assert.deepEqual(clean.settings.media.positions,{music:20});assert.deepEqual(clean.settings.media.defaults,{music:1});assert.equal(clean.settings.media.decoder,'auto-copy');assert.equal(clean.memory.motion,'off');
 assert.equal(memory.playlists[0].ids.length,2,'the migration does not mutate its input');
});
test('liste longue : rendu borné et recherche insensible aux accents',async()=>{
 const source=await fs.readFile(path.join(__dirname,'../ui/video-list.js'),'utf8');
 const {videoWindow,filterVideoItems}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
 const items=Array.from({length:10000},(_,i)=>({title:i===500?'Été.mkv':`Film ${i}.mkv`}));
 const window=videoWindow(items,44*500,600);
 assert.ok(window.rows.length<30);assert.equal(window.before+window.rows.length*44+window.after,440000);
 assert.equal(filterVideoItems(items,'ete')[0].title,'Été.mkv');
});
