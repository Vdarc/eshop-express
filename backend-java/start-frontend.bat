@echo off
call "%~dp0env.bat"
cd /d "%TS%\frontend"
echo [frontend] vite dev server on 20682 ...
call npm run dev
pause
