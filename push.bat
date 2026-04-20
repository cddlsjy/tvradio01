@echo off
cd /d d:\dev2026\ljkradiosdk19-code
echo === 开始Git操作 ===
"C:\Program Files\Git\bin\git.exe" init
"C:\Program Files\Git\bin\git.exe" config user.name "User"
"C:\Program Files\Git\bin\git.exe" config user.email "user@example.com"
"C:\Program Files\Git\bin\git.exe" remote add origin https://gitee.com/cddlsjy/exoradio
"C:\Program Files\Git\bin\git.exe" add .
"C:\Program Files\Git\bin\git.exe" commit -m "Initial commit with all project files"
"C:\Program Files\Git\bin\git.exe" branch -M main
"C:\Program Files\Git\bin\git.exe" push -u origin main --force
echo === 操作完成 ===
pause
