# Poketoilet 一键构建脚本（PowerShell 版）
#
# 用法（PowerShell 默认禁止直接双击运行 .ps1，任选其一）：
#   1. 在 PowerShell 里执行：  powershell -ExecutionPolicy Bypass -File build.ps1
#   2. 右键 build.ps1 -> "使用 PowerShell 运行"
#
# 依赖：
#   - JDK 21（路径见下方 $env:JAVA_HOME，如变化请修改）
#   - 网络代理：Gradle 走 gradle.properties 里的 127.0.0.1:7890 代理
#     （本机命令行直连 maven.neoforged.net 不通，已实测；Clash 需在运行）
#
# 构建顺序：cobblemon-ext（库）→ poketoilet（主模组）
# cobblemon-ext 的构建会自动把 jar 同步到 dev/libs 供 poketoilet 编译引用，
# 并安装进整合包 mods 目录。

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$env:JAVA_HOME = "C:\Users\user\.jdks\ms-21.0.9"

Write-Host "==> [1/2] 构建 cobblemon-ext（库）..." -ForegroundColor Cyan
Push-Location cobblemon-ext
& .\gradlew.bat build installToInstance --console=plain
$extExit = $LASTEXITCODE
Pop-Location
if ($extExit -ne 0) {
    Write-Host "==> cobblemon-ext 构建失败。" -ForegroundColor Red
    exit $extExit
}

Write-Host "==> [2/2] 构建 poketoilet（主模组）..." -ForegroundColor Cyan
& .\gradlew.bat build installToInstance --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "==> poketoilet 构建失败，请查看上面的报错信息。" -ForegroundColor Red
    exit $LASTEXITCODE
}

$jar = Join-Path $PSScriptRoot "build\libs\poketoilet-1.0.0.jar"
if (-not (Test-Path $jar)) {
    Write-Host "==> 构建产物不存在：$jar" -ForegroundColor Red
    exit 1
}

Write-Host "==> 全部构建成功，已安装进整合包 mods：" -ForegroundColor Green
Write-Host "    cobblemon-ext-1.0.0.jar"
Write-Host "    poketoilet-1.0.0.jar"
Write-Host "    启动游戏后在日志里搜 [Poketoilet] / [cobblemon-ext] 确认加载。"
