@echo off
echo Starting Git operations...
"C:\Program Files\Git\bin\git.exe" init
"C:\Program Files\Git\bin\git.exe" config user.name "User"
"C:\Program Files\Git\bin\git.exe" config user.email "user@example.com"
"C:\Program Files\Git\bin\git.exe" remote add origin https://gitee.com/cddlsjy/exoradio
"C:\Program Files\Git\bin\git.exe" add .
"C:\Program Files\Git\bin\git.exe" commit -m "Initial commit"
"C:\Program Files\Git\bin\git.exe" branch -M main
"C:\Program Files\Git\bin\git.exe" push -u origin main --force
echo Done!
pause
