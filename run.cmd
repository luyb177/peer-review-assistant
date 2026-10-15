@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java 17+ is required. Set JAVA_HOME and add JAVA_HOME\bin to PATH.
  pause
  exit /b 1
)
if exist "bin\peer-review-assistant.jar" (
  java -Dfile.encoding=UTF-8 -jar "bin\peer-review-assistant.jar" %*
) else if exist "target\peer-review-assistant.jar" (
  java -Dfile.encoding=UTF-8 -jar "target\peer-review-assistant.jar" %*
) else (
  echo JAR not found. Run build.cmd first, or extract the submission ZIP.
  pause
  exit /b 1
)
