#!/bin/sh

CLASSPATH=.
CLASSPATH=$CLASSPATH:jsch-0.1.54.jar

export CLASSPATH
echo $CLASSPATH
echo   Compiling jterminal ...

rm JSSHTerminal/*.class
javac -deprecation JSSHTerminal/MainPanel.java
