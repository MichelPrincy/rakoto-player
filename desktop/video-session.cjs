const fs=require('node:fs/promises');
const path=require('node:path');
const {createHash,randomUUID}=require('node:crypto');
const {types}=require('./media-probe.cjs');
const videoExtensions=new Set(Object.entries(types).filter(([,mime])=>mime.startsWith('video/')).map(([ext])=>ext));
const collator=new Intl.Collator('fr',{numeric:true,sensitivity:'base'});
const fileId=file=>createHash('sha256').update(path.normalize(file).toLowerCase()).digest('hex').slice(0,32);
const videoPath=file=>videoExtensions.has(path.extname(file).slice(1).toLowerCase());

async function folderSession(selected,describe){
 const folder=path.dirname(selected.path);let entries=[],notice='';
 try{entries=await fs.readdir(folder,{withFileTypes:true})}
 catch{notice='Le dossier n’est pas accessible ; seul le fichier choisi est disponible.'}
 const paths=entries.filter(entry=>entry.isFile()&&videoPath(entry.name)).map(entry=>path.join(folder,entry.name));
 if(!paths.some(file=>fileId(file)===selected.item.id))paths.push(selected.path);
 paths.sort((a,b)=>collator.compare(path.basename(a),path.basename(b))||a.localeCompare(b));
 const items=[];
 for(const file of paths){const item=fileId(file)===selected.item.id?selected.item:await describe(file);if(item)items.push(item)}
 return {id:randomUUID(),folderName:path.basename(folder)||folder,items,selectedId:selected.item.id,notice};
}

function persistentState(memory,settings,ephemeral){
 const ids=values=>Array.isArray(values)?values.filter(id=>typeof id==='string'&&!ephemeral.has(id)):[];
 const records=values=>Object.fromEntries(Object.entries(values||{}).filter(([id])=>!ephemeral.has(id)));
 const music={...memory,motion:'off',queue:ids(memory.queue),favorites:ids(memory.favorites),history:ids(memory.history),positions:records(memory.positions)};
 music.playlists=(Array.isArray(memory.playlists)?memory.playlists:[]).flatMap(list=>{
  const original=Array.isArray(list.ids)?list.ids:[],filtered=ids(original);
  // Keep intentionally empty music playlists; discard old video-only playlists.
  return original.length&&!filtered.length&&original.every(id=>ephemeral.has(id))?[]:[{...list,ids:filtered}];
 });
 const preferences={...settings};
 if(settings.media)preferences.media={...settings.media,positions:records(settings.media.positions),defaults:records(settings.media.defaults)};
 return {memory:music,settings:preferences};
}
module.exports={fileId,videoPath,folderSession,persistentState};
