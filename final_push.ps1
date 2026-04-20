$gitPath = "C:\Program Files\Git\bin\git.exe"
$repoUrl = "https://gitee.com/cddlsjy/exoradio"

Write-Host "=== 开始Git操作 ===" -ForegroundColor Green

# 检查Git是否存在
if (Test-Path $gitPath) {
    Write-Host "✓ Git已找到" -ForegroundColor Green
    & $gitPath --version
} else {
    Write-Host "✗ Git未找到，开始下载..." -ForegroundColor Red
    Invoke-WebRequest -Uri "https://github.com/git-for-windows/git/releases/latest/download/Git-2.45.2-64-bit.exe" -OutFile "git-installer.exe"
    Write-Host "下载完成，开始安装..." -ForegroundColor Yellow
    Start-Process -FilePath "./git-installer.exe" -ArgumentList "/VERYSILENT /NORESTART /NOCANCEL /SP- /SUPPRESSMSGBOXES /CLOSEAPPLICATIONS /RESTARTAPPLICATIONS /COMPONENTS=\"icons,ext,assoc,gitlfs\"" -Wait
    Write-Host "安装完成" -ForegroundColor Green
}

# 初始化Git仓库
Write-Host "`n1. 初始化Git仓库..." -ForegroundColor Yellow
& $gitPath init

# 配置用户信息
Write-Host "2. 配置用户信息..." -ForegroundColor Yellow
& $gitPath config user.name "User"
& $gitPath config user.email "user@example.com"

# 添加远程仓库
Write-Host "3. 添加远程仓库..." -ForegroundColor Yellow
& $gitPath remote remove origin 2>&1
& $gitPath remote add origin $repoUrl
& $gitPath remote -v

# 添加所有文件
Write-Host "4. 添加所有文件..." -ForegroundColor Yellow
& $gitPath add .
& $gitPath status

# 提交更改
Write-Host "5. 提交更改..." -ForegroundColor Yellow
& $gitPath commit -m "Initial commit with all project files" 2>&1

# 重命名分支
Write-Host "6. 重命名分支..." -ForegroundColor Yellow
& $gitPath branch -M main 2>&1

# 推送到远程仓库
Write-Host "7. 推送到远程仓库..." -ForegroundColor Yellow
& $gitPath push -u origin main --force 2>&1

Write-Host "`n=== 操作完成 ===" -ForegroundColor Green
