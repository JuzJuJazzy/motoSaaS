@ECHO OFF
        REM Gradle wrapper BAT - requires gradle-wrapper.jar in gradle\wrapper\
        IF EXIST "%~dp0gradle\wrapper\gradle-wrapper.jar" (
          java -jar "%~dp0gradle\wrapper\gradle-wrapper.jar" %*
        ) ELSE (
          ECHO Gradle wrapper JAR not found. Please open the project in Android Studio (it can download Gradle), or install Gradle on your system (e.g., choco install gradle).
          EXIT /B 1
        )
