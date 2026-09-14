@echo off
setlocal
where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Khong tim thay Java. Hay cai JDK 17 hoac Android Studio.
  pause
  exit /b 1
)
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo Build that bai. Mo Android Studio de xem loi Gradle/SDK chi tiet.
  pause
  exit /b 1
)
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
