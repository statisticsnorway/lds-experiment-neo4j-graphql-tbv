#!/usr/bin/env bash

for i in neo4j:3.5; do
  docker pull $i &
done

mkdir -p neo4jplugins

if [ ! -f "apoc-3.5.0.4-all.jar" ]; then
  echo "Downloading neo4j apoc plugin...";
  pushd neo4jplugins
  curl -L -O https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/download/3.5.0.4/apoc-3.5.0.4-all.jar
  popd
  echo "Download complete!";
fi
