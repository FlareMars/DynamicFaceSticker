#!/bin/sh
export ProjectPath=$(cd "../$(dirname "$1")"; pwd)
export TargetClassName="com.flarejaven.example.jnithread.NdkJniUtils"

export SourceFile="${ProjectPath}/app/src/main/java"
export TargetPath="${ProjectPath}/app/src/main/jni"

cd "${SourceFile}"
javah -d ${TargetPath} -classpath "${SourceFile}" "${TargetClassName}"
echo -d ${TargetPath} -classpath "${SourceFile}" "${TargetClassName}"