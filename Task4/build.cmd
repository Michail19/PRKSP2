@echo off
cd /d "%~dp0"
call mvn clean package -DskipTests
if errorlevel 1 (
  echo [ERROR] Maven build failed.
  exit /b 1
)
echo Build OK: server\target\server-1.0.0.jar and client\target\client-1.0.0.jar
