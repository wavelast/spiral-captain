@echo off
setlocal

for %%I in ("%~dp0..\..") do set ROOT=%%~fI\
set BUILD=%~dp0
set STAGE=%ROOT%target\package-input
set BUNDLE=%ROOT%target\portable
set INSTALLER=%ROOT%target\installer
set DIST=%ROOT%dist
set VERSION=1.0.0
if not "%~1"=="" set VERSION=%~1
set ICONS=%ROOT%app\src\main\resources\icons
set ICON=%ROOT%target\spiral-captain.ico
set PORTABLE_EXE=SpiralCaptain-%VERSION%-portable.exe
set SETUP_MSI=SpiralCaptain-%VERSION%-x64-setup.msi
set UPGRADE_UUID=0FCD21FB-A16E-4096-844D-40C24AF1EDF2
set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
set PATH=%PATH%;%USERPROFILE%\.dotnet\tools

tasklist /fi "imagename eq Spiral Captain.exe" 2>nul | findstr /i /c:"Spiral Captain.exe" >nul
if not errorlevel 1 (
  echo Spiral Captain is running. Close it first, then run package.cmd again.
  exit /b 1
)

echo === Building ===
call mvn -q -f "%ROOT%pom.xml" package -DskipTests
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%"
copy /y "%ROOT%app\target\spiral-captain.jar" "%STAGE%\" >nul
copy /y "%ROOT%prefs\target\spiral-captain-prefs-0.1.0-SNAPSHOT.jar" "%STAGE%\spiral-captain-prefs.jar" >nul

echo === Icon ===
powershell -NoProfile -Command "$ErrorActionPreference = 'Stop'; $sizes = 16,20,24,32,40,48,64,128,256; $pngs = New-Object 'Collections.Generic.List[byte[]]'; foreach ($size in $sizes) { $pngs.Add([IO.File]::ReadAllBytes('%ICONS%\icon-' + $size + '.png')) }; $bytes = New-Object IO.MemoryStream; $out = New-Object IO.BinaryWriter($bytes); $out.Write([uint16]0); $out.Write([uint16]1); $out.Write([uint16]$sizes.Count); $offset = 6 + 16 * $sizes.Count; for ($i = 0; $i -lt $sizes.Count; $i++) { $side = [byte]($sizes[$i] -band 255); $out.Write($side); $out.Write($side); $out.Write([byte]0); $out.Write([byte]0); $out.Write([uint16]1); $out.Write([uint16]32); $out.Write([uint32]$pngs[$i].Length); $out.Write([uint32]$offset); $offset += $pngs[$i].Length }; foreach ($png in $pngs) { $out.Write($png) }; [IO.File]::WriteAllBytes('%ICON%', $bytes.ToArray())"
if errorlevel 1 (
  echo Could not build the icon.
  exit /b 1
)

echo === Packaging ===
if exist "%DIST%\Spiral Captain" rmdir /s /q "%DIST%\Spiral Captain"
jpackage --type app-image ^
  --name "Spiral Captain" ^
  --app-version %VERSION% ^
  --vendor "wavelast" ^
  --description "Spiral Captain" ^
  --icon "%ICON%" ^
  --input "%STAGE%" ^
  --main-jar spiral-captain.jar ^
  --main-class com.spiralcaptain.app.Launcher ^
  --java-options "--enable-native-access=ALL-UNNAMED" ^
  --add-modules java.base,java.desktop,java.prefs,java.logging,java.xml,jdk.unsupported,jdk.jfr ^
  --jlink-options "--strip-debug --no-man-pages --no-header-files --compress=zip-9" ^
  --dest "%DIST%"
if errorlevel 1 (
  echo Packaging failed.
  exit /b 1
)

echo === Portable exe ===
if exist "%BUNDLE%" rmdir /s /q "%BUNDLE%"
mkdir "%BUNDLE%"
powershell -NoProfile -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; [IO.Compression.ZipFile]::CreateFromDirectory('%DIST%\Spiral Captain', '%BUNDLE%\app.zip', 'Optimal', $false); (Get-FileHash '%BUNDLE%\app.zip' -Algorithm SHA256).Hash | Set-Content -NoNewline -Encoding ascii '%BUNDLE%\build.txt'"
if errorlevel 1 (
  echo Could not bundle the app for the portable exe.
  exit /b 1
)
"%CSC%" /nologo /target:winexe /platform:x64 /optimize+ ^
  /out:"%DIST%\%PORTABLE_EXE%" ^
  /win32icon:"%ICON%" ^
  /resource:"%BUNDLE%\app.zip",app.zip ^
  /resource:"%BUNDLE%\build.txt",build.txt ^
  /reference:System.IO.Compression.dll ^
  /reference:System.IO.Compression.FileSystem.dll ^
  /reference:System.Windows.Forms.dll ^
  /reference:System.Drawing.dll ^
  "%BUILD%Portable.cs"
if errorlevel 1 (
  echo Could not build the portable exe.
  exit /b 1
)

echo === Installer ===
where wix >nul 2>nul
if errorlevel 1 (
  echo WiX is not installed, so the installer was skipped. Install it with:
  echo   dotnet tool install --global wix --version 5.0.2
  echo   wix extension add --global WixToolset.UI.wixext/5.0.2
  echo   wix extension add --global WixToolset.Util.wixext/5.0.2
  goto done
)
if exist "%INSTALLER%" rmdir /s /q "%INSTALLER%"
jpackage --type msi ^
  --app-image "%DIST%\Spiral Captain" ^
  --name "Spiral Captain" ^
  --app-version %VERSION% ^
  --vendor "wavelast" ^
  --description "Spiral Captain" ^
  --icon "%ICON%" ^
  --win-dir-chooser ^
  --win-menu ^
  --win-menu-group "Spiral Captain" ^
  --win-shortcut ^
  --win-shortcut-prompt ^
  --win-upgrade-uuid %UPGRADE_UUID% ^
  --resource-dir "%BUILD%installer" ^
  --dest "%INSTALLER%"
if errorlevel 1 (
  echo Could not build the installer.
  exit /b 1
)
for %%M in ("%INSTALLER%\*.msi") do copy /y "%%M" "%DIST%\%SETUP_MSI%" >nul

:done
echo.
echo Done:
echo   "%DIST%\Spiral Captain\Spiral Captain.exe"
echo   "%DIST%\%PORTABLE_EXE%"
if exist "%DIST%\%SETUP_MSI%" echo   "%DIST%\%SETUP_MSI%"
