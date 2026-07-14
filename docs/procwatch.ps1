# Watches the Billiards Java process and prints CPU/memory/thread usage once per
# second. Detection uses general app markers so it works for Gradle runs, packaged
# billiard-viewer.jar launches, and Abdul/NiShan/Suryansh checkout paths.
$Keywords = @(
    "billiards.viewer.Main",
    "billiard-viewer",
    "BilliardsEverything",
    "Billards_Stable"
)

function Get-BilliardsPid {
    $matches = @(
        Get-CimInstance Win32_Process |
            Where-Object {
                $cmd = $_.CommandLine

                $_.Name -in @("java.exe", "javaw.exe") -and
                -not [string]::IsNullOrWhiteSpace($cmd) -and
                (($Keywords | Where-Object { $cmd -like "*$_*" }).Count -gt 0)
            } |
            Sort-Object CreationDate -Descending
    )

    if ($matches.Count -eq 0) {
        return $null
    }

    if ($matches.Count -gt 1) {
        Write-Warning "Multiple matching Java billiards processes found. Using newest PID $($matches[0].ProcessId)."
    }

    return $matches[0].ProcessId
}

$pidToWatch = Get-BilliardsPid

if ($null -eq $pidToWatch) {
    Write-Error "Could not find the Billiards Java process. Start the app first, then run this script."
    exit 1
}

Write-Host "Detected Billiards PID: $pidToWatch"

while ($true) {
    try {
        $p = Get-Process -Id $pidToWatch -ErrorAction Stop
    }
    catch {
        Write-Warning "Process $pidToWatch exited. Trying to detect it again..."

        $pidToWatch = Get-BilliardsPid

        if ($null -eq $pidToWatch) {
            Start-Sleep 5
            continue
        }

        Write-Host "Detected new Billiards PID: $pidToWatch"
        $p = Get-Process -Id $pidToWatch
    }

    $now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

    "{0} PID={1} CPU={2:N2}s Private={3:N2}GB Virtual={4:N2}GB Working={5:N2}GB Threads={6}" -f `
        $now, `
        $p.Id, `
        $p.CPU, `
        ($p.PrivateMemorySize64 / 1GB), `
        ($p.VirtualMemorySize64 / 1GB), `
        ($p.WorkingSet64 / 1GB), `
        ($p.Threads.Count)

    Start-Sleep 1
}
