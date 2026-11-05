@echo off
cd /d "%~dp0"
if not exist "server\target\server-1.0.0.jar" (
  echo Build the project first: build.cmd
  exit /b 1
)
java -jar "server\target\server-1.0.0.jar"
