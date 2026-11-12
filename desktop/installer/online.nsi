Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "WinVer.nsh"
!include "x64.nsh"
!addplugindir /x86-unicode "${PLUGINSDIR_BUILD}"
Name "RAKOTO Player · Installation en ligne"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\RAKOTO Player"
RequestExecutionLevel user
SetCompressor lzma
BrandingText "RAKOTO Player · Votre musique, votre liberté."
VIProductVersion "${VERSION}.0"
VIAddVersionKey /LANG=1036 "ProductName" "RAKOTO Player"
VIAddVersionKey /LANG=1036 "FileDescription" "Installation en ligne de RAKOTO Player"
VIAddVersionKey /LANG=1036 "FileVersion" "${VERSION}"
VIAddVersionKey /LANG=1036 "LegalCopyright" "RAKOTO Player et ses contributeurs"
!define MUI_ICON "${ROOT}\ui\rakoto.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${ROOT}\installer\assets\welcome.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "${ROOT}\installer\assets\header.bmp"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TITLE "RAKOTO Player, en quelques instants"
!define MUI_WELCOMEPAGE_TEXT "Votre musique, votre univers, votre liberté.$\r$\n$\r$\nCet assistant télécharge le programme d'installation complet, vérifie son intégrité puis ouvre l'assistant RAKOTO.$\r$\n$\r$\nUne connexion Internet est nécessaire pendant le téléchargement. Ensuite, votre lecteur fonctionne avec vos fichiers locaux, même hors ligne."
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_LANGUAGE "French"
Var payloadUrl
Var testArgument
Function .onInit
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_ICONSTOP "Windows 10 ou 11 est nécessaire." /SD IDOK
    SetErrorLevel 2
    Abort
  ${EndIf}
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "Cette version nécessite Windows 64 bits (x64)." /SD IDOK
    SetErrorLevel 2
    Abort
  ${EndIf}
  StrCpy $payloadUrl "${PAYLOAD_URL}"
  StrCpy $testArgument ""
  ${GetParameters} $0
  ClearErrors
  ${GetOptions} $0 "/TESTMODE" $1
  ${IfNot} ${Errors}
    StrCpy $testArgument "/TESTMODE"
  ${EndIf}
  ClearErrors
  ${GetOptions} $0 "/PAYLOADURL=" $1
  ${IfNot} ${Errors}
    StrCpy $2 $1 8
    StrCpy $3 $1 17
    ${If} $2 == "https://"
      StrCpy $payloadUrl $1
    ${ElseIf} $3 == "http://127.0.0.1:"
      StrCpy $payloadUrl $1
    ${Else}
      MessageBox MB_ICONSTOP "Adresse de téléchargement non autorisée." /SD IDOK
      SetErrorLevel 2
      Abort
    ${EndIf}
  ${EndIf}
FunctionEnd
Section
  InitPluginsDir
  SetOutPath "$PLUGINSDIR"
  File /oname=HashCheck.exe "${GENERATED}\HashCheck.exe"
  DetailPrint "Téléchargement de RAKOTO Player ${VERSION}..."
  ${If} ${Silent}
    INetC::get /SILENT /CONNECTTIMEOUT 30 /RECEIVETIMEOUT 120 "$payloadUrl" "$PLUGINSDIR\RAKOTO-Offline.exe" /END
  ${Else}
    INetC::get /CAPTION "RAKOTO Player · Téléchargement" /CONNECTTIMEOUT 30 /RECEIVETIMEOUT 120 "$payloadUrl" "$PLUGINSDIR\RAKOTO-Offline.exe" /END
  ${EndIf}
  Pop $0
  ${If} $0 != "OK"
    MessageBox MB_ICONEXCLAMATION "Le téléchargement a échoué ($0). Vérifiez votre connexion et réessayez, ou utilisez l'installation hors ligne." /SD IDOK
    SetErrorLevel 3
    Abort
  ${EndIf}
  DetailPrint "Vérification de l'intégrité SHA-256..."
  nsExec::ExecToStack '$\"$PLUGINSDIR\HashCheck.exe$\" $\"$PLUGINSDIR\RAKOTO-Offline.exe$\" ${PAYLOAD_SHA256}'
  Pop $0
  Pop $1
  ${If} $0 != 0
    Delete "$PLUGINSDIR\RAKOTO-Offline.exe"
    MessageBox MB_ICONSTOP "Le fichier téléchargé n'est pas conforme. L'installation a été annulée. Réessayez depuis le site RAKOTO." /SD IDOK
    SetErrorLevel 4
    Abort
  ${EndIf}
  DetailPrint "Ouverture de l'assistant RAKOTO Player..."
  ${If} ${Silent}
    ExecWait '$\"$PLUGINSDIR\RAKOTO-Offline.exe$\" /S $testArgument /D=$INSTDIR' $0
  ${Else}
    ExecWait '$\"$PLUGINSDIR\RAKOTO-Offline.exe$\" $testArgument /D=$INSTDIR' $0
  ${EndIf}
  ${If} $0 != 0
    SetErrorLevel $0
    Abort
  ${EndIf}
  Delete "$PLUGINSDIR\RAKOTO-Offline.exe"
  SetAutoClose true
SectionEnd
