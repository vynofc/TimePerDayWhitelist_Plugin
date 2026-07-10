@echo off
setlocal

cd /d "%~dp0"

echo [1/2] Cleaning old build...
call mvn clean
if errorlevel 1 goto :fail

echo [2/2] Building plugin...
call mvn package
if errorlevel 1 goto :fail

echo.
echo Build finished. Artifact(s):
dir /b target\*.jar
exit /b 0

:fail
echo.
echo Build failed with exit code %errorlevel%.
exit /b %errorlevel%
