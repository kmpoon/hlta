#!/bin/bash

BASE=$(dirname $0)
TARGET="$BASE/target/scala-2.11/HLTA-assembly-1.1-deps.jar"
if [[ -e $TARGET ]]; then
  mv $TARGET $BASE/target/scala-2.11/HLTA-deps.jar
else
  echo "$TARGET does not exist."
  exit -1
fi