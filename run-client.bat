@echo off
cd /d %~dp0
if not exist out mkdir out
javac -d out src\common\*.java src\model\*.java src\server\*.java src\client\*.java
if errorlevel 1 pause & exit /b 1
java -cp out client.TriviaClient
