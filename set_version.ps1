param([string]$version)
if (-not $version) {
    Write-Error "Version tag is required."
    exit 1
}
$file = "src/chatreseau/util/UpdateManager.java"
if (-not (Test-Path $file)) {
    Write-Error "File not found: $file"
    exit 1
}
$content = Get-Content $file -Raw
$newContent = [regex]::Replace($content, 'CURRENT_VERSION\s*=\s*"[^"]*"', "CURRENT_VERSION = `"$version`"")
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($file, $newContent, $utf8NoBom)
Write-Host "Version mis a jour avec succes (sans BOM) : $version"
