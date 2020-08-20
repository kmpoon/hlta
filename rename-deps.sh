#!/bin/bash

SCALA_VERSION=2.12
HLTA_VERSION=2.3

BASE=$(dirname $0)
TARGET="$BASE/target/scala-${SCALA_VERSION}/HLTA-assembly-${HLTA_VERSION}-deps.jar"
if [[ -e $TARGET ]]; then
  mv $TARGET $BASE/target/scala-${SCALA_VERSION}/HLTA-deps.jar
else
  echo "$TARGET does not exist."
  exit -1
fi
