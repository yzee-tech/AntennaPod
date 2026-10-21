@echo off
setlocal
cd /d "%~dp0"
if not exist adb.exe (
    echo adb.exe was not found. Put this file and migrate-antennapod.sh into the platform-tools folder.
    pause
    exit /b 1
)
echo Looking for your phone. If the phone asks "Allow USB debugging?", tap Allow.
adb.exe wait-for-device
adb.exe devices -l
adb.exe push migrate-antennapod.sh /data/local/tmp/migrate-antennapod.sh
if errorlevel 1 (
    echo Could not copy the script to the phone.
    pause
    exit /b 1
)
adb.exe shell sh /data/local/tmp/migrate-antennapod.sh
echo.
pause
