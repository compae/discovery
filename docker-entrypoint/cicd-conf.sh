#!/usr/bin/env bash

if [[ ! -v DISCOVERY_CICD_JAVA_INITIAL_RAM_PERCENTAGE ]]; then
    DISCOVERY_CICD_JAVA_INITIAL_RAM_PERCENTAGE="-XX:InitialRAMPercentage=10.0"
fi

if [[ ! -v DISCOVERY_CICD_JAVA_MAX_RAM_PERCENTAGE ]]; then
    DISCOVERY_CICD_JAVA_MAX_RAM_PERCENTAGE="-XX:MaxRAMPercentage=25.0"
fi

# Check if the user contains legacy memory JVM options and warn about it
if [[ $DISCOVERY_CICD_JAVA_OPTIONS == *"-Xms"* ]] || [[ $DISCOVERY_CICD_JAVA_OPTIONS == *"-Xmx"* ]]; then
  WARN "The memory percentage defined by -XX:InitialRAMPercentage and -XX:MaxRAMPercentage will be ignored since JVM options (-Xms -Xmx) are provided in DISCOVERY_CICD_JAVA_OPTIONS. Please, set the JVM memory configuration using DISCOVERY_CICD_JAVA_INITIAL_RAM_PERCENTAGE and DISCOVERY_CICD_JAVA_MAX_RAM_PERCENTAGE instead."
fi

DISCOVERY_CICD_MEMORY_OPTS="-XX:+UseContainerSupport ${DISCOVERY_CICD_JAVA_INITIAL_RAM_PERCENTAGE} ${DISCOVERY_CICD_JAVA_MAX_RAM_PERCENTAGE}"

# env var used by service/discovery-cicd/run
echo "$DISCOVERY_CICD_MEMORY_OPTS" > /env_vars/DISCOVERY_CICD_MEMORY_OPTS