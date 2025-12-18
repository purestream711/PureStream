$ErrorActionPreference = "Stop"
try {
    ./gradlew.bat assembleDebug
    Write-Host "Build completed successfully"
} catch {
    Write-Host "Build failed: $_"
    exit 1
}