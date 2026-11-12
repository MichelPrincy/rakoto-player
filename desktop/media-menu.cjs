const { fonts }=require('./native-player.cjs');
const names = {fra:'fr',fre:'fr',eng:'en',jpn:'ja',mlg:'mg',deu:'de',ger:'de',spa:'es',ita:'it',
  por:'pt',zho:'zh',chi:'zh',kor:'ko',ara:'ar',rus:'ru',hin:'hi',nld:'nl',dut:'nl'};
const languageNames = new Intl.DisplayNames(['fr'],{type:'language',fallback:'none'});
function language(code) {
  if (!code||code==='und') return 'Langue non renseignée';
  try {
    const name=languageNames.of(names[code.toLowerCase()]||code);
    return name?name[0].toUpperCase()+name.slice(1):code;
  } catch {return code;}
}

const channels = {1:'1.0',2:'2.0',3:'3.0',4:'4.0',5:'5.0',6:'5.1',8:'7.1'};
function trackText(track,report) {
  const entries=track.type==='audio'?report?.audio:report?.subtitles;
  const metadata=track.external?null:entries?.find(item=>item.index!==null&&item.index===track['ff-index'])||
    entries?.find(item=>item.index===null&&item.ordinal===Number(track.id)-1);
  const codec=String(metadata?.codec||track.codec||'').toUpperCase();
  const layout=metadata?.layout || channels[track['demux-channel-count']] || '';
  const label=language(track.lang || metadata?.language);
  const title=track.title || metadata?.title;
  return [label,title,codec,layout,track.external?'Externe':''].filter(Boolean).join(' · ').slice(0,180);
}
function buildMenu(ctx) {
  const {kind,current,report,native,props,flips}=ctx;
  const call=(label,fn,enabled=true,extras={})=>({label,enabled,...extras,
    click:()=>Promise.resolve().then(fn).catch(ctx.error)});
  const group=(label,submenu,enabled=true)=>({label,submenu,enabled});
  const sep=()=>({type:'separator'});
  const radio=(label,selected,fn,enabled)=>call(label,fn,enabled,{type:'radio',checked:Boolean(selected)});
  const check=(label,selected,fn,enabled)=>call(label,fn,enabled,{type:'checkbox',checked:Boolean(selected)});
  const list=props['track-list']||[];
  const video=Boolean(native&&report?.video.length);
  const speeds=[.25,.5,.75,1,1.25,1.5,2].map(speed=>radio(speed.toLocaleString('fr')+'×',
    (props.speed||current.speed)===speed,()=>ctx.value('speed',speed)));
  const select=(type,key)=>[
    ...list.filter(track=>track.type===type).map(track=>radio(trackText(track,report),track.selected,
      ()=>ctx.set(key,track.id),native)),
    radio('Désactivé',!list.some(track=>track.type===type&&track.selected),()=>ctx.set(key,'no'),native),
    call('Automatique',()=>ctx.set(key,'auto'),native)
  ];
  const delay=(label,key)=>group(label,[
    {label:'Décalage actuel '+(Number(props[key])||0).toFixed(2)+' s',enabled:false},sep(),
    call('Avancer de 500 ms',()=>ctx.delay(key,-.5),native),
    call('Avancer de 100 ms',()=>ctx.delay(key,-.1),native),
    call('Retarder de 100 ms',()=>ctx.delay(key,.1),native),
    call('Retarder de 500 ms',()=>ctx.delay(key,.5),native),
    call('Remettre à zéro',()=>ctx.set(key,0),native)
  ],native);
  const volume=group('Volume',[0,25,50,75,100].map(percent=>radio(percent+' %',Math.round(current.volume*100)===percent,
    ()=>ctx.value('volume',percent/100))));
  const audio=[
    group('Pistes audio',select('audio','aid'),native),
    call('Mémoriser la piste active pour ce fichier',ctx.defaultTrack,native&&Number.isInteger(props.aid)&&ctx.defaultAudio!==props.aid),
    call('Effacer la piste mémorisée',ctx.clearDefault,Number.isInteger(ctx.defaultAudio)),
    sep(),volume,check('Normalisation du volume',ctx.normalize,ctx.normalizeAudio,native),
    delay('Synchronisation audio','audio-delay'),
    group('Canaux audio',['auto','mono','stereo','5.1','7.1'].map(value=>radio(
      {auto:'Automatique',mono:'Mono',stereo:'Stéréo','5.1':'5.1','7.1':'7.1'}[value],props['audio-channels']===value,
      ()=>ctx.set('audio-channels',value),native)),native),
    ...(report?.native===false?[group('Moteur audio',[
      radio('Web Audio · fondu et visualisation',!native,()=>ctx.value('audioEngine','web')),
      radio('mpv · réglages avancés',native,()=>ctx.value('audioEngine','native'))
    ])]:[]),
    call('Égaliseur',()=>ctx.action('equalizer')),
    check('Couper le son',current.muted,()=>ctx.action('mute'))
  ];
  const subtitle=[
    ...select('sub','sid'),
    sep(),call('Charger un fichier externe…',ctx.subtitle,native),delay('Synchronisation','sub-delay'),
    group('Style et apparence',[
      check('Respecter les styles ASS / SSA',props['sub-ass-override']!=='force',
        ()=>ctx.set('sub-ass-override',props['sub-ass-override']==='force'?'no':'force'),native),
      group('Police',fonts.map(font=>radio(font,props['sub-font']===font,async()=>{
        await ctx.set('sub-ass-override','force');await ctx.set('sub-font',font);
      },native))),
      group('Taille',[24,32,42,55,72,90].map(size=>radio(size+' px',props['sub-font-size']===size,async()=>{
        await ctx.set('sub-ass-override','force');await ctx.set('sub-font-size',size);
      },native))),
      group('Couleur',[['Blanc','#FFFFFF'],['Jaune','#FDE047'],['Bleu clair','#7DD3FC'],['Vert clair','#86EFAC']]
        .map(([label,color])=>call(label,async()=>{
          await ctx.set('sub-ass-override','force');await ctx.set('sub-color',color);
        },native))),
      group('Position',[['Haut',15],['Centre',50],['Bas',92],['Tout en bas',100]].map(([label,position])=>
        radio(label,props['sub-pos']===position,async()=>{
          await ctx.set('sub-ass-override','force');await ctx.set('sub-pos',position);
        },native)))
    ],native)
  ];
  const aspect=[
    ...[['Auto',-1],['16:9',16/9],['4:3',4/3],['21:9',21/9]].map(([label,ratio])=>radio(label,
      Math.abs((props['video-aspect-override']??-1)-ratio)<.01,()=>ctx.set('video-aspect-override',ratio),video)),
    call('Personnalisé…',ctx.aspect,video)
  ];
  const size=[50,75,100,150,200].map(percent=>radio(percent+' %',Math.abs((props['video-zoom']||0)-Math.log2(percent/100))<.01,
    ()=>ctx.set('video-zoom',Math.log2(percent/100)),video));
  const movie=[
    group('Format d’image',aspect,video),group('Taille de l’image',size,video),
    group('Rotation',[0,90,180,270].map(degrees=>radio(degrees+'°',(props['video-rotate']||0)===degrees,
      ()=>ctx.set('video-rotate',degrees),video)),video),
    check('Retourner horizontalement',flips.h,()=>ctx.flip('h'),video),
    check('Retourner verticalement',flips.v,()=>ctx.flip('v'),video),
    check('Désentrelacement',props.deinterlace,()=>ctx.set('deinterlace',!props.deinterlace),video),
    group('Décodage vidéo',[
      radio('Matériel compatible (copie)',ctx.decoder==='auto-copy',()=>ctx.setDecoder('auto-copy'),video),
      radio('Logiciel',ctx.decoder==='no',()=>ctx.setDecoder('no'),video)
    ],video),
    sep(),call('Vidéo flottante',ctx.float,video),call('Capture d’image…',ctx.capture,video),
    call('Informations techniques…',ctx.info,Boolean(report))
  ];
  const pause=()=>call(current.playing?'Mettre en pause':'Lire',()=>ctx.action('toggle'));
  const playback=[
    pause(),call('Arrêter',()=>ctx.action('stop')),sep(),
    group('Répéter',[['Désactivé','off'],['File d’attente','all'],['Média','one']].map(([label,id])=>
      radio(label,current.repeat===id,()=>ctx.value('repeat',id)))),
    check('Lecture aléatoire',current.shuffle,()=>ctx.action('shuffle')),
    group('Image par image',[call('Image suivante',()=>ctx.step('step'),video),
      call('Image précédente',()=>ctx.step('backstep'),video)],video),
    group('Avancer / Reculer',[-30,-5,5,30].map(seconds=>call((seconds>0?'+':'')+seconds+' s',
      ()=>ctx.value('seek',Math.max(0,current.time+seconds))))),
    group('Vitesse',speeds),check('Reprendre à la dernière position',current.resume,()=>ctx.action('resume')),
    ...(report?.chapters.length?[group('Chapitres',report.chapters.map(chapter=>call(chapter.title,()=>ctx.chapter(chapter.time))))]:[])
  ];
  const settings=[
    check('Molette en plein écran : volume',ctx.wheel,ctx.setWheel),
    call('Paramètres RAKOTO',()=>ctx.action('settings'))
  ];
  const quick={audio,subtitle,video:movie,speed:speeds};
  if(Object.hasOwn(quick,kind))return quick[kind];
  return [
    pause(),call('Arrêter',()=>ctx.action('stop')),
    call('Précédent',()=>ctx.action('previous')),call('Suivant',()=>ctx.action('next')),sep(),
    group('Audio',audio),group('Sous-titres',subtitle,native),group('Vidéo',movie,video),
    group('Lecture',playback),group('Vitesse de lecture',speeds),
    group('Taille de l’image',size,video),group('Format d’image',aspect,video),sep(),
    call(ctx.fullscreen?'Quitter le plein écran':'Plein écran',ctx.full),call('Capture d’image…',ctx.capture,video),
    call('Informations média…',ctx.info,Boolean(report)),group('Paramètres',settings)
  ];
}
module.exports={buildMenu,language,trackText};


