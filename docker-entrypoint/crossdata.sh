#!/usr/bin/env bash

mkdir -p /etc/sds/crossdata/shell

crossdata_conf_file=/etc/sds/crossdata/shell/driver-application.conf

# KEYSTORE_PASSWORD and TRUSTSTORE_PASSWORD set in secrets.sh
{
    echo 'crossdata-driver.akka-http.ssl.keystore = "/etc/pki/discovery.jks"';
    echo "crossdata-driver.akka-http.ssl.keystore-password = ${KEYSTORE_PASSWORD}";
    echo 'crossdata-driver.akka-http.ssl.truststore = "/etc/pki/ca-bundle.jks"';
    echo "crossdata-driver.akka-http.ssl.truststore-password = ${TRUSTSTORE_PASSWORD}";
} > $crossdata_conf_file
