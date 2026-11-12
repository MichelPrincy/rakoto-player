const koffi = require('koffi');
const user = koffi.load('user32.dll');
const Point = koffi.struct('RakotoPoint',{x:'int',y:'int'});
const Rect = koffi.struct('RakotoRect',{left:'int',top:'int',right:'int',bottom:'int'});
const getClientRect = user.func('int __stdcall GetClientRect(uintptr_t, _Out_ RakotoRect *)');
const clientToScreen = user.func('int __stdcall ClientToScreen(uintptr_t, _Inout_ RakotoPoint *)');
const getWindowLong = user.func('intptr_t __stdcall GetWindowLongPtrW(uintptr_t, int)');
const setWindowLong = user.func('intptr_t __stdcall SetWindowLongPtrW(uintptr_t, int, intptr_t)');
const setWindowPos = user.func('int __stdcall SetWindowPos(uintptr_t, intptr_t, int, int, int, int, uint)');
const showWindow = user.func('int __stdcall ShowWindow(uintptr_t, int)');
const windowProcess = user.func('uint32_t __stdcall GetWindowThreadProcessId(uintptr_t, _Out_ uint32_t *)');
const getOwner = user.func('uintptr_t __stdcall GetWindow(uintptr_t, uint)');
const nativeHandle = win => win.getNativeWindowHandle().readUInt32LE(0);

const ownerIndex=-8, exStyleIndex=-20, ownerCommand=4;
const toolWindow=0x80, appWindow=0x40000, noActivateStyle=0x08000000;
const noResizeOrMove=0x0003, noActivate=0x0010, frameChanged=0x0020, showFlag=0x0040, noOwnerZOrder=0x0200;

function ownsHandle(handle,pid){
  if(!handle||!pid)return false;
  const owner=[0];
  return Boolean(windowProcess(handle,owner))&&owner[0]===pid;
}

function hideVideo(handle,pid){
  if(ownsHandle(handle,pid))showWindow(handle,0);
}

function placeVideo(handle,pid,host,overlay,topmost){
  if(!ownsHandle(handle,pid))return false;
  const owner=nativeHandle(host), controls=nativeHandle(overlay);
  const client={}, origin={x:0,y:0};
  if(!getClientRect(owner,client)||!clientToScreen(owner,origin))return false;
  const width=client.right-client.left,height=client.bottom-client.top;
  if(width<1||height<1)return false;
  let flags=noActivate|showFlag|noOwnerZOrder;
  if(Number(getOwner(handle,ownerCommand))!==owner){
    setWindowLong(handle,ownerIndex,owner);
    const style=Number(getWindowLong(handle,exStyleIndex));
    setWindowLong(handle,exStyleIndex,(style|toolWindow|noActivateStyle)&~appWindow);
    flags|=frameChanged;
    showWindow(handle,4); // SW_SHOWNOACTIVATE also restores the initially minimized window.
  }
  const isTopmost=(Number(getWindowLong(handle,exStyleIndex))&0x0008)!==0;
  if(isTopmost!==topmost)
    setWindowPos(handle,topmost?-1:-2,0,0,0,0,noResizeOrMove|noActivate|noOwnerZOrder);
  if(topmost){
    const placed=setWindowPos(handle,-1,origin.x,origin.y,width,height,flags);
    setWindowPos(controls,-1,0,0,0,0,noResizeOrMove|noActivate|noOwnerZOrder);
    return Boolean(placed);
  }
  return Boolean(setWindowPos(handle,controls,origin.x,origin.y,width,height,flags));
}

module.exports={hideVideo,placeVideo};


