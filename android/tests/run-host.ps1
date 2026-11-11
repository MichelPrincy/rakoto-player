$ErrorActionPreference='Stop'
$androidRoot=Split-Path -Parent $PSScriptRoot
$main=Join-Path $androidRoot 'app\src\main\java\com\rakotoplayer\android'
$classes=Join-Path $androidRoot 'build\host-tests\classes'
$data=Join-Path $androidRoot 'build\host-tests\data'
$api=Join-Path $env:ANDROID_HOME 'platforms\android-36\android.jar'
[void](New-Item -ItemType Directory -Path $classes -Force)
$sources=@((Join-Path $main 'VaultFile.java'),(Join-Path $main 'Library.java'),(Join-Path $main 'MediaEntry.java'),(Join-Path $PSScriptRoot 'VaultFileTest.java'),(Join-Path $PSScriptRoot 'LibrarySortTest.java'))
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -encoding UTF-8 -cp $api -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Compilation des tests en échec.'}
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -cp "$classes;$api" VaultFileTest $data
if($LASTEXITCODE -ne 0){throw 'Validation du chiffrement en échec.'}
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -cp "$classes;$api" LibrarySortTest
if($LASTEXITCODE -ne 0){throw 'Validation des tris en échec.'}
