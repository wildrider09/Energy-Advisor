#!/bin/bash
export HOME=/Users/abhisgg
export PATH=/Users/abhisgg/.toolbox/bin:/usr/local/bin:/usr/bin:/bin
APP_JAR="/Volumes/workplace/smart-home-energy-advisor/build/libs/smart-home-energy-advisor-1.0.0.jar"

while true; do
    if ! curl -s http://localhost:9090/api/state > /dev/null 2>&1; then
        echo "$(date): Restarting app..."
        kill -9 $(lsof -ti:9090) 2>/dev/null
        sleep 2
        nohup java -jar "$APP_JAR" > /tmp/bootrun.log 2>&1 &
        sleep 10
    fi
    ada credentials update --account=767398136570 --provider=conduit --role=IibsAdminAccess-DO-NOT-DELETE --once >> /tmp/ada-refresh.log 2>&1
    sleep 240
done
