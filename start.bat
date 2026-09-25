@echo off
title Mosque Display Server
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "server.ps1"
pause
