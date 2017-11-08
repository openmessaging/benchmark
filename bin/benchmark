#!/bin/bash


CLASSPATH=benchmark-framework/target/classes:`cat benchmark-framework/target/classpath.txt`

JVM_MEM="-Xms4G -Xmx4G -XX:+UseG1GC"

java -server -cp $CLASSPATH $JVM_MEM io.openmessaging.benchmark.Benchmark $*

