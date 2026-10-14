@echo off
setlocal
set "GRADLE_VERSION=8.9"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "INSTALL_DIR=%GRADLE_USER_HOME%\file-navigator-gradle\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%INSTALL_DIR%\bin\gradle.bat"
if exist "%GRADLE_BIN%" goto runGradle
set "TMP_DIR=%TEMP%\file-navigator-gradle-%RANDOM%"
mkdir "%TMP_DIR%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%TMP_DIR%\gradle.zip'"
if errorlevel 1 goto failed
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%TMP_DIR%\gradle.zip' -DestinationPath '%TMP_DIR%' -Force"
if errorlevel 1 goto failed
powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force -Path '%GRADLE_USER_HOME%\file-navigator-gradle' | Out-Null; if (Test-Path '%INSTALL_DIR%') { Remove-Item '%INSTALL_DIR%' -Recurse -Force }; Move-Item -Path '%TMP_DIR%\gradle-%GRADLE_VERSION%' -Destination '%INSTALL_DIR%' -Force"
if errorlevel 1 goto failed
rmdir /s /q "%TMP_DIR%"
:runGradle
call "%GRADLE_BIN%" %*
exit /b %errorlevel%
:failed
set "ERR=%errorlevel%"
echo Failed to download or extract Gradle %GRADLE_VERSION%. 1>&2
if exist "%TMP_DIR%" rmdir /s /q "%TMP_DIR%"
exit /b %ERR%
