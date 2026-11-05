@echo off
cd /d "%~dp0"
if not exist "client\target\client-1.0.0.jar" (
  echo Build the project first: build.cmd
  exit /b 1
)
java -jar "client\target\client-1.0.0.jar"
