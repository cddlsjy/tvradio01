@echo off
echo Current directory: %cd%
echo Files in current directory:
dir /a
echo.
echo Files in app directory:
dir /a app 2>nul
echo.
echo Trying to find Git...
if exist "C:\Program Files\Git\bin\git.exe" (
    echo Git found at C:\Program Files\Git\bin\git.exe
    set GIT="C:\Program Files\Git\bin\git.exe"
) else if exist "C:\Program Files (x86)\Git\bin\git.exe" (
    echo Git found at C:\Program Files (x86)\Git\bin\git.exe
    set GIT="C:\Program Files (x86)\Git\bin\git.exe"
) else if exist "C:\PortableGit\bin\git.exe" (
    echo Git found at C:\PortableGit\bin\git.exe
    set GIT="C:\PortableGit\bin\git.exe"
) else (
    echo Git not found, trying to download...
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/git-for-windows/git/releases/latest/download/PortableGit-2.45.2-64-bit.7z.exe' -OutFile 'PortableGit.exe'"
    if exist "PortableGit.exe" (
        echo PortableGit downloaded, extracting...
        PortableGit.exe /SILENT /DIR="c:\PortableGit"
        set GIT="C:\PortableGit\bin\git.exe"
    ) else (
        echo Failed to download Git
        pause
        exit /b 1
    )
)
echo.
echo Initializing Git repository...
%GIT% init
echo Configuring Git...
%GIT% config user.name "User"
%GIT% config user.email "user@example.com"
echo Adding remote repository...
%GIT% remote add origin https://gitee.com/cddlsjy/exoradio
echo Adding all files...
%GIT% add .
echo Committing changes...
%GIT% commit -m "Re-upload all code"
echo Renaming branch to main...
%GIT% branch -M main
echo Pushing to remote repository...
%GIT% push -u origin main --force
echo.
echo Upload completed!
pause
