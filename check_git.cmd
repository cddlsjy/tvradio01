@echo off
echo Checking Git installation...
if exist "C:\Program Files\Git\bin\git.exe" (
    echo Git found at C:\Program Files\Git\bin\git.exe
    "C:\Program Files\Git\bin\git.exe" --version
) else if exist "C:\Program Files (x86)\Git\bin\git.exe" (
    echo Git found at C:\Program Files (x86)\Git\bin\git.exe
    "C:\Program Files (x86)\Git\bin\git.exe" --version
) else (
    echo Git not found, installing...
    powershell -Command "winget install --id Git.Git -e --accept-source-agreements --accept-package-agreements"
    echo Installation completed
    if exist "C:\Program Files\Git\bin\git.exe" (
        "C:\Program Files\Git\bin\git.exe" --version
    )
)
