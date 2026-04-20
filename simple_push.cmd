@echo off
set GIT_PATH="C:\Program Files\Git\bin\git.exe"

echo 初始化Git仓库...
%GIT_PATH% init

echo 配置用户信息...
%GIT_PATH% config user.name "User"
%GIT_PATH% config user.email "user@example.com"

echo 添加远程仓库...
%GIT_PATH% remote add origin https://gitee.com/cddlsjy/exoradio

echo 添加所有文件...
%GIT_PATH% add .

echo 提交更改...
%GIT_PATH% commit -m "Initial commit with all project files"

echo 重命名分支...
%GIT_PATH% branch -M main

echo 推送到远程仓库...
%GIT_PATH% push -u origin main --force

echo 操作完成！
