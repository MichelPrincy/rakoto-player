export const videoRowHeight=44;
export function filterVideoItems(items,query=''){
 const fold=value=>String(value||'').normalize('NFD').replace(/\p{M}/gu,'').toLowerCase();
 const term=fold(query).trim();return term?items.filter(item=>fold(item.fileName||item.title).includes(term)):items;
}
export function videoWindow(items,scrollTop=0,height=600){
 const start=Math.min(items.length,Math.max(0,Math.floor(scrollTop/videoRowHeight)-5));
 const end=Math.min(items.length,start+Math.ceil(height/videoRowHeight)+10);
 return {rows:items.slice(start,end),before:start*videoRowHeight,after:Math.max(0,(items.length-end)*videoRowHeight)};
}
