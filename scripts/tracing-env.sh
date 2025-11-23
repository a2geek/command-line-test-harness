#!/bin/bash

agent="-agentlib:native-image-agent=config-merge-dir=$PWD/app/src/main/resources/META-INF/native-image/io.github.a2geek.clth.app"
alias clth="java $agent -jar app/build/libs/clth.jar"
