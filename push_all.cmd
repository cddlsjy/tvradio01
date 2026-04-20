@echo off
set GIT="C:\Program Files\Git\bin\git.exe"

%GIT% init
%GIT% config user.name "User"
%GIT% config user.email "user@example.com"
%GIT% remote add origin https://gitee.com/cddlsjy/exoradio
%GIT% add .
%GIT% commit -m "Initial commit"
%GIT% branch -M main
%GIT% push -u origin main --force
