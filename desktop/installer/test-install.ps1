param([ValidateSet('offline','online')][string]$Mode='offline',[string]$PayloadUrl='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$release=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'release.json') -Raw | ConvertFrom-Json
$testRoot=Join-Path $projectRoot 'tools\media-work\installation-tests'
[void](New-Item -ItemType Directory -Path $testRoot -Force)
$destination=Join-Path $testRoot ($Mode+'-'+[guid]::NewGuid().ToString('N'))
if (-not ([IO.Path]::GetFullPath($destination).StartsWith([IO.Path]::GetFullPath($testRoot)+[IO.Path]::DirectorySeparatorChar))) {throw 'Chemin de test invalide'}
$file=if ($Mode -eq 'offline') {$release.offline} else {$release.online}
$installerPath=Join-Path $projectRoot ('dist\installateurs\'+$file)
$arguments='/S /TESTMODE'
if ($PayloadUrl) {$arguments+=' /PAYLOADURL="'+$PayloadUrl+'"'}
$arguments+=' /D='+$destination
try {
  $process=Start-Process -FilePath $installerPath -ArgumentList $arguments -PassThru -Wait -WindowStyle Hidden
  if ($process.ExitCode -ne 0) {throw ('Installation en échec : '+$process.ExitCode)}
  foreach ($relative in @('RAKOTO Player.exe','resources\app\native\mpv.exe','resources\app\media-analyzer.cjs','.rakoto-install','Désinstaller RAKOTO Player.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $destination $relative))) {throw ('Fichier absent : '+$relative)}
  }
  $registered=Get-ItemProperty -LiteralPath 'HKCU:\Software\RAKOTOPlayer.Test'
  if ($registered.InstallDir -ne $destination) {throw 'Dossier enregistré incorrect'}
  # This user file must survive uninstallation, along with all user media.
  Set-Content -LiteralPath (Join-Path $destination 'document-personnel-test.txt') -Value 'À conserver'
  $env:RAKOTO_FIXTURES=Join-Path $projectRoot 'tools\media-work\fixtures'
  $env:RAKOTO_TEST_LOG=Join-Path $testRoot ($Mode+'-lecture.txt')
  $env:RAKOTO_INSTALLED_TEST_EXE=Join-Path $destination 'RAKOTO Player.exe'
  node -e "const p=require('child_process').spawn(process.env.RAKOTO_INSTALLED_TEST_EXE,['--verify-advanced'],{stdio:'inherit',env:process.env});p.on('exit',code=>process.exitCode=code);p.on('error',error=>{console.error(error.message);process.exitCode=1})"
  if ($LASTEXITCODE -ne 0) {throw ('Lecture après installation en échec : '+$LASTEXITCODE)}
  Write-Output ('Installation '+$Mode+' et fonctions du lecteur vérifiées.')
} finally {
  $uninstaller=Join-Path $destination 'Désinstaller RAKOTO Player.exe'
  if (Test-Path -LiteralPath $uninstaller) {
    $process=Start-Process -FilePath $uninstaller -ArgumentList ('/S _?='+$destination) -PassThru -Wait -WindowStyle Hidden
    if ($process.ExitCode -ne 0) {throw ('Désinstallation en échec : '+$process.ExitCode)}
    if (Test-Path -LiteralPath (Join-Path $destination 'RAKOTO Player.exe')) {throw 'Application encore présente après désinstallation'}
    if (-not (Test-Path -LiteralPath (Join-Path $destination 'document-personnel-test.txt'))) {throw 'Le fichier personnel a été supprimé'}
    if (Test-Path -LiteralPath 'HKCU:\Software\RAKOTOPlayer.Test') {throw 'Clé de test non supprimée'}
    Write-Output 'Désinstallation vérifiée : fichiers personnels préservés et clés du programme supprimées.'
    # Only generated test files at this exact, prevalidated workspace destination.
    Remove-Item -LiteralPath (Join-Path $destination 'document-personnel-test.txt')
    if (Test-Path -LiteralPath $uninstaller) {Remove-Item -LiteralPath $uninstaller}
    Remove-Item -LiteralPath $destination
  }
}
