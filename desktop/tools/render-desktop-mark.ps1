$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore,WindowsBase
$projectRoot = Split-Path -Parent $PSScriptRoot
[xml]$svg = Get-Content -LiteralPath (Join-Path $projectRoot 'ui/logo.svg') -Raw
$visual = New-Object System.Windows.Media.DrawingVisual
$drawing = $visual.RenderOpen()
$drawing.PushTransform((New-Object System.Windows.Media.ScaleTransform(5.12,5.12)))
foreach ($node in $svg.SelectNodes("//*[local-name()='path']")) {
    $owner = $node
    while ($owner -and -not ($owner.GetAttribute('style') -match 'stroke:(#[A-Fa-f0-9]+)')) { $owner = $owner.ParentNode }
    if (-not $owner) { throw 'Couleur vectorielle absente' }
    $color = $Matches[1]
    $widthOwner = $node
    while ($widthOwner -and -not $widthOwner.HasAttribute('stroke-width')) { $widthOwner = $widthOwner.ParentNode }
    $pen = New-Object System.Windows.Media.Pen ([System.Windows.Media.BrushConverter]::new().ConvertFromString($color)),([double]::Parse($widthOwner.GetAttribute('stroke-width'),[cultureinfo]::InvariantCulture))
    $pen.StartLineCap = [System.Windows.Media.PenLineCap]::Round
    $pen.EndLineCap = [System.Windows.Media.PenLineCap]::Round
    $pen.LineJoin = [System.Windows.Media.PenLineJoin]::Round
    $drawing.DrawGeometry($null,$pen,[System.Windows.Media.Geometry]::Parse($node.GetAttribute('d')))
}
$drawing.Pop()
$drawing.Close()
$bitmap = New-Object System.Windows.Media.Imaging.RenderTargetBitmap(512,512,96,96,[System.Windows.Media.PixelFormats]::Pbgra32)
$bitmap.Render($visual)
$encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
$encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
$stream = [IO.File]::Create((Join-Path $projectRoot 'ui/logo-mark.png'))
try { $encoder.Save($stream) } finally { $stream.Dispose() }
Write-Output 'Logo vectoriel converti pour Windows.'
