@echo off
echo 检查Git安装...
if exist "C:\Program Files\Git\bin\git.exe" (
    echo Git已找到
    "C:\Program Files\Git\bin\git.exe" --version
    echo 开始Git操作...
    "C:\Program Files\Git\bin\git.exe" init
    "C:\Program Files\Git\bin\git.exe" config user.name "User"
    "C:\Program Files\Git\bin\git.exe" config user.email "user@example.com"
    "C:\Program Files\Git\bin\git.exe" remote add origin https://gitee.com/cddlsjy/exoradio
    "C:\Program Files\Git\bin\git.exe" add .
    "C:\Program Files\Git\bin\git.exe" commit -m "Initial commit"
    "C:\Program Files\Git\bin\git.exe" branch -M main
    "C:\Program Files\Git\bin\git.exe" push -u origin main --force
    echo 操作完成！
) else (
    echo Git未找到，开始下载...
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/git-for-windows/git/releases/latest/download/Git-2.45.2-64-bit.exe' -OutFile 'git-installer.exe'"
    echo 下载完成，开始安装...
    git-installer.exe /VERYSILENT /NORESTART /NOCANCEL /SP- /SUPPRESSMSGBOXES /CLOSEAPPLICATIONS /RESTARTAPPLICATIONS /COMPONENTS="icons,ext,assoc,gitlfs"
    echo 安装完成，重新执行操作...
    "C:\Program Files\Git\bin\git.exe" init
    "C:\Program Files\Git\bin\git.exe" config user.name "User"
    "C:\Program Files\Git\bin\git.exe" config user.email "user@example.com"
    "C:\Program Files\Git\bin\git.exe" remote add origin https://gitee.com/cddlsjy/exoradio
    "C:\Program Files\Git\bin\git.exe" add .
    "C:\Program Files\Git\bin\git.exe" commit -m "Initial commit"
    "C:\Program Files\Git\bin\git.exe" branch -M main
    "C:\Program Files\Git\bin\git.exe" push -u origin main --force
    echo 操作完成！
)
pause
