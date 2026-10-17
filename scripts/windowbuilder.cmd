@echo off
setlocal
cd /d "%~dp0\.."
set "ECLIPSE=%CD%\.tools\eclipse-java\eclipse\eclipse.exe"
if not exist "%ECLIPSE%" (
  echo Portable Eclipse is not present in this checkout.
  echo See docs\WindowBuilder.md for installation and opening LoginPanel.java.
  exit /b 1
)
start "" "%ECLIPSE%" -data "%CD%\.tools\windowbuilder-workspace"
