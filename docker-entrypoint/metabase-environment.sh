#!/usr/bin/env bash

####################################
###  POSTGRES_CONNECTION_STRING  ###
####################################

MB_DB_SSL=${MB_DB_SSL:-"true"}
MB_DB_TYPE=${MB_DB_TYPE:-"postgres"}
SSL_OPTIONS="ssl=true&sslmode=verify-full&sslcert=/etc/pki/discovery.pem&sslkey=/etc/pki/discovery.pk8&sslrootcert=/etc/pki/ca-bundle.pem"
MB_JDBC_PARAMETERS=${MB_JDBC_PARAMETERS:-""}


if [ -n "$MB_DB_CONNECTION_URI" ]; then
    INFO "Using provided MB_DB_CONNECTION_URI: $MB_DB_CONNECTION_URI"
else
    if [ -z "$MB_DB_URL" ]; then
        WARN "MB_DB_URL variable not defined. Will use legacy MB_DB_HOST and MB_DB_PORT"
        MB_DB_URL="${MB_DB_HOST}:${MB_DB_PORT}"
    fi


    if [ "$MB_DB_SSL" = "true" ] && [ "$DOWNLOAD_SECRETS" = "true" ]; then
        DEBUG "Using postgres with TLS security"
        MB_DB_CONNECTION_URI="postgres://${MB_DB_URL}/${MB_DB_DBNAME}?user=${MB_DB_USER}&${SSL_OPTIONS}"
    elif [ "$MB_DB_SSL" = "true" ] && [ "$DOWNLOAD_SECRETS" = "false" ]; then
        ERROR "Cannot build postgres TLS connection string without downloading secrets"
        exit 1
    else
        DEBUG "Using postgres with user/pass security"
        MB_DB_CONNECTION_URI="postgres://${MB_DB_URL}/${MB_DB_DBNAME}?user=${MB_DB_USER}&password=${MB_DB_PASS}"
    fi


    if [ -n "$MB_JDBC_PARAMETERS" ]; then
        MB_DB_CONNECTION_URI="${MB_DB_CONNECTION_URI}&${MB_JDBC_PARAMETERS}"
    fi

    DEBUG "MB_DB_CONNECTION_URI: $MB_DB_CONNECTION_URI"
fi


#########################################
###   SSO PROXY RELATED ENVIRONMENT   ###
#########################################
# Although now we run behind the proxy so we do not server TLS directly,
# we still need these variables to verify the JWT when using native JWT auth
MB_JETTY_SSL_KEYSTORE="/etc/pki/discovery.jks"
MB_JETTY_SSL_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
MB_JETTY_SSL_TRUSTSTORE="/etc/pki/ca-bundle.jks"
MB_JETTY_SSL_TRUSTSTORE_PASSWORD="$TRUSTSTORE_PASSWORD"

# We run behind the proxy, so only bind to 127.0.0.1 so noone
# can access the server from outside the container
MB_JETTY_HOST="127.0.0.1"

# Authorization is metabase native or managed by the proxy?
if [ "$USE_GOSEC_SSO_AUTH" == "true" ]; then
    # we need to set the auth headers that the oauth-proxy will send us down
    # note that the header map in metabase is always lowercase
    MB_USER_HEADER="x-dcos-oauth-user"
    MB_GROUP_HEADER="x-dcos-oauth-groups"
fi


########################
###   JAVA OPTIONS   ###
########################
JAVA_OPTS="$JAVA_OPTS -XX:+IgnoreUnrecognizedVMOptions" # Don't barf if we see an option we don't understand (e.g. Java 9 option on Java 7/8)
JAVA_OPTS="$JAVA_OPTS -Djava.awt.headless=true"         # don't try to start AWT. Not sure this does anything but better safe than wasting memory
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"            # Use UTF-8
# TODO: stratio logs
# JAVA_OPTS="$JAVA_OPTS -Dlog4j.configuration=target/log/log4j2.xml"
JAVA_OPTS="$JAVA_OPTS --XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

if [ -n "$MAX_HEAP" ]; then
    JAVA_OPTS="$JAVA_OPTS -Xmx$MAX_HEAP"
elif [ -n "$PERCENTAGE_MAX_HEAP_JVM" ]; then
    HEAP_IN_MB=$(awk '{printf("%.0f\n", $1*$2/100)}' <<<"$PERCENTAGE_MAX_HEAP_JVM $MARATHON_APP_RESOURCE_MEM")
    JAVA_OPTS="$JAVA_OPTS -Xmx${HEAP_IN_MB}m"
fi

if [ "$JMX" ]; then
    JAVA_OPTS="$JAVA_OPTS $JMX"
fi

# TODO: prometheus
# if [ "$PROMETHEUS_METRICS_ENABLE" = "true" ] ; then
#     PROMETHEUS_METRICS_PORT="${PROMETHEUS_METRICS_PORT:-6080}"
#     JAVA_OPTS="$JAVA_OPTS -javaagent:/app/target/uberjar/jmx_prometheus_javaagent-0.12.0.jar=$PROMETHEUS_METRICS_PORT:/app/target/uberjar/config.yaml"
# fi

DEBUG "Using these JAVA_OPTS: ${JAVA_OPTS}"

MB_EMOJI_IN_LOGS="false"



###################################
###   PUT VARIABLES IN FILES    ###
###################################
# put the env vars in files so we can read them when the supervised services start
echo "$MB_DB_CONNECTION_URI"             > /env_vars/MB_DB_CONNECTION_URI
echo "$MB_JETTY_SSL_KEYSTORE"            > /env_vars/MB_JETTY_SSL_KEYSTORE
echo "$MB_JETTY_SSL_KEYSTORE_PASSWORD"   > /env_vars/MB_JETTY_SSL_KEYSTORE_PASSWORD
echo "$MB_JETTY_SSL_TRUSTSTORE"          > /env_vars/MB_JETTY_SSL_TRUSTSTORE
echo "$MB_JETTY_SSL_TRUSTSTORE_PASSWORD" > /env_vars/MB_JETTY_SSL_TRUSTSTORE_PASSWORD
echo "$MB_JETTY_HOST"                    > /env_vars/MB_JETTY_HOST
echo "$MB_USER_HEADER"                   > /env_vars/MB_USER_HEADER
echo "$MB_GROUP_HEADER"                  > /env_vars/MB_GROUP_HEADER
echo "$MB_EMOJI_IN_LOGS"                 > /env_vars/MB_EMOJI_IN_LOGS
echo "$JAVA_OPTIONS"                     > /env_vars/JAVA_OPTIONS
