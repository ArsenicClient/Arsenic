#!/bin/bash
cd `dirname $0`
./gradlew setupDevWorkspace
./gradlew build