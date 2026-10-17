@echo off
setlocal
cd /d "%~dp0"
set "JAVA_COMMAND=java"
if exist "runtime\bin\java.exe" (
  set "JAVA_COMMAND=%CD%\runtime\bin\java.exe"
) else if defined JAVA_HOME (
  set "JAVA_COMMAND=%JAVA_HOME%\bin\java.exe"
) else (
  where java >nul 2>nul
  if errorlevel 1 (
    echo Java 17+ is required. Use the Windows portable package with its bundled runtime.
    pause
    exit /b 1
  )
)
if exist "bin\peer-review-assistant.jar" (
  "%JAVA_COMMAND%" -Dfile.encoding=UTF-8 -jar "bin\peer-review-assistant.jar" --config "%CD%\config\application.properties" %*
) else if exist "target\peer-review-assistant.jar" (
  "%JAVA_COMMAND%" -Dfile.encoding=UTF-8 -jar "target\peer-review-assistant.jar" --config "%CD%\config\application.properties" %*
) else (
  echo JAR not found. Run build.cmd first, or extract the portable package.
  pause
  exit /b 1
)
