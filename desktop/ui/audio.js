export const frequencies = [31,62,125,250,500,1000,2000,4000,8000,16000];
export const presets = {
  'Neutre': [0,0,0,0,0,0,0,0,0,0],
  'Signature bleue': [4,3,2,0,-1,0,2,3,3,2],
  'Basses profondes': [8,6,4,2,0,-1,-1,0,1,1],
  'Voix claire': [-4,-2,0,2,3,5,4,2,0,-1],
  'Cinéma': [5,4,2,0,1,2,3,4,3,2],
  'Acoustique': [3,2,1,0,1,3,2,1,2,2]
};

export class AudioDecks {
  constructor(container, hooks) {
    this.hooks = hooks;
    this.active = 0;
    this.decks = Array.from({length:2}, (_, index) => {
      const media = document.createElement('audio');
      media.hidden = true;
      media.preload = 'metadata';
      media.classList.toggle('active', index === 0);
      container.append(media);
      for (const type of ['play', 'pause', 'loadedmetadata', 'durationchange', 'ended', 'error'])
        media.addEventListener(type, () => {
          if (index === this.active && media === this.media) this.hooks.event(type);
        });
      return media;
    });
  }

  get media() { return this.decks[this.active]; }

  graph() {
    if (this.context) return;
    const context = this.context = new AudioContext();
    this.master = context.createGain();
    this.analyser = context.createAnalyser();
    this.analyser.fftSize = 2048;
    this.analyser.smoothingTimeConstant = .63;
    this.samples = new Uint8Array(this.analyser.frequencyBinCount);
    this.bass = context.createBiquadFilter();
    this.bass.type = 'lowshelf';
    this.bass.frequency.value = 100;
    this.filters = frequencies.map(hz => {
      const band = context.createBiquadFilter();
      band.type = 'peaking';
      band.frequency.value = Math.min(hz, context.sampleRate * .47);
      band.Q.value = 1.1;
      return band;
    });
    this.gains = this.decks.map((media, index) => {
      const gain = context.createGain();
      gain.gain.value = index === this.active ? 1 : 0;
      context.createMediaElementSource(media).connect(gain).connect(this.bass);
      return gain;
    });
    const chain = [this.bass, ...this.filters, this.master, this.analyser, context.destination];
    chain.slice(0,-1).forEach((filter,index) => filter.connect(chain[index+1]));
  }

  sync(state) {
    if (!this.context) return;
    const now = this.context.currentTime;
    this.master.gain.setTargetAtTime(state.muted ? 0 : state.volume, now, .025);
    this.bass.gain.setTargetAtTime(state.bass, now, .025);
    this.filters.forEach((filter,index) => filter.gain.setTargetAtTime(state.eqOn ? state.bands[index] : 0, now, .025));
    this.decks.forEach(media => { if (media.playbackRate !== state.speed) media.playbackRate = state.speed; });
  }

  async open(item, state) {
    this.cancelFade();
    this.graph();
    this.sync(state);
    const media = this.media;
    media.src = item.url;
    media.load();
    media.playbackRate = state.speed;
    await this.play();
  }

  async play() {
    if (!this.media.getAttribute('src')) return;
    const pending = this.context.resume();
    await Promise.all([pending, this.media.play()]);
  }

  pause() { this.cancelFade(); this.media.pause(); }

  seek(seconds) {
    this.cancelFade();
    if (Number.isFinite(this.media.duration))
      this.media.currentTime = Math.max(0, Math.min(seconds, this.media.duration));
  }

  maybeFade(item, seconds) {
    if (!item || !seconds || this.transition || this.media.paused || !Number.isFinite(this.media.duration)) return;
    const remaining = (this.media.duration - this.media.currentTime) / this.media.playbackRate;
    if (remaining > seconds || remaining < .16 || this.media.duration < seconds * 2) return;
    const nextIndex = 1 - this.active;
    const next = this.decks[nextIndex];
    const transition = this.transition = { item, nextIndex, started:false, timer:0 };
    next.src = item.url;
    next.playbackRate = this.media.playbackRate;
    next.play().then(() => {
      if (transition !== this.transition) return;
      transition.started = true;
      const duration = Math.max(.12, (this.media.duration - this.media.currentTime) / this.media.playbackRate);
      const start = this.context.currentTime;
      this.ramp(this.active, 1, 0, start, duration);
      this.ramp(nextIndex, 0, 1, start, duration);
      transition.timer = setTimeout(() => this.finishFade(), duration * 1000);
    }).catch(() => { if (transition === this.transition) this.cancelFade(); });
  }

  ramp(index, from, to, time, duration) {
    const gain = this.gains[index].gain;
    gain.cancelScheduledValues(time);
    gain.setValueAtTime(from,time);
    if (duration) gain.linearRampToValueAtTime(to,time+duration);
  }

  cancelFade() {
    if (!this.transition) return;
    clearTimeout(this.transition.timer);
    const idle = this.transition.nextIndex;
    this.transition = null;
    this.decks[idle].pause();
    this.decks[idle].removeAttribute('src');
    this.decks[idle].load();
    this.ramp(this.active,1,1,this.context.currentTime,0);
    this.ramp(idle,0,0,this.context.currentTime,0);
  }

  finishFade() {
    if (!this.transition?.started) return false;
    const { item, nextIndex, timer } = this.transition;
    clearTimeout(timer);
    this.transition = null;
    const old = this.active;
    this.active = nextIndex;
    this.decks[old].pause();
    this.decks[old].removeAttribute('src');
    this.decks[old].load();
    this.decks.forEach((deck,index) => deck.classList.toggle('active',index===this.active));
    this.ramp(old,0,0,this.context.currentTime,0);
    this.ramp(this.active,1,1,this.context.currentTime,0);
    this.hooks.promote(item);
    return true;
  }

  levels() {
    if (!this.analyser) return Array(27).fill(5);
    this.analyser.getByteFrequencyData(this.samples);
    return Array.from({length:27},(_,index) => {
      const hz=48*Math.pow(12000/48,index/26);
      return Math.max(5,this.samples[Math.round(hz*this.analyser.fftSize/this.context.sampleRate)]/2.55);
    });
  }

  spectrum() {
    if(!this.analyser)return Array(5).fill(0);
    this.analyser.getByteFrequencyData(this.samples);
    const boundaries=[40,185,545,1750,4750,11200];
    return boundaries.slice(0,-1).map((start,i)=>{
      const left=Math.max(1,Math.ceil(start*this.analyser.fftSize/this.context.sampleRate));
      const right=Math.min(this.samples.length,Math.ceil(boundaries[i+1]*this.analyser.fftSize/this.context.sampleRate));
      let peak=0;
      for(let bin=left;bin<right;bin++)peak=Math.max(peak,this.samples[bin]);
      return peak/255;
    });
  }
}

