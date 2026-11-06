@echo off
cd /d "%~dp0"
call mvn clean package
if errorlevel 1 exit /b 1
