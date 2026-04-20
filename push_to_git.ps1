$gitPath = "C:\Program Files\Git\bin\git.exe"
$repoUrl = "https://gitee.com/cddlsjy/exoradio"

Write-Host "=== 开始Git操作 ===" -ForegroundColor Green

# 初始化Git仓库
Write-Host "1. 初始化Git仓库..." -ForegroundColor Yellow
& $gitPath init

# 配置用户信息
Write-Host "2. 配置Git用户信息..." -ForegroundColor Yellow
& $gitPath config user.name "User"
& $gitPath config user.email "user@example.com"

# 添加远程仓库
Write-Host "3. 添加远程仓库..." -ForegroundColor Yellow
& $gitPath remote add origin $repoUrl

# 添加所有文件
Write-Host "4. 添加所有文件..." -ForegroundColor Yellow
& $gitPath add .

# 提交更改
Write-Host "5. 提交更改..." -ForegroundColor Yellow
& $gitPath commit -m "Initial commit with all project files"

# 重命名分支为main
Write-Host "6. 重命名分支为main..." -ForegroundColor Yellow
& $gitPath branch -M main

# 推送到远程仓库
Write-Host "7. 推送到远程仓库..." -ForegroundColor Yellow
& $gitPath push -u origin main --force

Write-Host "=== 操作完成 ===" -ForegroundColor Green
