@echo off
chcp 65001 >nul
echo ====================================
echo 文件传输服务端
echo ====================================
echo.
echo 正在启动服务端...
echo HTTP端口: 8080
echo TCP端口: 9090
echo 共享目录: ./share
echo.

if not exist "share" (
    mkdir share
    echo 已创建共享目录: share
)

REM 设置使用Java 21
set JAVA_HOME=D:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%

mvn compile exec:java -Dexec.mainClass="com.qoder.server.FileTransferServer"

pause
