export const navigation = [
  {name:'DÉCOUVRIR', items:[['home','Accueil','home'],['search','Rechercher','search'],['library','Bibliothèque','library']]},
  {name:'VOTRE COLLECTION', items:[['music','Musique','music'],['video','Vidéos','video'],['podcast','Podcasts','mic'],
    ['favorites','Favoris','heart'],['history','Écoutés récemment','history'],['playlists','Playlists','playlist'],
    ['local','Fichiers locaux','folder'],['downloads','Téléchargements','download']]},
  {name:'STUDIO', items:[['eq','Égaliseur','wave'],['settings','Paramètres','settings']]}
];
export const examples = [
  ['Minuit sur l’océan','Nora Estelle','Horizons','Ambient',1],
  ['Au-delà du bleu','Atlas Nord','Haute fréquence','Électronique',2],
  ['Lignes de fuite','Studio Sillage','Haute fréquence','Indie',3],
  ['Éclipse claire','Mila Vey','Première lumière','Instrumental',4],
  ['Côte lointaine','Le Collectif Azur','Horizons','Ambient',5],
  ['Sous la surface','Studio Sillage','','Documentaire',6,'video'],
  ['Voyage orbital','Mila Vey','','Documentaire',7,'video'],
  ['Fréquences lentes','Nora Estelle','Première lumière','Instrumental',8],
  ['Dans le silence','Les Voix du Large','Carnets sonores','Podcast',9,'podcast']
].map(([title,artist,album,genre,index,type='music']) => ({
  id:'demo'+index, title, artist, album, genre, cover:'/art/'+index+'.svg', type,
  ext:type==='video'?'MP4':'FLAC', download:index%3===0
}));

const svgPaths = {
  home:'M3 11 12 3l9 8v9H4v-9 M9 20v-6h6v6', search:'M16 16l5 5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  library:'M4 4v16 M8 4v16 M12 4v16 M16 5l4 14', music:'M9 18V5l11-2v13 M9 18c0 2-5 3-5 0s5-3 5 0 M20 16c0 2-5 3-5 0s5-3 5 0',
  video:'M3 5h18v14H3z M9 9l6 3-6 3z', mic:'M9 5a3 3 0 1 1 6 0v6a3 3 0 0 1-6 0z M5 10v1a7 7 0 0 0 14 0v-1 M12 18v4',
  heart:'M20 5q-4-3-8 1-4-4-8-1t0 8l8 8 8-8q4-5 0-8Z', history:'M3 11a9 9 0 1 1 3 7 M3 5v6h6 M12 7v5l4 3',
  playlist:'M4 6h16 M4 12h11 M4 18h7 M17 14v7 M14 17h7', folder:'M3 6h7l2 3h9v10H3z',
  download:'M12 3v12 M8 11l4 4 4-4 M4 17v4h16v-4', wave:'M3 10v4 M7 6v12 M12 3v18 M17 7v10 M21 10v4',
  settings:'M9 4h6l1 3 3 1v3l2 1-2 1v3l-3 1-1 3H9l-1-3-3-1v-3l-2-1 2-1V8l3-1z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  plus:'M12 5v14 M5 12h14', arrow:'M4 12h15 M14 7l5 5-5 5', back:'M20 12H5 M10 7l-5 5 5 5',
  minus:'M5 12h14', square:'M5 5h14v14H5z', close:'M6 6l12 12 M18 6 6 18',
  moon:'M20 15A8 8 0 0 1 9 4a9 9 0 1 0 11 11Z', sun:'M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l2 2 M17 17l2 2 M5 19l2-2 M17 7l2-2 M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0',
  play:'M8 5l11 7-11 7z', pause:'M8 6h2v12H8z M14 6h2v12h-2z', previous:'M5 5v14 M18 5l-11 7 11 7z',
  next:'M19 5v14 M6 5l11 7-11 7z', shuffle:'M3 6h3c4 0 7 12 11 12h4 M17 14l4 4-4 4 M3 18h3q2 0 4-4 M14 9q2-3 4-3h3 M17 2l4 4-4 4',
  repeat:'M4 10V6h14l-3-3 M18 6l-3 3 M20 14v4H6l3-3 M6 18l3 3', queue:'M3 5h18 M3 10h18 M3 15h11 M3 20h11',
  mini:'M3 5h18v14H3z M12 12h7v5h-7z', expand:'M8 4H4v4 M16 4h4v4 M20 16v4h-4 M8 20H4v-4',
  volume:'M3 9h4l5-4v14l-5-4H3z M16 8q5 4 0 8 M19 5q8 7 0 14', mute:'M3 9h4l5-4v14l-5-4H3z M16 9l6 6 M22 9l-6 6',
  trash:'M4 6h16 M9 6V3h6v3 M6 6l1 15h10l1-15 M10 10v7 M14 10v7',
  caption:'M3 5h18v14H3z M6 11h5 M13 11h5 M6 15h8 M16 15h2',
  grip:'M9 5h.1 M15 5h.1 M9 12h.1 M15 12h.1 M9 19h.1 M15 19h.1',
  info:'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 11v5 M12 7h.1'
};

export function glyph(h,name) {
  return h('svg', { class:'glyph', viewBox:'0 0 24 24', fill:'none', 'aria-hidden':'true' },
    [h('path', {d:svgPaths[name] || svgPaths.info, stroke:'currentColor', 'stroke-width':1.65,
      'stroke-linecap':'round', 'stroke-linejoin':'round',
      fill:['play','pause'].includes(name) ? 'currentColor' : 'none'})]);
}

export function parseCaptions(text, ext) {
  const time = (m,s,part) => Number(m)*60 + Number(s) + Number('0.' + (part || '0'));
  if (ext==='lrc') {
    const entries = [];
    const offset = Number(/\[offset:([+-]?\d+)\]/i.exec(text)?.[1] || 0) / 1000;
    for (const line of text.split(/\r?\n/)) {
      const stamps = [...line.matchAll(/\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g)];
      const label = line.replace(/\[[^\]]*\]/g,'').trim();
      for (const stamp of stamps) entries.push({start:Math.max(0,time(stamp[1],stamp[2],stamp[3])+offset), text:label});
    }
    entries.sort((a,b)=>a.start-b.start);
    return entries.map((entry,i)=>({...entry,end:entries[i+1]?.start ?? Infinity}));
  }
  return text.replace(/\r/g,'').trim().split(/\n[ \t]*\n/).flatMap(block => {
    const match = /(\d+):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d+):(\d{2}):(\d{2})[,.](\d{3})[^\n]*\n([\s\S]+)/.exec(block);
    if (!match) return [];
    return [{ start:Number(match[1])*3600+time(match[2],match[3],match[4]),
      end:Number(match[5])*3600+time(match[6],match[7],match[8]), text:match[9].trim().replace(/<[^>]*>/g,'') }];
  }).filter(entry=>entry.end>entry.start).sort((a,b)=>a.start-b.start);
}
