const { contextBridge, ipcRenderer, webUtils } = require('electron');
const subscribe = (channel, fn) => {
  const listener = (_, value) => { fn(value); };
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
};
contextBridge.exposeInMainWorld('desktop', {
  prepare: id=>ipcRenderer.invoke('library:prepare',id),
  defaults: action=>ipcRenderer.invoke('windows:defaults',action),
  onVideoProgress: fn=>subscribe('video:progress',fn),
  onVideoItem: fn=>subscribe('video:item',fn),
  load: () => ipcRenderer.invoke('library:load'),
  import: () => ipcRenderer.invoke('library:import'),
  drop: files => ipcRenderer.invoke('library:drop', Array.from(files, file => webUtils.getPathForFile(file))),
  save: value => ipcRenderer.send('library:save', value),
  captions: (id, ext, pick) => ipcRenderer.invoke('library:captions', id, ext, pick),
  window: (mode, opacity) => ipcRenderer.send('window:mode', mode, opacity),
  publish: state => ipcRenderer.send('player:state', state),
  command: (action, value) => ipcRenderer.send('player:command', action, value),
  onCommand: fn => subscribe('player:command', fn),
  onState: fn => subscribe('player:state', fn),
  onOpenedFiles: fn => subscribe('library:opened',fn),
  onMediaUpdated: fn => subscribe('library:updated',fn),
  videoSession: () => ipcRenderer.invoke('video:session'),
  onVideoSession: fn => subscribe('video:session',fn),
  meter: levels => ipcRenderer.send('player:meter',levels),
  onMeter: fn => subscribe('player:meter',fn),
  onPoster: fn => subscribe('player:poster',fn),
  windowDrag: value => ipcRenderer.send('window:drag',value),
  onMode: fn => subscribe('window:changed', fn),
  nativeOpen: (id,options) => ipcRenderer.invoke('media:open',id,options),
  nativeSync: options => ipcRenderer.invoke('media:sync',options),
  nativeRelease: () => ipcRenderer.invoke('media:release'),
  nativeTransport: (action,value) => ipcRenderer.invoke('media:transport',action,value),
  nativeSelect: (kind,id) => ipcRenderer.invoke('media:select',kind,id),
  mediaMenu: kind => ipcRenderer.invoke('media:menu',kind),
  mediaCommand: (action,value) => ipcRenderer.invoke('media:command',action,value),
  mediaExternal: () => ipcRenderer.invoke('media:external'),
  mediaInfo: () => ipcRenderer.invoke('media:info'),
  mediaAspect: value => ipcRenderer.invoke('media:aspect',value),
  mediaStage: bounds => ipcRenderer.send('media:stage',bounds),
  mediaDrag: (x,y) => ipcRenderer.send('media:drag',x,y),
  onNative: fn => subscribe('media:update',fn),
  onOverlay: fn => subscribe('media:overlay',fn),
  onMenuLock: fn => subscribe('media:menu-lock',fn),
  onMediaDetails: fn => subscribe('media:details',fn),
  onMediaNotice: fn => subscribe('media:notice',fn)
});




