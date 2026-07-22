#!/usr/bin/env bash

mkdir -p /etc/sds/crossdata/shell

crossdata_conf_file=/etc/sds/crossdata/shell/driver-application.conf

# KEYSTORE_PASSWORD and TRUSTSTORE_PASSWORD set in secrets.sh
{
    echo 'crossdata-driver.pekko-http.ssl.keystore = "/etc/pki/discovery.jks"';
    echo "crossdata-driver.pekko-http.ssl.keystore-password = ${KEYSTORE_PASSWORD}";
    echo 'crossdata-driver.pekko-http.ssl.truststore = "/etc/pki/ca-bundle.jks"';
    echo "crossdata-driver.pekko-http.ssl.truststore-password = ${TRUSTSTORE_PASSWORD}";

    # TODO: this is temporary until we manage to have this propoerty in the fat-jars reference.conf
    echo "crossdata-driver.config.row-deserialization-parallelism = 4";
    echo 'crossdata-driver.config.row-deserialization-parallelism = ${?crossdata_driver_config_row_deserialization_parallelism}';
} > $crossdata_conf_file

VIRTUALIZER_JDBC_FROM_EXTRA_JARS=${VIRTUALIZER_JDBC_FROM_EXTRA_JARS:-false}
if [[ "$VIRTUALIZER_JDBC_FROM_EXTRA_JARS" = "false" ]]; then
    cp /stratio-crossdata-jdbc4-shaded.jar /plugins/
fi
