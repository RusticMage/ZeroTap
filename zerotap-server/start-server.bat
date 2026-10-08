@echo off
title ZeroTap Command Center Server
echo Starting ZeroTap Spring Boot Server on port 8080...
java -jar "%~dp0target\zerotap-server-1.0.0-SNAPSHOT.jar"
