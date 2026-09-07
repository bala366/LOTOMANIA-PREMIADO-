@echo off
setlocal
cd /d "%~dp0"
echo === LOTOMANIA CICLO E AUSENTES V4 ===
if exist settings.gradle (echo OK settings.gradle) else (echo FALTA settings.gradle)
if exist build.gradle (echo OK build.gradle) else (echo FALTA build.gradle)
if exist app\build.gradle (echo OK app\build.gradle) else (echo FALTA app\build.gradle)
if exist app\src\main\AndroidManifest.xml (echo OK AndroidManifest.xml) else (echo FALTA AndroidManifest.xml)
if exist app\src\main\java\com\lotomania\ciclo\MainActivity.java (echo OK MainActivity.java) else (echo FALTA MainActivity.java)
if exist .github\workflows\build-apk.yml (echo OK workflow GitHub) else (echo FALTA workflow GitHub)
pause
