# PaddleOCR 模型下载脚本
# 将 det/rec/cls 模型与 ppocr_keys_v1.txt 下载到 app/src/main/assets/paddle_ocr/

$ErrorActionPreference = "Stop"
$BaseDir = $PSScriptRoot
$AssetsDir = Join-Path $BaseDir "app\src\main\assets\paddle_ocr"
$TempDir = Join-Path $Env:TEMP "paddle_ocr_download"

if (-not (Test-Path $AssetsDir)) {
    New-Item -ItemType Directory -Path $AssetsDir -Force
}
if (-not (Test-Path $TempDir)) {
    New-Item -ItemType Directory -Path $TempDir -Force
}

$Urls = @{
    det = "https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_det_infer.tar"
    rec = "https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_rec_infer.tar"
    cls = "https://paddleocr.bj.bcebos.com/dygraph_v2.0/ch/ch_ppocr_mobile_v2.0_cls_infer.tar"
    keys = "https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/ppocr/utils/ppocr_keys_v1.txt"
}

Write-Host "1/4 下载 ppocr_keys_v1.txt ..."
Invoke-WebRequest -Uri $Urls.keys -OutFile (Join-Path $AssetsDir "ppocr_keys_v1.txt") -UseBasicParsing

foreach ($name in @("det", "rec", "cls")) {
    $tarPath = Join-Path $TempDir "$name.tar"
    Write-Host "2/4 下载 $name 模型..."
    Invoke-WebRequest -Uri $Urls[$name] -OutFile $tarPath -UseBasicParsing
    Write-Host "3/4 解压 $name ..."
    $extractDir = Join-Path $TempDir $name
    if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
    New-Item -ItemType Directory -Path $extractDir -Force
    tar -xf $tarPath -C $extractDir
    $subDir = Get-ChildItem $extractDir -Directory | Select-Object -First 1
    if (-not $subDir) { $subDir = $extractDir } else { $subDir = $subDir.FullName }
    $pdmodel = Join-Path $subDir "inference.pdmodel"
    $pdiparams = Join-Path $subDir "inference.pdiparams"
    if (Test-Path $pdmodel) {
        Copy-Item $pdmodel (Join-Path $AssetsDir "$name.pdmodel") -Force
        Copy-Item $pdiparams (Join-Path $AssetsDir "$name.pdiparams") -Force
        Write-Host "  已复制 $name.pdmodel / $name.pdiparams 到 assets/paddle_ocr/"
    } else {
        Write-Warning "  未找到 inference.pdmodel，请检查解压目录: $subDir"
    }
}

Write-Host "4/4 清理临时目录..."
Remove-Item $TempDir -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "完成。模型已放入: $AssetsDir"
