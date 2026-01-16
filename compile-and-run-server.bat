@echo off
chcp 65001 >nul
echo ====================================
echo 编译并运行服务端 (无需Maven)
echo ====================================
echo.

REM 创建输出目录
if not exist "out" mkdir out
if not exist "share" mkdir share

echo 正在下载依赖...
echo 注意: 需要手动下载gson-2.10.1.jar
echo 下载地址: https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
echo 保存到: lib\gson-2.10.1.jar
echo.

if not exist "lib\gson-2.10.1.jar" (
    echo 错误: 未找到 gson-2.10.1.jar
    echo 请先下载依赖库到 lib 目录
    pause
    exit /b 1
)

echo 正在编译服务端...
javac -encoding UTF-8 -d out -cp "lib\*" src\main\java\com\qoder\server\*.java src\main\java\com\qoder\server\model\*.java

if %ERRORLEVEL% NEQ 0 (
    echo 编译失败!
    pause
    exit /b 1
)

echo 编译成功! 正在启动服务端...
echo HTTP端口: 8080
echo TCP端口: 9090
echo 共享目录: ./share
echo.

java -cp "out;lib\*" com.qoder.server.FileTransferServer

pause
