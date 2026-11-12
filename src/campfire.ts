/** Animate the supplied illustration, without moving the characters or decoding video.
 * Only a small, masked crop is rendered. The untouched image is the fallback. */
export function createCampfire(image:HTMLImageElement,canvas:HTMLCanvasElement){
 const region=[.27,.41,.31,.46]; // Source pixels, normalized from the top left.
 let gl:WebGLRenderingContext|null=null,program:WebGLProgram|null=null;
 let texture:WebGLTexture|null=null,buffer:WebGLBuffer|null=null;
 let frame=0,last=0,enabled=false,disposed=false,failed=false;
 let time:WebGLUniformLocation|null=null;
 const vertex=`attribute vec2 position;varying vec2 uv;void main(){uv=position*.5+.5;gl_Position=vec4(position,0.,1.);}`;
 const fragment=`precision mediump float;
 varying vec2 uv;uniform sampler2D picture;uniform float time;uniform vec4 region;
 void main(){
  vec2 p=region.xy+vec2(uv.x,1.-uv.y)*region.zw;
  float height=clamp((.81-p.y)/.29,0.,1.);
  // Narrow at the tips and fixed at the logs. The drum, hands and feet are excluded.
  float center=.424+.018*sin(height*4.);
  float width=mix(.105,.026,height);
  float shape=(1.-smoothstep(width*.56,width,abs(p.x-center)))*smoothstep(.49,.56,p.y)*(1.-smoothstep(.79,.83,p.y));
  float turbulence=sin(p.y*95.-time*4.3)+.48*sin(p.y*173.-time*6.7+p.x*53.);
  vec2 offset=vec2(turbulence*.005*height,sin(p.x*115.+time*3.1)*.004*height)*shape;
  vec3 source=texture2D(picture,p).rgb;
  vec3 flame=texture2D(picture,p+offset).rgb;
  float hot=smoothstep(.60,.90,max(source.r,flame.r))*smoothstep(.25,.60,max(source.g,flame.g));
  float alpha=shape*hot*.97;
  vec3 color=flame*(1.+.025*sin(time*7.+p.y*84.)*height);
  // A few small embers drift upward from the actual hearth.
  for(int i=0;i<7;i++){
   float n=float(i),life=fract(time*(.19+n*.009)+n*.147);
   vec2 ember=vec2(.425+sin(n*3.1+life*4.)*(.018+life*.03),.76-life*.31);
   float distance=length((p-ember)*vec2(1.,.892));
   float spark=(1.-smoothstep(.0008,.0034,distance))*sin(life*3.14159)*.85;
   color=mix(color,vec3(1.,.76,.29),spark);alpha=max(alpha,spark);
  }
  gl_FragColor=vec4(color,alpha);
 }`;
 const release=()=>{
  if(!gl)return;
  if(texture)gl.deleteTexture(texture);if(buffer)gl.deleteBuffer(buffer);if(program)gl.deleteProgram(program);
  texture=null;buffer=null;program=null;time=null;
 };
 const prepare=()=>{
  if(gl&&program)return true;if(failed||!image.complete||!image.naturalWidth)return false;
  try{
   gl=canvas.getContext('webgl',{alpha:true,antialias:false,depth:false,stencil:false,premultipliedAlpha:false,powerPreference:'low-power'});
   if(!gl){failed=true;return false}
   const shader=(type:number,source:string)=>{
    const value=gl!.createShader(type);if(!value)throw Error('Shader allocation');
    gl!.shaderSource(value,source);gl!.compileShader(value);
    if(!gl!.getShaderParameter(value,gl!.COMPILE_STATUS)){gl!.deleteShader(value);throw Error('Shader compilation')}
    return value;
   };
   const vs=shader(gl.VERTEX_SHADER,vertex),fs=shader(gl.FRAGMENT_SHADER,fragment);
   program=gl.createProgram();if(!program)throw Error('Program allocation');
   gl.attachShader(program,vs);gl.attachShader(program,fs);gl.linkProgram(program);gl.deleteShader(vs);gl.deleteShader(fs);
   if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error('Program link');
   gl.useProgram(program);buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
   gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);
   const position=gl.getAttribLocation(program,'position');gl.enableVertexAttribArray(position);gl.vertexAttribPointer(position,2,gl.FLOAT,false,0,0);
   texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);
   gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
   gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
   gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);
   gl.uniform1i(gl.getUniformLocation(program,'picture'),0);gl.uniform4fv(gl.getUniformLocation(program,'region'),region);
   time=gl.getUniformLocation(program,'time');resize();return true;
  }catch{failed=true;release();canvas.dataset.fire='unavailable';return false}
 };
 const resize=()=>{
  const box=image.getBoundingClientRect();
  const scale=Math.min(box.width/image.naturalWidth,box.height/image.naturalHeight);
  if(!Number.isFinite(scale)||scale<=0)return;
  const width=image.naturalWidth*scale,height=image.naturalHeight*scale;
  image.style.setProperty('--art-top',box.height-height+'px');
  // The image uses object-fit:contain, object-position:right bottom on every viewport.
  Object.assign(canvas.style,{left:box.width-width+width*region[0]+'px',top:box.height-height+height*region[1]+'px',width:width*region[2]+'px',height:height*region[3]+'px'});
  const density=Math.min(devicePixelRatio,1.5),w=Math.max(1,Math.min(420,Math.round(width*region[2]*density))),h=Math.max(1,Math.min(560,Math.round(height*region[3]*density)));
  if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h}
  gl?.viewport(0,0,w,h);
 };
 const draw=(now:number)=>{
  frame=0;if(!enabled||disposed||document.hidden)return;
  if(now-last>=1000/24){last=now;gl!.uniform1f(time,(now%600000)/1000);gl!.drawArrays(gl!.TRIANGLE_STRIP,0,4)}
  frame=requestAnimationFrame(draw);
 };
 const setEnabled=(value:boolean)=>{
  enabled=value&&!disposed&&!failed;cancelAnimationFrame(frame);frame=0;
  if(enabled&&prepare()){canvas.hidden=false;canvas.dataset.fire='running';frame=requestAnimationFrame(draw)}
  else{canvas.hidden=true;canvas.dataset.fire=failed?'unavailable':'paused'}
 };
 const lost=(event:Event)=>{event.preventDefault();cancelAnimationFrame(frame);frame=0;canvas.hidden=true;canvas.dataset.fire='paused';gl=null;program=null;texture=null;buffer=null};
 const restored=()=>{failed=false;setEnabled(enabled)};
 canvas.addEventListener('webglcontextlost',lost);canvas.addEventListener('webglcontextrestored',restored);
 return {setEnabled,resize,dispose(){disposed=true;setEnabled(false);release();gl?.getExtension('WEBGL_lose_context')?.loseContext();canvas.removeEventListener('webglcontextlost',lost);canvas.removeEventListener('webglcontextrestored',restored);gl=null}};
}
