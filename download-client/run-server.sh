#!/bin/bash

echo "===================================="
echo "文件传输服务端"
echo "===================================="
echo ""
echo "正在启动服务端..."
echo "HTTP端口: 8080"
echo "TCP端口: 9090"
echo "共享目录: ./share"
echo ""

if [ ! -d "share" ]; then
    mkdir share
    echo "已创建共享目录: share"
fi

mvn compile exec:java -Dexec.mainClass="com.qoder.server.FileTransferServer"
