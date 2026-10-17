@echo off
setlocal
cd /d "%~dp0\.."
if not "%PEER_REVIEW_LIVE_ACCEPTANCE%"=="1" (
  echo This opt-in GUI acceptance writes synthetic data to the configured database.
  echo Default mode sends one synthetic submission to AI; --review-mutations does not call AI.
  echo Read docs\live-acceptance.md and set PEER_REVIEW_LIVE_ACCEPTANCE=1 explicitly.
  exit /b 2
)
if not exist "config\application.properties" (
  echo Missing private runtime configuration.
  exit /b 1
)
call mvn -DskipTests test-compile dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/live-runtime-classpath.txt
if errorlevel 1 exit /b 1
set /p "LIVE_CP="<"target\live-runtime-classpath.txt"
set "JAVA_COMMAND=java"
if defined JAVA_HOME set "JAVA_COMMAND=%JAVA_HOME%\bin\java.exe"
"%JAVA_COMMAND%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "%CD%\target\test-classes;%CD%\target\classes;%LIVE_CP%" edu.course.peerreview.ui.LiveAcceptance "%CD%" %*
exit /b %errorlevel%
