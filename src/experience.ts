import {remember,stored,text} from './content';

/** Original procedural ambience. No samples, streaming or microphone access. */
export class RakotoAmbience {
 context:AudioContext|null=null;analyser:AnalyserNode|null=null;gain:GainNode|null=null;
 active=false;timer:ReturnType<typeof setInterval>|undefined;next=0;phrase=0;
 voices=new Set<OscillatorNode>();
 async start(){
  if(!this.context){
   this.context=new AudioContext({latencyHint:'playback'});
   this.gain=this.context.createGain();this.gain.gain.value=.075;
   const compressor=this.context.createDynamicsCompressor();compressor.threshold.value=-22;compressor.ratio.value=3;
   this.analyser=this.context.createAnalyser();this.analyser.fftSize=1024;this.analyser.smoothingTimeConstant=.82;
   this.gain.connect(compressor);compressor.connect(this.analyser);this.analyser.connect(this.context.destination);
  }
  await this.context.resume();
  if(this.context.state!=='running')throw new Error('Audio suspended by the browser.');
  this.active=true;this.next=this.context.currentTime+.07;this.phrase=0;
  this.schedule();this.timer=setInterval(()=>this.schedule(),250);
 }
 schedule(){
  const context=this.context;if(!context||!this.gain||!this.active)return;
  const beat=60/76;
  // Schedule only a little ahead: bounded voices and no ever-growing audio graph.
  while(this.next<context.currentTime+.4){
   const notes=[146.832,174.614,220,261.626,220,195.998,174.614,130.813];
   const frequency=notes[this.phrase%notes.length];
   this.voice(frequency,this.next,beat*2.5,.26,'sine');
   this.voice(frequency*2,this.next+beat*.5,beat*1.7,.055,'triangle');
   if(this.phrase%4===0)this.voice(frequency/2,this.next,beat*7,.13,'sine');
   this.next+=beat*2;this.phrase++;
  }
 }
 voice(hz:number,start:number,length:number,level:number,wave:OscillatorType){
  const context=this.context;if(!context||!this.gain)return;
  const oscillator=context.createOscillator(),envelope=context.createGain();oscillator.type=wave;oscillator.frequency.value=hz;
  envelope.gain.setValueAtTime(.0001,start);envelope.gain.exponentialRampToValueAtTime(level,start+.18);envelope.gain.exponentialRampToValueAtTime(.0001,start+length);
  oscillator.connect(envelope);envelope.connect(this.gain);oscillator.start(start);oscillator.stop(start+length+.02);
  this.voices.add(oscillator);oscillator.onended=()=>{this.voices.delete(oscillator);oscillator.disconnect();envelope.disconnect()};
 }
 async stop(){this.active=false;clearInterval(this.timer);for(const voice of this.voices){try{voice.stop()}catch{/* It may have ended. */}}this.voices.clear();if(this.context?.state==='running')await this.context.suspend()}
 async dispose(){clearInterval(this.timer);this.active=false;if(this.context&&this.context.state!=='closed')await this.context.close();this.context=null;this.analyser=null;this.gain=null}
}

export function initExperience(){
 const root=document.documentElement,abort=new AbortController(),signal=abort.signal;
 const reduce=matchMedia('(prefers-reduced-motion: reduce)');
 const device=navigator as Navigator&{deviceMemory?:number;connection?:{saveData?:boolean}};
 const lowPower=Boolean(device.connection?.saveData||(device.deviceMemory&&device.deviceMemory<=4)||(navigator.hardwareConcurrency&&navigator.hardwareConcurrency<=4));
 let lite=reduce.matches||stored('rakoto-motion')==='lite'||(stored('rakoto-motion')!=='full'&&lowPower);
 const ambience=new RakotoAmbience();let soundBusy=false,soundError=false;
 const canvas=document.querySelector<HTMLCanvasElement>('#spectrum'),context=canvas?.getContext('2d');
 const bins=new Uint8Array(512);let spectrumFrame=0,scrollFrame=0,lastPaint=0,audioVisible=false;
 const reduceSoundMotion=()=>lite||reduce.matches;
 const setControls=()=>{
  const key=soundError?'sound.error':ambience.active?'sound.on':stored('rakoto-sound')==='on'?'sound.resume':'sound.off';
  document.querySelectorAll<HTMLButtonElement>('[data-sound-toggle]').forEach(button=>{button.setAttribute('aria-pressed',String(ambience.active));button.setAttribute('aria-label',text(key));button.title=text('sound.hint')});
  document.querySelectorAll<HTMLElement>('[data-sound-label]').forEach(el=>{el.textContent=text(key);el.dataset.i18n=key});
  const label=document.querySelector<HTMLElement>('[data-visualizer-label]');if(label){const key=ambience.active?'audio.reactive':'audio.decorative';label.textContent=text(key);label.dataset.i18n=key}
  root.classList.toggle('sound-active',ambience.active);
  const motion=document.querySelector<HTMLButtonElement>('[data-motion-toggle]');motion?.setAttribute('aria-pressed',String(lite));if(motion)motion.title=text(lite?'motion.lite':'motion.full');
  const motionLabel=document.querySelector<HTMLElement>('[data-motion-label]');if(motionLabel){motionLabel.textContent=text(lite?'motion.lite':'motion.full');motionLabel.dataset.i18n=lite?'motion.lite':'motion.full'}
 };
 const draw=(timestamp=0)=>{
  spectrumFrame=0;if(!canvas||!context)return;
  const live=ambience.active&&ambience.analyser;
  if(timestamp-lastPaint<40&&timestamp){if(live&&audioVisible&&!reduceSoundMotion())spectrumFrame=requestAnimationFrame(draw);return}lastPaint=timestamp;
  if(live)live.getByteFrequencyData(bins);
  context.clearRect(0,0,canvas.width,canvas.height);const count=56,width=canvas.width/count;
  for(let i=0;i<count;i++){
   // Deterministic resting shape, explicitly labelled decorative; FFT only when sound is active.
   const index=Math.min(bins.length-1,Math.floor(1+Math.pow(i/(count-1),1.7)*100));
   const level=live?bins[index]/255:(.08+Math.sin(i*.31)**2*.28+Math.sin(i*.09+.8)**2*.18);
   const height=8+level*145;
   context.fillStyle=i%7===0?'#e3b04b':`rgba(181,68,46,${.3+level*.7})`;
   context.fillRect(i*width+3,canvas.height-height-8,width-5,height);
  }
  if(live&&audioVisible&&!document.hidden&&!reduceSoundMotion())spectrumFrame=requestAnimationFrame(draw);
 };
 const animateSpectrum=()=>{cancelAnimationFrame(spectrumFrame);spectrumFrame=0;draw(performance.now()+40)};
 const sound=async(force?:boolean)=>{
  if(soundBusy)return;soundBusy=true;soundError=false;
  try{
   if(ambience.active&&force!==true){await ambience.stop();remember('rakoto-sound','off')}
   else if(!ambience.active){await ambience.start();remember('rakoto-sound','on')}
  }catch{soundError=true;await ambience.stop()}
  finally{soundBusy=false;setControls();animateSpectrum()}
 };
 document.querySelector('[data-sound-toggle]')?.addEventListener('click',()=>void sound(),{signal});
 document.querySelector('[data-sound-activate]')?.addEventListener('click',()=>void sound(true),{signal});
 const parallax=Array.from(document.querySelectorAll<HTMLElement>('[data-parallax]'));
 const updateScroll=()=>{
  scrollFrame=0;const total=root.scrollHeight-innerHeight;
  root.style.setProperty('--scroll',String(total>0?Math.max(0,Math.min(1,scrollY/total)):0));
  if(lite)return;
  for(const el of parallax){const box=el.getBoundingClientRect();if(box.bottom>0&&box.top<innerHeight)el.style.setProperty('--parallax',Math.max(-45,Math.min(45,(innerHeight*.5-box.top)*Number(el.dataset.parallax||0)))+'px')}
 };
 const queueScroll=()=>{if(!scrollFrame&&!document.hidden)scrollFrame=requestAnimationFrame(updateScroll)};
 const applyMotion=()=>{root.dataset.effects=lite?'lite':'full';setControls();queueScroll();animateSpectrum()};
 document.querySelector('[data-motion-toggle]')?.addEventListener('click',()=>{lite=reduce.matches?true:!lite;remember('rakoto-motion',lite?'lite':'full');applyMotion()},{signal});
 const motionPreference=()=>{lite=reduce.matches||stored('rakoto-motion')==='lite'||(stored('rakoto-motion')!=='full'&&lowPower);applyMotion()};
 reduce.addEventListener('change',motionPreference,{signal});
 const reveals=typeof IntersectionObserver==='function'?new IntersectionObserver(entries=>{
  for(const entry of entries)if(entry.isIntersecting){entry.target.classList.add('is-visible');reveals?.unobserve(entry.target)}
 },{threshold:.1,rootMargin:'0px 0px -20px 0px'}):null;
 document.querySelectorAll('[data-reveal]').forEach(el=>reveals?reveals.observe(el):el.classList.add('is-visible'));
 const scenes=typeof IntersectionObserver==='function'?new IntersectionObserver(entries=>{
  for(const entry of entries){entry.target.classList.toggle('in-view',entry.isIntersecting);if(entry.target.id==='audio'){audioVisible=entry.isIntersecting;animateSpectrum()}}
 },{threshold:0}):null;
 document.querySelectorAll('[data-scene]').forEach(el=>scenes?.observe(el));
 window.addEventListener('scroll',queueScroll,{passive:true,signal});window.addEventListener('resize',queueScroll,{passive:true,signal});
 document.addEventListener('rakoto:language',setControls,{signal});
 // Never resume audio automatically, even after a previous opt-in or a tab return.
 document.addEventListener('visibilitychange',()=>{root.classList.toggle('tab-hidden',document.hidden);if(document.hidden){void ambience.stop().then(()=>{setControls();animateSpectrum()});cancelAnimationFrame(scrollFrame);scrollFrame=0}else{queueScroll();setControls()}},{signal});
 document.addEventListener('freeze',()=>void ambience.stop(),{signal});
 window.addEventListener('pagehide',event=>{if(event.persisted){void ambience.stop().then(setControls);return}abort.abort();reveals?.disconnect();scenes?.disconnect();cancelAnimationFrame(scrollFrame);cancelAnimationFrame(spectrumFrame);void ambience.dispose()});
 window.addEventListener('pageshow',()=>{setControls();queueScroll()},{signal});
 root.classList.add('motion-ready');applyMotion();draw();
}
