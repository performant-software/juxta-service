#!/bin/bash

echo "Launching Juxta WS"
java -jar -server -XX:+OptimizeStringConcat juxta-ws.jar &
