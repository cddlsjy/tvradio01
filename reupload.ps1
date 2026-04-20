$ErrorActionPreference = "Continue"

Write-Host "=== 重新上传代码到 Gitee ===" -ForegroundColor Green
Write-Host "当前目录: $pwd" -ForegroundColor Yellow

# 检查目录内容
Write-Host "`n检查目录内容..." -ForegroundColor Yellow
try {
    $files = Get-ChildItem -Path . -Recurse -Force
    Write-Host "找到 $($files.Count) 个文件和目录" -ForegroundColor Green
    $files | Select-Object Name, FullName | Where-Object { $_.Name -notlike '*.git*' } | Select-Object -First 10
} catch {
    Write-Host "无法列出目录内容: $_" -ForegroundColor Red
}

# 查找 Gitrite-Host "`n查找 Git..." -ForegroundColor Yellow
$gitPaths = @(
    "C:\Program Files\Git\bin\git.exe",
    "C:\Program Files (x86)\Git\bin\git.exe",
    "C:\PortableGit\bin\git.exe",
    "$env:LOCALAPPDATA\Programs\Git\bin\git.exe"
)

$gitExe = $null
foreach ($path in $gitPaths) {
    if (Test-Path $path) {
        $gitExe = $path
        Write-Host "找到 Git: $gitExe" -ForegroundColor Green
        break
    }
}

if (-not $gitExe) {
    Write-Host "Git 未找到，尝试下载便携版..." -ForegroundColor Yellow
    try {
        Invoke-WebRequest -Uri "https://github.com/git-for-windows/git/releases/latest/download/PortableGit-2.45.2-64-bit.7z.exe" -OutFile "PortableGit.exe"
        if (Test-Path "PortableGit.exe") {
            Write-Host "下载完成，正在解压..." -ForegroundColor Yellow
            Start-Process -FilePath "./PortableGit.exe" -ArgumentList "/SILENT /DIR=c:\PortableGit" -Wait
            $gitExe = "C:\PortableGit\bin\git.exe"
            if (Test-Path $gitExe) {
                Write-Host "便携版 Git 安装成功" -ForegroundColor Green
            }
        }
    } catch {
        Write-Host "下载 Git 失败: $_" -ForegroundColor Red
    }
}

if (-not $gitExe) {
    Write-Host "无法找到或安装 Git，请手动安装后重试" -ForegroundColor Red
    exit 1
}

# 执行 Git 操作
Write-Host "`n执行 Git 操作..." -ForegroundColor Yellow

try {
    # 初始化仓库
    Write-Host "1. 初始化 Git 仓库..." -ForegroundColor Cyan
    & $gitExe init
    
    # 配置用户信息
    Write-Host "2. 配置用户信息..." -ForegroundColor Cyan
    & $gitExe config user.name "User"
    & $gitExe config user.email "user@example.com"
    
    # 添加远程仓库
    Write-Host "3. 添加远程仓库..." -ForegroundColor Cyan
    & $gitExe remote remove origin 2>&1
    & $gitExe remote add origin https://gitee.com/cddlsjy/exoradio
    & $gitExe remote -v
    
    # 添加所有文件
    Write-Host "4. 添加所有文件..." -ForegroundColor Cyan
    & $gitExe add .
    & $gitExe status
    
    # 提交更改
    Write-Host "5. 提交更改..." -ForegroundColor Cyan
    & $gitExe commit -m "重新上传所有代码" 2>&1
    
    # 重命名分支
    Write-Host "6. 重命名分支为 main..." -ForegroundColor Cyan
    & $gitExe branch -M main 2>&1
    
    # 推送到远程
    Write-Host "7. 推送到远程仓库..." -ForegroundColor Cyan
    & $gitExe push -u origin main --force 2>&1
    
    Write-Host "`n=== 上传成功！ ===" -ForegroundColor Green
    Write-Host "代码已成功上传到 https://gitee.com/cddlsjy/exoradio" -ForegroundColor Green
} catch {
    Write-Host "Git 操作失败: $_" -ForegroundColor Red
}

Write-Host "`n操作完成" -ForegroundColor Yellow
