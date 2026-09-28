#!/usr/bin/env sh
set -eu
if [ "$#" -gt 0 ]; then
  mvn -Pclient -DskipTests -Dclient.args="--name=$1" javafx:run
else
  mvn -Pclient -DskipTests javafx:run
fi
