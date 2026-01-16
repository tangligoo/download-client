@echo off
chcp 65001 >nul
echo ====================================
echo 文件传输客户端
echo ====================================
echo.
echo 正在启动客户端...
echo.

REM 设置使用Java 21
set JAVA_HOME=C:\Users\admin\.qoder\extensions\redhat.java-1.51.0-win32-x64\jre\21.0.9-win32-x86_64
set PATH=%JAVA_HOME%\bin;%PATH%

echo 使用的Java版本:
java -version
echo.

mvn javafx:run
