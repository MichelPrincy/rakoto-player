interface Artifact {file:string;bytes:number;sha256:string;url:string}
interface AndroidArtifact extends Artifact {version:string;minAndroid:string;abis:string[];fallbackUrl?:string}
interface Release {version:string;platform:string;offline:Artifact;online:Artifact;android?:AndroidArtifact}
const size=(bytes:number)=>bytes<1_000_000?new Intl.NumberFormat('fr-FR',{maximumFractionDigits:0}).format(bytes/1000)+' Ko':new Intl.NumberFormat('fr-FR',{maximumFractionDigits:1}).format(bytes/1_000_000)+' Mo';
function isArtifact(value:Artifact,android=false):boolean{
  try{const pattern=android?/^RAKOTO-Player-Android-\d+\.\d+\.\d+-beta\.\d+\.apk$/:/^RAKOTO-Player-\d+\.\d+\.\d+-(Offline|Online)-x64\.exe$/;const url=new URL(value.url);return pattern.test(value.file)&&Number.isSafeInteger(value.bytes)&&value.bytes>0&&/^[a-f0-9]{64}$/i.test(value.sha256)&&url.protocol==='https:'&&url.pathname.endsWith('/'+value.file);}catch{return false;}
}
function activateDownload(type:string,item:Artifact){
  document.querySelectorAll<HTMLAnchorElement>(`[data-download="${type}"]`).forEach(link=>{link.href=item.url;link.removeAttribute('aria-disabled');link.setAttribute('download',item.file)});
  document.querySelectorAll<HTMLElement>(`[data-size="${type}"]`).forEach(el=>el.textContent=size(item.bytes));
  document.querySelectorAll<HTMLElement>(`[data-hash="${type}"]`).forEach(el=>el.textContent=item.sha256);
}
async function release(){
  try{
    const response=await fetch('./release.json',{cache:'no-cache'});if(!response.ok)throw new Error('Indisponible');
    const info=await response.json() as Release;
    if(!/^\d+\.\d+\.\d+$/.test(info.version)||!isArtifact(info.offline)||!isArtifact(info.online))throw new Error('Version invalide');
    document.querySelectorAll<HTMLElement>('[data-version]').forEach(el=>el.textContent=info.version);
    for(const type of ['offline','online'] as const){
      const item=info[type];
      activateDownload(type,item);
    }
    document.querySelectorAll<HTMLElement>('[data-release-status]').forEach(el=>el.textContent='Version '+info.version+' · Windows 10 / 11 · 64 bits');
    const android=info.android;
    if(android&&isArtifact(android,true)&&/^\d+\.\d+\.\d+-beta\.\d+$/.test(android.version)){
      activateDownload('android',android);
      document.querySelectorAll<HTMLElement>('[data-android-file-size]').forEach(el=>el.textContent=new Intl.NumberFormat('fr-FR').format(android.bytes)+' octets');
      document.querySelectorAll<HTMLElement>('[data-android-binary-size]').forEach(el=>el.textContent=new Intl.NumberFormat('fr-FR',{maximumFractionDigits:1}).format(android.bytes/1048576)+' Mio');
      if(android.fallbackUrl&&isArtifact({...android,url:android.fallbackUrl},true)){
        document.querySelectorAll<HTMLAnchorElement>('[data-android-fallback]').forEach(link=>{link.href=android.fallbackUrl!;link.removeAttribute('aria-disabled');link.setAttribute('download',android.file)});
      }
      document.querySelectorAll<HTMLElement>('[data-android-status]').forEach(el=>el.textContent='Android '+android.minAndroid+' et plus · ARM 32 / 64 bits · '+android.version);
    }else{
      document.querySelectorAll<HTMLElement>('[data-size="android"]').forEach(el=>el.textContent='Indisponible');
      document.querySelectorAll<HTMLElement>('[data-android-status]').forEach(el=>el.textContent='Le téléchargement Android est temporairement indisponible.');
    }
  }catch{
    document.querySelectorAll<HTMLElement>('[data-release-status]').forEach(el=>el.textContent='Les téléchargements sont temporairement indisponibles. Réessayez dans un instant.');
    document.querySelectorAll<HTMLElement>('[data-size]').forEach(el=>el.textContent='Bientôt disponible');
    document.querySelectorAll<HTMLElement>('[data-android-status]').forEach(el=>el.textContent='Le téléchargement Android est temporairement indisponible.');
  }
}
document.querySelectorAll<HTMLAnchorElement>('[data-download]').forEach(link=>link.addEventListener('click',event=>{
  if(link.getAttribute('aria-disabled')==='true'){event.preventDefault();document.getElementById(link.dataset.download==='android'?'android-status':'download-status')?.focus();}
}));
document.querySelector<HTMLButtonElement>('[data-mobile-toggle]')?.addEventListener('click',event=>{
  const button=event.currentTarget as HTMLButtonElement;const expanded=button.getAttribute('aria-expanded')==='true';
  button.setAttribute('aria-expanded',String(!expanded));document.getElementById('navigation')?.classList.toggle('open',!expanded);
});
document.querySelectorAll<HTMLAnchorElement>('#navigation a').forEach(link=>link.addEventListener('click',()=>{
  document.getElementById('navigation')?.classList.remove('open');document.querySelector('[data-mobile-toggle]')?.setAttribute('aria-expanded','false');
}));
document.querySelector<HTMLButtonElement>('[data-demo-toggle]')?.addEventListener('click',event=>{
  const button=event.currentTarget as HTMLButtonElement;const open=button.getAttribute('aria-pressed')!=='true';
  button.setAttribute('aria-pressed',String(open));document.querySelector('.product-scene')?.classList.toggle('show-drawer',open);
  const label=button.querySelector('span');if(label)label.textContent=open?'Revenir à la fenêtre':'Voir le mini-lecteur';
});
document.querySelector<HTMLButtonElement>('[data-copy-hashes]')?.addEventListener('click',async event=>{
  const button=event.currentTarget as HTMLButtonElement;
  try{await navigator.clipboard.writeText(Array.from(document.querySelectorAll<HTMLElement>('[data-hash]')).map(el=>el.dataset.hash+': '+el.textContent).join('\n'));button.textContent='Empreintes copiées';}
  catch{button.textContent='Sélectionnez les empreintes ci-dessous';}
});
void release();
