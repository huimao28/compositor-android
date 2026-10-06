#!/bin/sh
# 还原 gradle-wrapper.jar（仓库里以 base64 文本形式存放，见 README）
set -e
cd "$(dirname "$0")/.."
base64 -d gradle/wrapper/gradle-wrapper.jar.b64 > gradle/wrapper/gradle-wrapper.jar
echo "restored gradle/wrapper/gradle-wrapper.jar"
