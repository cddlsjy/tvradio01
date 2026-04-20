$ErrorActionPreference = "Continue"
$gitPath = "C:\Program Files\Git\bin\git.exe"

Write-Host "=== Git测试脚本 ===" -ForegroundColor Cyan
Write-Host "Git路径: $gitPath" -ForegroundColor Yellow

# 检查Git是否存在
if (Test-Path $gitPath) {
    Write-Host "✓ Git已找到" -ForegroundColor Green
} else {
    Write-Host "✗ Git未找到" -ForegroundColor Red
    exit 1
}

Write-Host "`n1. 检查当前目录..." -ForegroundColor Yellow
Get-ChildItem -Force

Write-Host "`n2. 初始化Git仓库..." -ForegroundColor Yellow
& $gitPath init 2>&1

Write-Host "`n3. 配置用户..." -ForegroundColor Yellow
& $gitPath config user.name "User" 2>&1
& $gitPath config user.email "user@example.com" 2>&1

Write-Host "`n4. 添加远程仓库..." -ForegroundColor Yellow
& $gitPath remote remove origin 2>&1
& $gitPath remote add origin https://gitee.com/cddlsjy/exoradio 2>&1
& $gitPath remote -v

Write-Host "`n5. 添加文件..." -ForegroundColor Yellow
& $gitPath add . 2>&1
& $gitPath status

Write-Host "`n6. 提交..." -ForegroundColor Yellow
& $gitPath commit -m "Initial commit" 2>&1

Write-Host "`n7. 重命名分支..." -ForegroundColor Yellow
& $gitPath branch -M main 2>&1

Write-Host "`n8. 推送..." -ForegroundColor Yellow
& $gitPath push -u origin main --force 2>&1

Write-Host "`n=== 完成 ===" -ForegroundColor Green
