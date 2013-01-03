#!/bin/bash

echo "Launching Juxta WS"
java -jar -server -Xmn512M -Xms1G -Xmx1G -XX:+OptimizeStringConcat juxta-ws.jar &
