#!/usr/bin/env bash

CURL_SECURITY_OPTS=${CURL_CUSTOM_SECURITY_OPTS:-} #Example CURL options "--cacert ${SSL_CA} --cert ${SSL_CERT} --key ${SSL_KEY}"

function download_extra_jars() {
  local jars=$1
  local destination=$2

  if [ -z "$jars" ]; then
    INFO "[EXTERNAL-JAR] No required extra jars for $destination, skipping..."
  else
    INFO "[EXTERNAL-JAR] Required extra jars for $destination: $jars"

    while IFS=',' read -ra EXTRAJAR; do
      for i in "${EXTRAJAR[@]}"; do
        INFO "[EXTERNAL-JAR] Downloading extra jar: $i to $destination"
        if ! (cd "$destination" && curl -sLO "$CURL_SECURITY_OPTS" "$i"); then
          WARN "[EXTERNAL-JAR] Failed downloading $i"
        fi
      done
    done <<< "$jars"
  fi
}

download_extra_jars "$EXTRA_JARS" /plugins
