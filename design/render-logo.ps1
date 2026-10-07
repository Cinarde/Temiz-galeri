# Render the Android vector source without changing its geometry or palette.
Add-Type -AssemblyName PresentationCore,WindowsBase
$workspace = Split-Path -Parent $PSScriptRoot
[xml]$vector = Get-Content -Raw (Join-Path $workspace 'app/src/main/res/drawable/ic_launcher_foreground.xml')
$ns = 'http://schemas.android.com/apk/res/android'
function Export-Logo([string]$destination, [int]$size, [bool]$round) {
    $visual = New-Object System.Windows.Media.DrawingVisual
    $drawing = $visual.RenderOpen()
    $drawing.PushTransform((New-Object System.Windows.Media.ScaleTransform ($size / 108.0),($size / 108.0)))
    $background = [System.Windows.Media.BrushConverter]::new().ConvertFromString('#315E47')
    $radius = if ($round) { 54 } else { 24 }
    $drawing.DrawRoundedRectangle($background, $null, [System.Windows.Rect]::new(0,0,108,108), $radius, $radius)
    foreach ($path in $vector.vector.path) {
        $brush = [System.Windows.Media.BrushConverter]::new().ConvertFromString($path.GetAttribute('fillColor',$ns))
        $geometry = [System.Windows.Media.Geometry]::Parse($path.GetAttribute('pathData',$ns))
        $drawing.DrawGeometry($brush,$null,$geometry)
    }
    $drawing.Pop()
    $drawing.Close()
    $bitmap = [System.Windows.Media.Imaging.RenderTargetBitmap]::new($size,$size,96,96,[System.Windows.Media.PixelFormats]::Pbgra32)
    $bitmap.Render($visual)
    $encoder = [System.Windows.Media.Imaging.PngBitmapEncoder]::new()
    $encoder.Frames.Add([System.Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
    $stream = [System.IO.File]::Create($destination)
    try { $encoder.Save($stream) } finally { $stream.Dispose() }
}
Export-Logo (Join-Path $PSScriptRoot 'galerini-temizle-logo.png') 512 $false
$densitySizes = @{ mdpi=48; hdpi=72; xhdpi=96; xxhdpi=144; xxxhdpi=192 }
foreach ($density in $densitySizes.Keys) {
    $folder = Join-Path $workspace "app/src/main/res/mipmap-$density"
    Export-Logo (Join-Path $folder 'ic_launcher.png') $densitySizes[$density] $false
    Export-Logo (Join-Path $folder 'ic_launcher_round.png') $densitySizes[$density] $true
}
