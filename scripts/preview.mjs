import http from 'node:http';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../docs');
const types={'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.json':'application/json','.svg':'image/svg+xml','.png':'image/png'};
http.createServer(async(request,response)=>{
  try{
    const url=new URL(request.url,'http://localhost');
    const name=decodeURIComponent(url.pathname)==='/'?'index.html':decodeURIComponent(url.pathname).slice(1);
    const file=path.resolve(root,name);
    if(!file.startsWith(root+path.sep)){response.writeHead(403);response.end();return;}
    const bytes=await readFile(file);
    response.writeHead(200,{'content-type':types[path.extname(file)]||'application/octet-stream'});response.end(bytes);
  }catch{response.writeHead(404);response.end('Fichier introuvable');}
}).listen(4173,'127.0.0.1',()=>console.log('Aperçu local : http://127.0.0.1:4173'));
