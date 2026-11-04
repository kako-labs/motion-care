$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repoRoot 'demo\mock-data-ui'
$sourceHtml = Join-Path $sourceDir 'index.html'
$sourceCss = Join-Path $sourceDir 'style.css'
$sourceJs = Join-Path $sourceDir 'app.js'
$outputHtml = Join-Path $repoRoot 'demo\motion-care-demo.html'

$html = Get-Content -LiteralPath $sourceHtml -Raw -Encoding utf8
$css = Get-Content -LiteralPath $sourceCss -Raw -Encoding utf8
$js = Get-Content -LiteralPath $sourceJs -Raw -Encoding utf8

$html = $html.Replace(
  '<link rel="stylesheet" href="style.css">',
  "<style>`r`n$css`r`n</style>"
)
$html = $html.Replace(
  '<script src="app.js"></script>',
  "<script>`r`n$js`r`n</script>"
)

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($outputHtml, $html, $utf8NoBom)
Write-Host "Standalone demo created: $outputHtml"
