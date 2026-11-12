import {applyLanguage,language,stored,text,type CopyKey,type Language} from './content';
import {initExperience} from './experience';
interface Artifact {file:string;bytes:number;sha256:string;url:string}
interface AndroidVariant extends Artifact {abi:'arm64-v8a'|'armeabi-v7a';fallbackUrl?:string}
interface AndroidArtifact extends Artifact {version:string;minAndroid:string;abis:string[];fallbackUrl?:string;variants?:AndroidVariant[]}
interface Release {version:string;platform:string;offline:Artifact;online:Artifact;android?:AndroidArtifact}
let current:Release|null=null,currentAndroid:AndroidArtifact|null=null,detected:string|undefined,selectBound=false;
const size=(bytes:number)=>new Intl.NumberFormat(language()==='mg'?'mg-MG':'fr-FR',{maximumFractionDigits:1}).format(bytes/(bytes<1_000_000?1000:1_000_000))+(bytes<1_000_000?' Ko':' Mo');
function isArtifact(value:Artifact,android=false):boolean{
 try{const pattern=android?/^RAKOTO-Player-Android-\d+\.\d+\.\d+(?:-beta\.\d+)?(?:-arm64-v8a|-armeabi-v7a)?\.apk$/:/^RAKOTO-Player-\d+\.\d+\.\d+-(Offline|Online)-x64\.exe$/;const url=new URL(value.url);return pattern.test(value.file)&&Number.isSafeInteger(value.bytes)&&value.bytes>0&&/^[a-f0-9]{64}$/i.test(value.sha256)&&url.protocol==='https:'&&url.pathname.endsWith('/'+value.file)}catch{return false}
}
function activateDownload(type:string,item:Artifact){
 document.querySelectorAll<HTMLAnchorElement>(`[data-download="${type}"]`).forEach(link=>{link.href=item.url;link.removeAttribute('aria-disabled');link.setAttribute('download',item.file)});
 document.querySelectorAll<HTMLElement>(`[data-size="${type}"]`).forEach(el=>el.textContent=size(item.bytes));
 document.querySelectorAll<HTMLElement>(`[data-hash="${type}"]`).forEach(el=>el.textContent=item.sha256);
}
function androidDownloads(android:AndroidArtifact){
 currentAndroid=android;
 const select=document.querySelector<HTMLSelectElement>('[data-android-variant]');
 const display=()=>{
  const info=currentAndroid;if(!info)return;
  const variants=(info.variants||[]).filter(item=>['arm64-v8a','armeabi-v7a'].includes(item.abi)&&item.file===`RAKOTO-Player-Android-${info.version}-${item.abi}.apk`&&isArtifact(item,true));
  const abi=select?.value==='auto'?detected:select?.value;
  const item=variants.find(item=>item.abi===abi)||info;activateDownload('android',item);
  document.querySelectorAll<HTMLElement>('[data-android-file-size]').forEach(el=>el.textContent=new Intl.NumberFormat('fr-FR').format(item.bytes)+' octets');
  document.querySelectorAll<HTMLElement>('[data-android-binary-size]').forEach(el=>el.textContent=new Intl.NumberFormat('fr-FR',{maximumFractionDigits:1}).format(item.bytes/1048576)+' Mio');
  if(item.fallbackUrl&&isArtifact({...item,url:item.fallbackUrl},true))document.querySelectorAll<HTMLAnchorElement>('[data-android-fallback]').forEach(link=>{link.href=item.fallbackUrl!;link.removeAttribute('aria-disabled');link.setAttribute('download',item.file)});
  const key:CopyKey='abi' in item?(item.abi==='arm64-v8a'?'android.arm64':'android.arm32'):'android.universal';
  document.querySelectorAll<HTMLElement>('[data-android-status]').forEach(el=>el.textContent=`Android ${info.minAndroid}+ · ${text(key)} · ${info.version}`);
  document.querySelectorAll<HTMLElement>('[data-android-availability]').forEach(el=>{el.textContent=text('status.android');el.dataset.i18n='status.android'});
 };
 display();
 if(select){select.disabled=false;if(!selectBound){select.addEventListener('change',display);selectBound=true}}
 const hints=typeof navigator==='undefined'?undefined:(navigator as Navigator&{userAgentData?:{getHighEntropyValues?:(hints:string[])=>Promise<{architecture?:string;bitness?:string;platform?:string}>}}).userAgentData;
 if(hints?.getHighEntropyValues&&!detected)void hints.getHighEntropyValues(['architecture','bitness','platform']).then(info=>{
  if(info.platform==='Android'&&info.architecture==='arm'&&['32','64'].includes(info.bitness||''))detected=info.bitness==='64'?'arm64-v8a':'armeabi-v7a';
  if(select?.value==='auto')display();
 }).catch(()=>{});
}
function renderRelease(info:Release){
 current=info;
 document.querySelectorAll<HTMLElement>('[data-version]').forEach(el=>el.textContent=info.version);
 for(const type of ['offline','online'] as const)activateDownload(type,info[type]);
 document.querySelectorAll<HTMLElement>('[data-release-status]').forEach(el=>el.textContent=`Windows 10 / 11 · x64 · ${info.version}`);
 document.querySelectorAll<HTMLAnchorElement>('[data-release-notes]').forEach(el=>el.href=`https://github.com/MichelPrincy/rakoto-player/releases/tag/windows-v${info.version}`);
 if(info.android&&isArtifact(info.android,true)&&/^\d+\.\d+\.\d+(?:-beta\.\d+)?$/.test(info.android.version))androidDownloads(info.android);
 else{
  currentAndroid=null;
  document.querySelectorAll<HTMLAnchorElement>('[data-download="android"],[data-android-fallback]').forEach(el=>el.setAttribute('aria-disabled','true'));
  document.querySelectorAll<HTMLElement>('[data-android-status],[data-android-availability]').forEach(el=>{el.textContent=text('status.unavailable');if(el.dataset)el.dataset.i18n='status.unavailable'});
 }
}
function validRelease(info:Release):boolean{return /^\d+\.\d+\.\d+$/.test(info?.version)&&isArtifact(info.offline)&&isArtifact(info.online)}
async function release(){
 try{const embedded=document.querySelector('#release-data');if(embedded?.textContent){const info=JSON.parse(embedded.textContent) as Release;if(validRelease(info))renderRelease(info)}}catch{/* The fresh manifest remains the source of truth. */}
 try{
  const response=await fetch('./release.json',{cache:'no-cache'});if(!response.ok)throw new Error('Unavailable');
  const info=await response.json() as Release;if(!validRelease(info))throw new Error('Invalid release');renderRelease(info);
 }catch{
  // The prerendered release remains usable if a refresh is blocked/offline.
  if(!current)document.querySelectorAll<HTMLElement>('[data-release-status]').forEach(el=>el.textContent=text('download.error'));
 }
}

document.querySelectorAll<HTMLAnchorElement>('[data-download]').forEach(link=>link.addEventListener('click',event=>{
 if(link.getAttribute('aria-disabled')==='true'){event.preventDefault();document.getElementById(link.dataset.download==='android'?'android-status':'download-status')?.focus()}
}));
const closeMenu=()=>{document.getElementById('navigation')?.classList.remove('open');const toggle=document.querySelector('[data-mobile-toggle]');toggle?.setAttribute('aria-expanded','false');toggle?.setAttribute('aria-label',text('nav.menu'))};
document.querySelector<HTMLButtonElement>('[data-mobile-toggle]')?.addEventListener('click',event=>{
 const button=event.currentTarget as HTMLButtonElement,open=button.getAttribute('aria-expanded')!=='true';button.setAttribute('aria-expanded',String(open));button.setAttribute('aria-label',text(open?'nav.close':'nav.menu'));document.getElementById('navigation')?.classList.toggle('open',open);
});
document.querySelectorAll<HTMLAnchorElement>('#navigation a').forEach(link=>link.addEventListener('click',closeMenu));
document.querySelector<HTMLButtonElement>('[data-copy-hashes]')?.addEventListener('click',async event=>{
 const button=event.currentTarget as HTMLButtonElement;
 try{await navigator.clipboard.writeText(Array.from(document.querySelectorAll<HTMLElement>('[data-hash]')).map(el=>el.dataset.hash+': '+el.textContent).join('\n'));button.textContent=text('download.copied')}
 catch{button.textContent=text('download.select')}
});
if(typeof window!=='undefined'&&document.documentElement){
 const requested=new URL(location.href).searchParams.get('lang');
 const preferred=requested==='fr'||requested==='mg'?requested:stored('rakoto-language');
 applyLanguage(preferred==='mg'?'mg':'fr',false);
 document.querySelectorAll<HTMLButtonElement>('[data-language]').forEach(button=>button.addEventListener('click',()=>{
  const lang:Language=button.dataset.language==='mg'?'mg':'fr';applyLanguage(lang);closeMenu();
  const url=new URL(location.href);if(lang==='mg')url.searchParams.set('lang','mg');else url.searchParams.delete('lang');history.replaceState(null,'',url.pathname+url.search+url.hash);
 }));
 document.addEventListener('rakoto:language',()=>{if(current)renderRelease(current)});
 document.addEventListener('keydown',event=>{if(event.key==='Escape')closeMenu()});
 document.querySelectorAll('[data-year]').forEach(el=>el.textContent=String(new Date().getFullYear()));
 const dialog=document.querySelector<HTMLDialogElement>('#capture-dialog');let lastCapture:HTMLElement|null=null;
 const captionKeys:Record<string,CopyKey>={windows:'capture.windows',equalizer:'capture.eq',drawer:'capture.drawer'};
 document.querySelectorAll<HTMLButtonElement>('[data-capture]').forEach(button=>button.addEventListener('click',()=>{
  if(!dialog)return;const name=button.dataset.capture||'windows';if(!(name in captionKeys))return;
  const image=dialog.querySelector('img')!;image.src=new URL('./assets/screens/'+name+'.webp',location.href).href;image.alt=text(captionKeys[name]);
  const caption=dialog.querySelector<HTMLElement>('[data-capture-caption]');if(caption){caption.dataset.i18n=captionKeys[name];caption.textContent=text(captionKeys[name])}
  lastCapture=button;dialog.showModal();
 }));
 const closeCapture=()=>{dialog?.close();lastCapture?.focus()};
 document.querySelector('[data-close-capture]')?.addEventListener('click',closeCapture);
 dialog?.addEventListener('click',event=>{if(event.target===dialog){const box=dialog.getBoundingClientRect();if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)closeCapture()}});
 document.querySelector('[data-share]')?.addEventListener('click',async()=>{
  const url=new URL('https://michelprincy.github.io/rakoto-player/');if(language()==='mg')url.searchParams.set('lang','mg');
  const announcement=document.querySelector('#announcement');
  try{
   if(navigator.share)await navigator.share({title:'RAKOTO Player',text:text('footer.slogan'),url:url.href});
   else{await navigator.clipboard.writeText(url.href);if(announcement)announcement.textContent=text('footer.copied')}
  }catch(error){if((error as Error).name!=='AbortError'&&announcement)announcement.textContent=text('footer.share.error')}
 });
 initExperience();
}
void release();
