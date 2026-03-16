#!/bin/bash
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/common/*.java src/model/*.java src/server/*.java src/client/*.java && java -cp out server.TriviaServer
