#!/bin/bash

if [ $# -ne 1 ]; then
  echo "specify input file." 
  exit 1
fi

bin/hadoop dfs -rmr output

bin/hadoop accels -D hadoop.pipes.java.recordreader=true\
 -D hadoop.pipes.java.recordwriter=true\
 -output output\
 -cpubin bin/cpu-kmeans2D\
 -gpubin bin/cpu-kmeans2D\
 -input $1
