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

if [ "$AUTHENTICATOR" = "gosec-sso" ]; then
    # we need to set the correct claim for username of the stratio jwt
    # (default in stratio-metabase code correspond to Datio case)
    JWT_USERNAME_CLAIM=${JWT_USERNAME_CLAIM:-"uid"}
    JWT_PUBLIC_KEY_LOCATION="file"
fi


########################
###   JAVA OPTIONS   ###
########################
JAVA_OPTS="$JAVA_OPTS -XX:+IgnoreUnrecognizedVMOptions" # Don't barf if we see an option we don't understand (e.g. Java 9 option on Java 7/8)
JAVA_OPTS="$JAVA_OPTS -Djava.awt.headless=true"         # don't try to start AWT. Not sure this does anything but better safe than wasting memory
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"            # Use UTF-8
JAVA_OPTS="$JAVA_OPTS --XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

# Stratio log format
JAVA_OPTS="$JAVA_OPTS -Dlog4j2.configurationFile=/app/target/log4j2.xml -Dlog4j2.Script.enableLanguages=javascript"

# JVM memory configuration
if [[ ! -v JAVA_INITIAL_RAM_PERCENTAGE ]]; then
    JAVA_INITIAL_RAM_PERCENTAGE="-XX:InitialRAMPercentage=30.0"
fi

if [[ ! -v JAVA_MAX_RAM_PERCENTAGE ]]; then
    if [ -n "$PERCENTAGE_MAX_HEAP_JVM" ]; then # legacy PERCENTAGE_MAX_HEAP_JVM env var was an integer in CCT
        JAVA_MAX_RAM_PERCENTAGE="-XX:MaxRAMPercentage=${PERCENTAGE_MAX_HEAP_JVM}.0"
    else
        JAVA_MAX_RAM_PERCENTAGE="-XX:MaxRAMPercentage=70.0"
    fi
fi

## Check if the user contains legacy memory JVM options and warn about it
if [[ $JAVA_OPTS == *"-Xms"* ]] || [[ $JAVA_OPTS == *"-Xmx"* ]]; then
  WARN "The memory percentage defined by -XX:InitialRAMPercentage and -XX:MaxRAMPercentage will be ignored since JVM options (-Xms -Xmx) are provided in JAVA_OPTS. Please, set the JVM memory configuration using JAVA_INITIAL_RAM_PERCENTAGE and JAVA_MAX_RAM_PERCENTAGE instead."
fi

JAVA_MEMORY_OPTS="-XX:+UseContainerSupport ${JAVA_INITIAL_RAM_PERCENTAGE} ${JAVA_MAX_RAM_PERCENTAGE}"
JAVA_OPTS="$JAVA_OPTS -${JAVA_MEMORY_OPTS}"

# Metrics configuration
if [ "$JMX" ]; then
    JAVA_OPTS="$JAVA_OPTS $JMX"
fi

if [ "$PROMETHEUS_METRICS_ENABLE" = "true" ] ; then
    PROMETHEUS_METRICS_PORT="${PROMETHEUS_METRICS_PORT:-6080}"
    JAVA_OPTS="$JAVA_OPTS -javaagent:/app/target/uberjar/jmx_prometheus_javaagent.jar=$PROMETHEUS_METRICS_PORT:/app/target/uberjar/config.yaml"
fi

DEBUG "Using these JAVA_OPTS: ${JAVA_OPTS}"

# Check that JETTY_LOG_LEVEL is never set to DEBUG (or lower)

if [[ $JETTY_LOG_LEVEL == "DEBUG" || $JETTY_LOG_LEVEL == "TRACE" ]]; then
  INFO "[Discovery] Cannot set JETTY_LOG_LEVEL to $JETTY_LOG_LEVEL due to security reasons. Setting it instead to INFO"
  JETTY_LOG_LEVEL="INFO"
fi

export JETTY_LOG_LEVEL

# writes the environment variable whose name is passed as argument
# to the /env_vars folder only if the variable has a value
function write_to_file_if_exists() {
    local variable_name=$1
    if [ -n "${!variable_name}" ]; then
        echo "${!variable_name}" > "/env_vars/${variable_name}"
    fi
}

# Timeout for returning connections to the pool. Causes large virtualizer collections syncs to fail.
# set it to 0 (no tiemout)
MB_JDBC_DATA_WAREHOUSE_UNRETURNED_CONNECTION_TIMEOUT_SECONDS=${MB_JDBC_DATA_WAREHOUSE_UNRETURNED_CONNECTION_TIMEOUT_SECONDS:-"0"}

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
echo "$JAVA_OPTS"                        > /env_vars/JAVA_OPTS
echo "$AUTHENTICATOR"                    > /env_vars/AUTHENTICATOR
echo "$JETTY_LOG_LEVEL"                  > /env_vars/JETTY_LOG_LEVEL

write_to_file_if_exists JWT_PUBLIC_KEY_FILE
write_to_file_if_exists JWT_USERNAME_CLAIM
write_to_file_if_exists JWT_PUBLIC_KEY_LOCATION

# some default metabase settings we want to set
echo "/plugins"                          > /env_vars/MB_PLUGINS_DIR
echo "none"                              > /env_vars/MB_HUMANIZATION_STRATEGY
echo "false"                             > /env_vars/MB_EMOJI_IN_LOGS
echo "false"                             > /env_vars/MB_COLORIZE_LOGS
echo "false"                             > /env_vars/MB_ANON_TRACKING_ENABLED
echo "false"                             > /env_vars/MB_CHECK_FOR_UPDATES
echo "$MB_JDBC_DATA_WAREHOUSE_UNRETURNED_CONNECTION_TIMEOUT_SECONDS" > /env_vars/MB_JDBC_DATA_WAREHOUSE_UNRETURNED_CONNECTION_TIMEOUT_SECONDS
write_to_file_if_exists SERVICE_URL
