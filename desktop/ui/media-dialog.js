const root=document.getElementById('details');
const fmt=new Intl.NumberFormat('fr-FR',{maximumFractionDigits:2});
const labels=new Intl.DisplayNames(['fr'],{type:'language'});
const aliases={fra:'fr',fre:'fr',eng:'en',jpn:'ja',mlg:'mg',deu:'de',ger:'de',spa:'es'};
function node(tag,text,parent=root){const element=document.createElement(tag);if(text)element.textContent=text;parent.append(element);return element;}
function language(code){if(!code||code==='und')return 'Langue non renseignée';try{return labels.of(aliases[code]||code);}catch{return code;}}
function show({kind,report,theme,value,hardware,output}) {
  root.replaceChildren();
  document.body.classList.toggle('dark',theme==='dark');
  if(kind==='aspect'){
    node('h1','Format d’image personnalisé');
    node('p','Saisissez le rapport largeur / hauteur entre 0,5 et 4.');
    const form=node('form'),label=node('label','Rapport',form),input=node('input','',label);
    input.type='number';input.min='.5';input.max='4';input.step='any';input.required=true;
    input.value=value>0?value:1.7778;node('button','Appliquer',form);
    form.addEventListener('submit',event=>{event.preventDefault();window.desktop.mediaAspect(input.valueAsNumber);});
    input.focus();return;
  }
  if(!report)return;
  const video=report.video[0];
  node('h1','Informations média');
  node('p',report.filename);
  const summary=node('dl');
  const row=(label,data)=>{node('dt',label,summary);node('dd',data||'Non renseigné',summary);};
  row('Conteneur',report.container);
  row('Durée',report.duration&&fmt.format(report.duration)+' s');
  row('Taille du fichier',report.size&&fmt.format(report.size/1048576)+' Mio');
  row('Débit total',report.bitrate&&fmt.format(report.bitrate/1000)+' kbit/s');
  if(video){
    row('Codec vidéo',video.codecName||video.codec);
    row('Résolution',video.width&&video.height&&video.width+' × '+video.height);
    row('Cadence',video.fps&&fmt.format(video.fps)+' images/s');
    row('Débit vidéo',video.bitrate&&fmt.format(video.bitrate/1000)+' kbit/s');
    row('Sortie / décodage',[output,hardware&&hardware!=='no'?hardware:output?'logiciel':''].filter(Boolean).join(' / '));
  }
  const tracks=(title,list)=>{
    node('h2',title+' · '+list.length);
    if(!list.length){node('p','Aucune piste');return;}
    const ul=node('ul');
    for(const track of list)node('li',[language(track.language),track.title,track.codec.toUpperCase(),
      track.layout|| (track.channels?track.channels+' canaux':''),track.sampleRate&&fmt.format(track.sampleRate)+' Hz',
      track.default?'par défaut':'',track.forced?'forcée':''].filter(Boolean).join(' · '),ul);
  };
  tracks('Audio',report.audio);
  tracks('Sous-titres intégrés',report.subtitles);
  if(report.chapters.length){node('h2','Chapitres · '+report.chapters.length);const ul=node('ul');
    for(const chapter of report.chapters)node('li',fmt.format(chapter.time)+' s · '+chapter.title,ul);}
}
window.desktop.onMediaDetails(show);
