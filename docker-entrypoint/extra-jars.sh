#!/usr/bin/env bash

WGET_SECURITY_OPTS=${WGET_CUSTOM_SECURITY_OPTS:-} #Example WGET options "--ca-cert=${SSL_CA} --certificate=${SSL_CERT} --private-key=$SSL_KEY"

function download_extra_jars() {
  local jars=$1
  local destination=$2

  if [ -z "$jars" ]; then
    INFO "[EXTERNAL-JAR] No required extra jars for $destination, skipping..."
  else
    INFO "[EXTERNAL-JAR] Required extra jars for $destination: $jars"
    while IFS=',' read -ra EXTRAJAR; do
      for i in "${EXTRAJAR[@]}"; do
        # process "$i"
        INFO "[EXTERNAL-JAR] Downloading extra jar: $i to $destination"
        wget -nv $WGET_SECURITY_OPTS -P $destination $i
        if [ $? -ne 0 ]; then
          WARN "[EXTERNAL-JAR] Failed downloading $i"
        fi
      done
    done <<< "$jars"
  fi
}

download_extra_jars "$EXTRA_JARS" /plugins
