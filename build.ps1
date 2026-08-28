# Poketoilet 一键构建脚本（PowerShell 版，替代原 build.bat）
#
# 用法（PowerShell 默认禁止直接双击运行 .ps1，任选其一）：
#   1. 在 PowerShell 里执行：  powershell -ExecutionPolicy Bypass -File build.ps1
#   2. 右键 build.ps1 -> "使用 PowerShell 运行"
#
# 依赖：
#   - JDK 21（路径见下方 $env:JAVA_HOME，如变化请修改）
#   - 网络代理：Gradle 走 gradle.properties 里的 127.0.0.1:7890 代理
#     （本机命令行直连 maven.neoforged.net 不通，已实测；Clash 需在运行。
#      若网络环境变了，同步修改 gradle.properties 里 org.gradle.jvmargs 的代理参数）

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$env:JAVA_HOME = "C:\Users\user\.jdks\ms-21.0.9"

Write-Host "==> 开始构建 poketoilet（build + installToInstance）..." -ForegroundColor Cyan
& .\gradlew.bat build installToInstance --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "==> 构建失败，请查看上面的报错信息。" -ForegroundColor Red
    exit $LASTEXITCODE
}

$jar = Join-Path $PSScriptRoot "build\libs\poketoilet-1.0.0.jar"
if (-not (Test-Path $jar)) {
    Write-Host "==> 构建产物不存在：$jar" -ForegroundColor Red
    exit 1
}

Write-Host "==> 构建成功，已安装进整合包 mods：" -ForegroundColor Green
Write-Host "    $jar"
Write-Host "    启动游戏后在日志里搜 [Poketoilet] 确认加载。"
