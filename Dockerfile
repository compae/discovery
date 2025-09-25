# ###############################################################################################
# STAGE 1: stratio drivers builder
# ###############################################################################################

FROM qa.int.stratio.com/stratio/stratio-metabase-builder:0.56.4-0.1.0-SNAPSHOT as builder

WORKDIR /home/node

ENV FC_LANG en-US
ENV LC_CTYPE en_US.UTF-8

# Add:
# - our drivers source code to build them
# - deps.edn with the stratio mvn repo declared so crossdata-jdbc dependency is resolved when building our drivers
COPY ./metabase/modules                                /home/node/modules
COPY ./metabase/modules/drivers/stratio-repo.edn       /root/.clojure/deps.edn

# build our drivers
RUN bin/build-driver.sh crossdata
RUN bin/build-driver.sh stratiopostgres


# ###############################################################################################
# # STAGE 2: runner
# ###############################################################################################

FROM ubuntu:24.04 as runner

WORKDIR /app

ENV FC_LANG en-US
ENV LC_CTYPE en_US.UTF-8
ENV B_LOG_VERSION 0.6.1-7055620
ENV KMS_UTILS_VERSION 0.6.1-7055620
ENV STRATIO_UTILS /stratio

################################
###       DEPENDENCIES       ###
################################
# apt things
RUN apt-get update &&  apt-get install -y \
    openjdk-21-jdk \
    ca-certificates \
    bash \
    nginx \
    # services supervisor
    s6 \
    # we use wget to download "extra jars" (maybe use curl instead?)
    wget \
    # we use zip only in the Dockerfile itself - mayber install and remove in the corresponding layer?
    zip \
    # jq, curl for kms-utils
    jq \
    curl \
    # to substitue environment variables in the nginx.conf file with envsubst
    gettext-base \
    # these fonts are neede by metabase
    fontconfig \
    fonts-noto-core \
    # kms utils and b-log
    && mkdir -p "$STRATIO_UTILS" \
    && curl -o "$STRATIO_UTILS/kms_utils.sh" "http://qa.int.stratio.com/repository/paas/kms_utils/${KMS_UTILS_VERSION}/kms_utils-${KMS_UTILS_VERSION}.sh" \
    && chmod +x "$STRATIO_UTILS/kms_utils.sh" \
    && curl -o "$STRATIO_UTILS/b-log.sh" "http://qa.int.stratio.com/repository/paas/log_utils/${B_LOG_VERSION}/b-log-${B_LOG_VERSION}.sh" \
    && chmod +x "$STRATIO_UTILS/b-log.sh" \
    # delete default nginx.conf and create some needed folders
    && rm /etc/nginx/nginx.conf \
    && mkdir /etc/pki /env_vars \
    # clean apt caches
    && apt-get -qq clean \
    && rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME environment variable
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

################################
###        METABASE          ###
################################
# add Metabase uberjar
RUN mkdir -p target/uberjar
COPY --from=builder /home/node/target/uberjar/metabase.jar /app/target/uberjar/

# adapt MANIFEST.MF to implementation discovery version so that we detect metabase own CVEs
ENV METABASE_VERSION=0.56.4
RUN jar xf /app/target/uberjar/metabase.jar META-INF/MANIFEST.MF  \
    && sed -i '1s;^;Implementation-Version: '"$METABASE_VERSION"'\n;' META-INF/MANIFEST.MF \
    && zip /app/target/uberjar/metabase.jar META-INF/MANIFEST.MF \
    && rm -rf META-INF

# Append to the default migration yaml our 100_stratio.yaml and to the default "init" sql script
# our subscription_triggers.sql scripts and overwrite them inside the jar
COPY ./metabase/resources/migrations/100_stratio.yaml           100_stratio.yaml
COPY ./metabase/resources/migrations/subscription_triggers.sql  subscription_triggers.sql

RUN jar xf /app/target/uberjar/metabase.jar migrations/000_legacy_migrations.yaml             \
   && jar xf /app/target/uberjar/metabase.jar migrations/initialization/metabase_postgres.sql \
   && cat 100_stratio.yaml          >> migrations/000_legacy_migrations.yaml                  \
   && cat subscription_triggers.sql >> migrations/initialization/metabase_postgres.sql        \
   && zip /app/target/uberjar/metabase.jar migrations/000_legacy_migrations.yaml              \
   && zip /app/target/uberjar/metabase.jar migrations/initialization/metabase_postgres.sql    \
   && rm -rf migrations/ 100_stratio.yaml subscription_triggers.sql


# create the plugins directory, with writable permissions
RUN mkdir -p /plugins
RUN chmod a+rwx /plugins

# add stratio drivers jars to plugins folder
COPY --from=builder /home/node/resources/modules/* /plugins/


################################
###  PROMETHEUS, LOGS, CICD  ###
################################
# Download prometheus jar
RUN wget --no-verbose -O /app/target/uberjar/jmx_prometheus_javaagent.jar \
    http://qa.int.stratio.com/repository/central/io/prometheus/jmx/jmx_prometheus_javaagent/0.19.0/jmx_prometheus_javaagent-0.19.0.jar \
    && echo '{}' > /app/target/uberjar/config.yaml

COPY log4j2.xml /app/target/log4j2.xml

# add discovery-cicd jar
COPY discovery-cicd/target/discoverycicd-*-uber.jar /app/target/uberjar/discovery-cicd.jar


################################
###   SCRIPTS AND SERVICES   ###
################################
# add metabase, discovery-cicd and nginx supervised services (sv is the folder where s6 looks for things)
COPY service/ /etc/sv/
RUN chmod +x /etc/sv/metabase/run /etc/sv/discovery-cicd/run /etc/sv/nginx/run

# nginx confs
COPY /nginx/* /etc/nginx/

# entrypoint
COPY /docker-entrypoint.sh /docker-entrypoint.sh
COPY /docker-entrypoint /docker-entrypoint

ENTRYPOINT ["/usr/bin/bash", "/docker-entrypoint.sh"]
