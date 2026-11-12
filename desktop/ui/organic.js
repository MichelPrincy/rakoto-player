export const themes={nature:'Lamba · Betsileo',ocean:'Océan',forest:'Forêt',light:'Lamba clair',dark:'Lamba nuit'};

export function organicMotion(element,intensity){
  const reduced=matchMedia('(prefers-reduced-motion: reduce)');
  const position=[0,0,0],velocity=[0,0,0],target=[0,0,0];
  let frame=0,last=0;
  const enabled=()=>!reduced.matches&&intensity()!=='off';
  const strength=()=>intensity()==='reduced'?.32:1;
  const clamp=(n,min,max)=>Math.max(min,Math.min(max,n));
  function paint(){
    element.style.setProperty('--sway-x',position[0].toFixed(2)+'px');
    element.style.setProperty('--sway-y',position[1].toFixed(2)+'px');
    element.style.setProperty('--stretch',(1+position[2]).toFixed(4));
  }
  function reset(){
    cancelAnimationFrame(frame);frame=last=0;
    position.fill(0);velocity.fill(0);target.fill(0);paint();
    element.classList.remove('is-dragging');
  }
  function update(time){
    if(!enabled()){reset();return;}
    const elapsed=last?Math.min((time-last)/1000,.024):.016;
    last=time;
    for(let i=0;i<3;i++){
      velocity[i]+=(125*(target[i]-position[i])-14*velocity[i])*elapsed;
      position[i]+=velocity[i]*elapsed;
    }
    position[0]=clamp(position[0],-7,7);position[1]=clamp(position[1],-5,5);position[2]=clamp(position[2],-.035,.045);
    paint();
    if(target.every(n=>n===0)&&Math.abs(position[0])+Math.abs(position[1])+Math.abs(velocity[0])+Math.abs(velocity[1])<.045&&
      Math.abs(position[2])+Math.abs(velocity[2])<.0007)reset();
    else frame=requestAnimationFrame(update);
  }
  function start(){if(!frame)frame=requestAnimationFrame(update);}
  return {
    push({x=0,y=0,scale=0}={}){
      if(!enabled())return;
      velocity[0]=clamp(velocity[0]+x*3.5*strength(),-120,120);
      velocity[1]=clamp(velocity[1]+y*3.5*strength(),-95,95);
      velocity[2]=clamp(velocity[2]+scale*1.05*strength(),-1.1,1.1);
      start();
    },
    drag(dx,dy){
      if(!enabled())return;
      element.classList.add('is-dragging');
      target[0]=clamp(-dx*.42,-6,6)*strength();
      target[1]=clamp(-dy*.32,-4.5,4.5)*strength();
      target[2]=clamp((Math.abs(dx)-Math.abs(dy))*.002,-.028,.038)*strength();
      start();
    },
    release(){target.fill(0);element.classList.remove('is-dragging');if(enabled())start();},
    reset
  };
}

export function dragSurface(root,send,motion,maximize){
  let active,frame=0,pending;
  function flush(){
    frame=0;
    if(!active||!pending)return;
    const point=pending;pending=null;
    motion.drag(point.x-active.lastX,point.y-active.lastY);
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
    send({phase:'end'});motion.release();
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
