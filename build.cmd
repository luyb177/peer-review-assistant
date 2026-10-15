@echo off
setlocal
cd /d "%~dp0"
if exist "mvnw.cmd" (
  call mvnw.cmd clean package %*
) else (
  call mvn clean package %*
)
if errorlevel 1 (
  echo Build failed. Check Java 17+, network and Maven output.
  exit /b 1
)
echo Submission ZIP is in target\peer-review-assistant-submission.zip
