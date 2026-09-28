#!/usr/bin/env sh
set -eu
mvn -Pserver -DskipTests exec:java
