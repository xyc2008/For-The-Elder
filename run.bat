@echo off
rem Windows entry point for run.sh. The real launcher lives in run.sh, which is
rem written for Git Bash (sed / awk / cygpath / md5sum all come from Git for
rem Windows), so this file only locates bash.exe and forwards the arguments.
rem Keep this file ASCII-only with CRLF line endings: cmd.exe parses .bat in the
rem OEM code page, so UTF-8 Chinese in here turns into mojibake and can split
rem lines mid-command.
setlocal
set "BASH="
if exist "%ProgramFiles%\Git\bin\bash.exe" set "BASH=%ProgramFiles%\Git\bin\bash.exe"
if not defined BASH if exist "%ProgramFiles(x86)%\Git\bin\bash.exe" set "BASH=%ProgramFiles(x86)%\Git\bin\bash.exe"
if not defined BASH if exist "%LocalAppData%\Programs\Git\bin\bash.exe" set "BASH=%LocalAppData%\Programs\Git\bin\bash.exe"
if not defined BASH for /f "delims=" %%I in ('where bash 2^>nul') do if not defined BASH set "BASH=%%I"
if not defined BASH (
    echo [run.bat] bash.exe not found - install Git for Windows, or run ./run.sh inside Git Bash
    exit /b 1
)
echo [run.bat] bash: %BASH%
"%BASH%" "%~dp0run.sh" %*
exit /b %ERRORLEVEL%
