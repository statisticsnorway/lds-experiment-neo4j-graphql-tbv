#!/usr/bin/env bash

for i in neo4j:4.0; do
  docker pull $i
done

mkdir -p neo4jplugins
pushd neo4jplugins
if [ ! -f "apoc-4.0.0.14-all.jar" ]; then
  rm -f apoc-*
  echo "Downloading neo4j apoc plugin...";
  curl -L -O https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/download/4.0.0.14/apoc-4.0.0.14-all.jar
  echo "Download complete!";
fi
popd
