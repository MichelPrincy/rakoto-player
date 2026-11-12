Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "WinVer.nsh"
!include "x64.nsh"
!addplugindir /x86-unicode "${PLUGINSDIR_BUILD}"
Name "RAKOTO Player"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\RAKOTO Player"
InstallDirRegKey HKCU "Software\RAKOTOPlayer" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 32
BrandingText "RAKOTO Player · Votre musique, votre liberté."
VIProductVersion "${VERSION}.0"
VIAddVersionKey /LANG=1036 "ProductName" "RAKOTO Player"
VIAddVersionKey /LANG=1036 "FileDescription" "Installation hors ligne de RAKOTO Player"
VIAddVersionKey /LANG=1036 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1036 "LegalCopyright" "RAKOTO Player et ses contributeurs"
!define MUI_ICON "${ROOT}\ui\rakoto.ico"
!define MUI_UNICON "${ROOT}\ui\rakoto.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${ROOT}\installer\assets\welcome.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "${ROOT}\installer\assets\header.bmp"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE "Bienvenue dans RAKOTO Player"
!define MUI_WELCOMEPAGE_TEXT "Votre musique, votre univers, votre liberté.$\r$\n$\r$\nInstallez votre lecteur audio et vidéo pour Windows.$\r$\n$\r$\nCette version contient tous les composants et fonctionne sans connexion Internet.$\r$\n$\r$\nInstallation pour votre compte Windows. Vos médias et votre bibliothèque restent à leur emplacement."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${ROOT}\installer\NOTICES.txt"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_TITLE "RAKOTO Player est installé"
!define MUI_FINISHPAGE_TEXT "Tout est prêt pour votre prochaine écoute.$\r$\n$\r$\nOuvrez RAKOTO Player et importez vos médias locaux."
!define MUI_FINISHPAGE_RUN "$INSTDIR\RAKOTO Player.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Lancer RAKOTO Player"
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "French"
Var testMode
Var registryKey
Var uninstallKey

Function .onInit
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_ICONSTOP "RAKOTO Player nécessite Windows 10 ou Windows 11."
    SetErrorLevel 2
    Abort
  ${EndIf}
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "Cette version nécessite Windows 64 bits (x64)."
    SetErrorLevel 2
    Abort
  ${EndIf}
  SetShellVarContext current
  StrCpy $testMode "0"
  StrCpy $registryKey "Software\RAKOTOPlayer"
  StrCpy $uninstallKey "Software\Microsoft\Windows\CurrentVersion\Uninstall\RAKOTOPlayer"
  ${GetParameters} $0
  ClearErrors
  ${GetOptions} $0 "/TESTMODE" $1
  ${IfNot} ${Errors}
    StrCpy $testMode "1"
    StrCpy $registryKey "Software\RAKOTOPlayer.Test"
    StrCpy $uninstallKey "Software\Microsoft\Windows\CurrentVersion\Uninstall\RAKOTOPlayer.Test"
  ${EndIf}
  nsProcess::_FindProcess "RAKOTO Player.exe"
  Pop $0
  ${If} $0 == 0
    MessageBox MB_ICONEXCLAMATION "Fermez RAKOTO Player avant de poursuivre l'installation."
    SetErrorLevel 2
    Abort
  ${EndIf}
FunctionEnd

Function ValidateDirectory
  ${GetRoot} "$INSTDIR" $0
  ${If} $INSTDIR == ""
  ${OrIf} $INSTDIR == "$0\"
  ${OrIf} $INSTDIR == "$WINDIR"
  ${OrIf} $INSTDIR == "$PROFILE"
  ${OrIf} $INSTDIR == "$LOCALAPPDATA"
  ${OrIf} $INSTDIR == "$PROGRAMFILES"
  ${OrIf} $INSTDIR == "$PROGRAMFILES64"
    SetErrorLevel 2
    Abort
  ${EndIf}
FunctionEnd
Function .onVerifyInstDir
  Call ValidateDirectory
FunctionEnd

Section "RAKOTO Player (obligatoire)" Core
  SectionIn RO
  Call ValidateDirectory
  SetOutPath "$INSTDIR"
  DetailPrint "Installation des composants de RAKOTO Player..."
  File /r "${APPDIR}\*"
  FileOpen $0 "$INSTDIR\.rakoto-install" w
  FileWrite $0 "RAKOTO.Player$\r$\n$testMode$\r$\n"
  FileClose $0
  WriteUninstaller "$INSTDIR\Désinstaller RAKOTO Player.exe"
  DetailPrint "Configuration de votre lecteur..."
  WriteRegStr HKCU "$registryKey" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "$uninstallKey" "DisplayName" "RAKOTO Player"
  WriteRegStr HKCU "$uninstallKey" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "$uninstallKey" "Publisher" "RAKOTO Player"
  WriteRegStr HKCU "$uninstallKey" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "$uninstallKey" "DisplayIcon" "$INSTDIR\RAKOTO Player.exe,0"
  WriteRegStr HKCU "$uninstallKey" "UninstallString" '$\"$INSTDIR\Désinstaller RAKOTO Player.exe$\"'
  WriteRegStr HKCU "$uninstallKey" "QuietUninstallString" '$\"$INSTDIR\Désinstaller RAKOTO Player.exe$\" /S'
  WriteRegDWORD HKCU "$uninstallKey" "EstimatedSize" ${INSTALLED_KB}
  WriteRegDWORD HKCU "$uninstallKey" "NoModify" 1
  WriteRegDWORD HKCU "$uninstallKey" "NoRepair" 1
SectionEnd
Section "Raccourci sur le bureau" Desktop
  ${If} $testMode == "0"
    CreateShortcut "$DESKTOP\RAKOTO Player.lnk" "$INSTDIR\RAKOTO Player.exe"
  ${EndIf}
SectionEnd
Section "Ajouter au menu Démarrer" StartMenu
  ${If} $testMode == "0"
    CreateDirectory "$SMPROGRAMS\RAKOTO Player"
    CreateShortcut "$SMPROGRAMS\RAKOTO Player\RAKOTO Player.lnk" "$INSTDIR\RAKOTO Player.exe"
    CreateShortcut "$SMPROGRAMS\RAKOTO Player\Désinstaller.lnk" "$INSTDIR\Désinstaller RAKOTO Player.exe"
  ${EndIf}
SectionEnd
Section /o "Lancer au démarrage de Windows" Startup
  ${If} $testMode == "0"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RAKOTOPlayer" '$\"$INSTDIR\RAKOTO Player.exe$\"'
  ${EndIf}
SectionEnd
Section /o "Proposer RAKOTO dans Ouvrir avec" FileTypes
  ${If} $testMode == "0"
    !include "${GENERATED}\associations.nsh"
  ${EndIf}
SectionEnd

Function un.onInit
  SetShellVarContext current
  StrCpy $testMode "0"
  FileOpen $0 "$INSTDIR\.rakoto-install" r
  IfErrors invalid
  FileRead $0 $1
  FileRead $0 $testMode
  FileClose $0
  StrCmp $1 "RAKOTO.Player$\r$\n" +2
  Goto invalid
  StrCmp $testMode "1$\r$\n" 0 normal
  StrCpy $testMode "1"
  StrCpy $registryKey "Software\RAKOTOPlayer.Test"
  StrCpy $uninstallKey "Software\Microsoft\Windows\CurrentVersion\Uninstall\RAKOTOPlayer.Test"
  Return
  normal:
  StrCpy $testMode "0"
  StrCpy $registryKey "Software\RAKOTOPlayer"
  StrCpy $uninstallKey "Software\Microsoft\Windows\CurrentVersion\Uninstall\RAKOTOPlayer"
  Return
  invalid:
    MessageBox MB_ICONSTOP "Le dossier ne contient pas une installation RAKOTO Player valide."
    SetErrorLevel 2
    Abort
FunctionEnd
Section "Uninstall"
  nsProcess::_FindProcess "RAKOTO Player.exe"
  Pop $0
  ${If} $0 == 0
    MessageBox MB_ICONEXCLAMATION "Fermez RAKOTO Player avant de le désinstaller."
    SetErrorLevel 2
    Abort
  ${EndIf}
  !include "${GENERATED}\uninstall-files.nsh"
  Delete "$INSTDIR\.rakoto-install"
  Delete "$INSTDIR\Désinstaller RAKOTO Player.exe"
  ${If} $testMode == "0"
    Delete "$DESKTOP\RAKOTO Player.lnk"
    Delete "$SMPROGRAMS\RAKOTO Player\RAKOTO Player.lnk"
    Delete "$SMPROGRAMS\RAKOTO Player\Désinstaller.lnk"
    RMDir "$SMPROGRAMS\RAKOTO Player"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "RAKOTOPlayer"
    !include "${GENERATED}\unregister-associations.nsh"
  ${EndIf}
  DeleteRegKey HKCU "$registryKey"
  DeleteRegKey HKCU "$uninstallKey"
  RMDir "$INSTDIR"
SectionEnd
