interface Artifact {file:string;bytes:number;sha256:string;url:string}
interface Release {version:string;platform:string;offline:Artifact;online:Artifact}
const size=(bytes:number)=>bytes<1_000_000?new Intl.NumberFormat('fr-FR',{maximumFractionDigits:0}).format(bytes/1000)+' Ko':new Intl.NumberFormat('fr-FR',{maximumFractionDigits:1}).format(bytes/1_000_000)+' Mo';
function isArtifact(value:Artifact):boolean{return /^RAKOTO-Player-\d+\.\d+\.\d+-(Offline|Online)-x64\.exe$/.test(value?.file)&&Number.isSafeInteger(value.bytes)&&value.bytes>0&&/^[a-f0-9]{64}$/i.test(value.sha256)&&new URL(value.url).protocol==='https:'}
async function release(){
  try{
    const response=await fetch('./release.json',{cache:'no-cache'});if(!response.ok)throw new Error('Indisponible');
    const info=await response.json() as Release;
    if(!/^\d+\.\d+\.\d+$/.test(info.version)||!isArtifact(info.offline)||!isArtifact(info.online))throw new Error('Version invalide');
    document.querySelectorAll<HTMLElement>('[data-version]').forEach(el=>el.textContent=info.version);
    for(const type of ['offline','online'] as const){
      const item=info[type];
      document.querySelectorAll<HTMLAnchorElement>(`[data-download="${type}"]`).forEach(link=>{link.href=item.url;link.removeAttribute('aria-disabled');link.setAttribute('download',item.file)});
      document.querySelectorAll<HTMLElement>(`[data-size="${type}"]`).forEach(el=>el.textContent=size(item.bytes));
      const hash=document.querySelector<HTMLElement>(`[data-hash="${type}"]`);if(hash)hash.textContent=item.sha256;
    }
    document.querySelectorAll<HTMLElement>('[data-release-status]').forEach(el=>el.textContent='Version '+info.version+' · Windows 10 / 11 · 64 bits');
  }catch{
    document.querySelectorAll<HTMLElement>('[data-release-status]').forEach(el=>el.textContent='Les téléchargements sont temporairement indisponibles. Réessayez dans un instant.');
    document.querySelectorAll<HTMLElement>('[data-size]').forEach(el=>el.textContent='Bientôt disponible');
  }
}
document.querySelectorAll<HTMLAnchorElement>('[data-download]').forEach(link=>link.addEventListener('click',event=>{
  if(link.getAttribute('aria-disabled')==='true'){event.preventDefault();document.getElementById('download-status')?.focus();}
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
