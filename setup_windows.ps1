$ErrorActionPreference = "Stop"

$oneDrive = $env:OneDrive
if ([string]::IsNullOrWhiteSpace($oneDrive)) {
    Write-Host "OneDrive가 설정되어 있지 않습니다. Windows에서 OneDrive에 먼저 로그인하세요." -ForegroundColor Yellow
    exit 1
}

$folder = Join-Path $oneDrive "BlackWalnutNotes"
$file = Join-Path $folder "home.md"
New-Item -ItemType Directory -Path $folder -Force | Out-Null

if (-not (Test-Path $file)) {
@"
# 기본함

- [ ] 오늘 할 일
- [x] 완료한 항목
- 일반 메모

**중요한 내용**

<span style="color:#D1AE6F">베이지 강조 문장</span>
"@ | Set-Content -Path $file -Encoding UTF8
}

Write-Host "생성 완료: $file" -ForegroundColor Green
Start-Process notepad.exe $file
