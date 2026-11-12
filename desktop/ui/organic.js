export const themes={nature:'Lamba · Betsileo',ocean:'Océan',forest:'Forêt',light:'Lamba clair',dark:'Lamba nuit'};

export function dragSurface(root,send,maximize){
  let active,frame=0,pending;
  function flush(){
    frame=0;
    if(!active||!pending)return;
    const point=pending;pending=null;
    active.lastX=point.x;active.lastY=point.y;
    send({phase:'move',...point});
  }
  function move(event){
    if(!active||event.pointerId!==active.id)return;
    pending={x:event.screenX,y:event.screenY};
    if(!frame)frame=requestAnimationFrame(flush);
  }
  function end(event){
    if(!active||(event&&event.pointerId!==active.id))return;
    if(pending)flush();
    const surface=active.surface,id=active.id;
    active=null;pending=null;cancelAnimationFrame(frame);frame=0;
    if(surface.hasPointerCapture(id))surface.releasePointerCapture(id);
    send({phase:'end'});
  }
  function down(event){
    const surface=event.target.closest('.chrome,.drawer-shell');
    if(event.button!==0||event.detail>1||!surface||event.target.closest('button,input,select,a,label'))return;
    event.preventDefault();
    active={id:event.pointerId,surface,lastX:event.screenX,lastY:event.screenY};
    surface.setPointerCapture(event.pointerId);
    send({phase:'start',x:event.screenX,y:event.screenY});
  }
  function double(event){
    if(event.target.closest('.chrome')&&!event.target.closest('button,input,select,a,label'))maximize?.();
  }
  const blur=()=>end();
  root.addEventListener('pointerdown',down);
  root.addEventListener('pointermove',move);
  root.addEventListener('pointerup',end);
  root.addEventListener('pointercancel',end);
  root.addEventListener('lostpointercapture',end);
  root.addEventListener('dblclick',double);
  window.addEventListener('blur',blur);
  return ()=>{
    end();root.removeEventListener('pointerdown',down);root.removeEventListener('pointermove',move);
    root.removeEventListener('pointerup',end);root.removeEventListener('pointercancel',end);root.removeEventListener('lostpointercapture',end);
    root.removeEventListener('dblclick',double);
    window.removeEventListener('blur',blur);
  };
}
