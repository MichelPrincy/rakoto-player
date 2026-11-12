const path=require('node:path');
const {existsSync}=require('node:fs');
const {types}=require('./media-probe.cjs');
const APP_NAME='RAKOTO Player',PROG_ID='RAKOTOPlayer.Media';
let query;
function currentDefault(extension){
 if(process.platform!=='win32'||!Object.hasOwn(types,extension))return null;
 try{
  if(!query)query=require('koffi').load('shlwapi.dll').func('int __stdcall AssocQueryStringW(uint32_t flags, uint32_t str, str16 assoc, str16 extra, _Out_ uint16_t *output, _Inout_ uint32_t *length)');
  const length=[32768],output=Buffer.alloc(length[0]*2);
  if(query(0,2,'.'+extension,'open',output,length)!==0)return null;
  return output.toString('utf16le').split('\0')[0]||null;
 }catch{return null}
}
function status(executable,extension){
 const installed=existsSync(path.join(path.dirname(executable),'.rakoto-install'));
 const current=currentDefault(extension);
 return {installed,extension,current:current?path.basename(current):null,isDefault:current?path.resolve(current).toLowerCase()===path.resolve(executable).toLowerCase():null};
}
function shouldOffer(value,preferences,now=Date.now()){
 return value.installed&&value.isDefault===false&&!preferences.never&&preferences.opens>=3&&now>=(preferences.next||0);
}
function registration(){
 const command=`'$\\"$INSTDIR\\RAKOTO Player.exe$\\" $\\"%1$\\"'`;
 const lines=[
  `WriteRegStr HKCU "Software\\Classes\\${PROG_ID}" "" "Média RAKOTO Player"`,
  `WriteRegStr HKCU "Software\\Classes\\${PROG_ID}" "FriendlyTypeName" "Audio et vidéo RAKOTO Player"`,
  `WriteRegStr HKCU "Software\\Classes\\${PROG_ID}\\DefaultIcon" "" '$\\"$INSTDIR\\RAKOTO Player.exe$\\",0'`,
  `WriteRegStr HKCU "Software\\Classes\\${PROG_ID}\\shell\\open\\command" "" ${command}`,
  `WriteRegStr HKCU "Software\\Classes\\Applications\\RAKOTO Player.exe" "FriendlyAppName" "${APP_NAME}"`,
  `WriteRegStr HKCU "Software\\Classes\\Applications\\RAKOTO Player.exe\\shell\\open\\command" "" ${command}`,
  `WriteRegStr HKCU "Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\RAKOTO Player.exe" "" "$INSTDIR\\RAKOTO Player.exe"`,
  `WriteRegStr HKCU "Software\\RAKOTOPlayer\\Capabilities" "ApplicationName" "${APP_NAME}"`,
  `WriteRegStr HKCU "Software\\RAKOTOPlayer\\Capabilities" "ApplicationDescription" "Lecteur audio et vidéo local"`,
  `WriteRegStr HKCU "Software\\RAKOTOPlayer\\Capabilities" "ApplicationIcon" '$\\"$INSTDIR\\RAKOTO Player.exe$\\",0'`,
  `WriteRegStr HKCU "Software\\RegisteredApplications" "${APP_NAME}" "Software\\RAKOTOPlayer\\Capabilities"`
 ];
 for(const ext of Object.keys(types))lines.push(
  `WriteRegStr HKCU "Software\\Classes\\.${ext}\\OpenWithProgids" "${PROG_ID}" ""`,
  `WriteRegStr HKCU "Software\\Classes\\Applications\\RAKOTO Player.exe\\SupportedTypes" ".${ext}" ""`,
  `WriteRegStr HKCU "Software\\RAKOTOPlayer\\Capabilities\\FileAssociations" ".${ext}" "${PROG_ID}"`);
 lines.push(`System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'`);
 return lines;
}
function unregistration(){return [
 ...Object.keys(types).map(ext=>`DeleteRegValue HKCU "Software\\Classes\\.${ext}\\OpenWithProgids" "${PROG_ID}"`),
 `DeleteRegKey HKCU "Software\\Classes\\${PROG_ID}"`,
 'DeleteRegKey HKCU "Software\\Classes\\Applications\\RAKOTO Player.exe"',
 'DeleteRegKey HKCU "Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\RAKOTO Player.exe"',
 `DeleteRegValue HKCU "Software\\RegisteredApplications" "${APP_NAME}"`,
 `System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'`
]}
module.exports={currentDefault,status,shouldOffer,registration,unregistration};
