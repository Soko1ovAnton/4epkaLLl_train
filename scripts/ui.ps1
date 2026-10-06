param(
    [Parameter(Mandatory=$true)][string]$Action,
    [string]$Target = "",
    [string]$Arg2 = ""
)
$adb = "C:\Users\an1on\Desktop\4epkaLll_train\.sdk\platform-tools\adb.exe"
$null = & $adb shell uiautomator dump /sdcard/ui.xml 2>&1
$raw = & $adb shell cat /sdcard/ui.xml 2>$null | Out-String
# adb adds \r and wrapping; clean
$raw = $raw -replace "`r", "" -replace "\n`$", ""
$xmlText = [string]::Join("", ($raw -split "`n"))
$doc = New-Object System.Xml.XmlDocument
$doc.LoadXml($xmlText) | Out-Null

function Get-Bounds($node) {
    $b = $node.GetAttribute("bounds") # [x1,y1][x2,y2]
    if ($b -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        return @{ x1=[int]$Matches[1]; y1=[int]$Matches[2]; x2=[int]$Matches[3]; y2=[int]$Matches[4] }
    }
    return $null
}
function Tap-Center($b) {
    $x = [int](($b.x1 + $b.x2) / 2); $y = [int](($b.y1 + $b.y2) / 2)
    & $adb shell input tap $x $y
}

$all = $doc.GetElementsByTagName("node")

switch ($Action) {
    "texts" {
        $all | ForEach-Object { $t = $_.GetAttribute("text"); if ($t) { "$t  =>  $($_.GetAttribute('bounds'))" } }
    }
    "tap-text" {
        $node = $all | Where-Object { $_.GetAttribute("text") -eq $Target } | Select-Object -First 1
        if (-not $node) { $node = $all | Where-Object { $_.GetAttribute("text") -like "*$Target*" } | Select-Object -First 1 }
        if ($node) { Tap-Center (Get-Bounds $node); "tapped: $($node.GetAttribute('text'))" } else { "NOT FOUND: $Target" }
    }
    "tap-class" {
        $node = $all | Where-Object { $_.GetAttribute("class") -eq $Target } | Select-Object -First 1
        if ($node) { Tap-Center (Get-Bounds $node); "tapped class: $Target" } else { "NOT FOUND class: $Target" }
    }
    "tap-id" {
        $full = "com.chepkall.gymlog:id/$Target"
        $node = $all | Where-Object { $_.GetAttribute("resource-id") -eq $full } | Select-Object -First 1
        if ($node) { Tap-Center (Get-Bounds $node); "tapped id: $Target" } else { "NOT FOUND id: $Target" }
    }
    "set-text" {
        # Target = resource-id short, Arg2 = value; taps field, clears, sends text (ASCII only)
        $full = "com.chepkall.gymlog:id/$Target"
        $node = $all | Where-Object { $_.GetAttribute("resource-id") -eq $full } | Select-Object -First 1
        if ($node) {
            Tap-Center (Get-Bounds $node)
            Start-Sleep -Milliseconds 400
            & $adb shell input keyevent --longpress KEYCODE_MOVE_END
            1..20 | ForEach-Object { & $adb shell input keyevent KEYCODE_DEL | Out-Null }
            if ($Arg2 -ne "") { & $adb shell input text $Arg2 }
            Start-Sleep -Milliseconds 200
            & $adb shell input keyevent 111  # ESC to close IME focus popup if any
            "set $Target = $Arg2"
        } else { "NOT FOUND id: $Target" }
    }
    "dump" {
        $all | ForEach-Object {
            $t = $_.GetAttribute("text"); $id = $_.GetAttribute("resource-id") -replace 'com\.chepkall\.gymlog:id/', ''
            if ($t -or $id) { "$($id): $t" }
        } | Select-Object -First 80
    }
}
