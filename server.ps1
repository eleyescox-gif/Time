$dir = $PSScriptRoot
if (-not $dir) { $dir = "$env:USERPROFILE\Desktop\mosque" }

$port = 8080
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:$port/")

try {
    $listener.Start()
} catch {
    Write-Host "⚠️ Port $port in use or error: $_" -ForegroundColor Yellow
    $port = 8081
    $listener = New-Object System.Net.HttpListener
    $listener.Prefixes.Add("http://localhost:$port/")
    $listener.Start()
}

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "   🕌 MOSQUE DISPLAY SERVER (মসজিদ ডিসপ্লে)       " -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "✅ Server running: http://localhost:$port" -ForegroundColor Cyan
Write-Host "   Display: http://localhost:$port/display.html" -ForegroundColor Yellow
Write-Host "   Admin:   http://localhost:$port/admin.html" -ForegroundColor Yellow
Write-Host "--------------------------------------------------" -ForegroundColor Gray
Write-Host "💡 Press Ctrl+C in this window to stop server." -ForegroundColor White
Write-Host "💡 Full screen toggle: Press F11 in your browser." -ForegroundColor White

Start-Process "http://localhost:$port/display.html"
Start-Sleep -Milliseconds 600
Start-Process "http://localhost:$port/admin.html"

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response
        
        $urlPath = $req.Url.AbsolutePath.TrimStart('/')
        if ($urlPath -eq '' -or $urlPath -eq 'display.html') { 
            $file = 'display.html' 
        } elseif ($urlPath -eq 'admin.html') { 
            $file = 'admin.html' 
        } else { 
            $res.StatusCode = 404
            $res.Close()
            continue 
        }
        
        $filePath = Join-Path $dir $file
        if (Test-Path $filePath) {
            $bytes = [System.IO.File]::ReadAllBytes($filePath)
            $res.ContentType = "text/html; charset=utf-8"
            $res.ContentLength64 = $bytes.Length
            $res.OutputStream.Write($bytes, 0, $bytes.Length)
        } else {
            $res.StatusCode = 404
        }
        $res.Close()
    }
} catch {
    # Handles stop / interruptions
} finally {
    if ($listener.IsListening) {
        $listener.Stop()
    }
    $listener.Close()
    Write-Host "`n🛑 Server stopped cleanly." -ForegroundColor Red
}
