# ###############################################################################################
# STAGE 1: stratio drivers builder
# ###############################################################################################

FROM qa.int.stratio.com/stratio/stratio-metabase-builder:0.50.21-0.1.0-SNAPSHOT as builder

WORKDIR /app/source

ENV FC_LANG en-US
ENV LC_CTYPE en_US.UTF-8

# Add:
# - our drivers source code to build them
# - deps.edn with the stratio mvn repo declared so crossdata-jdbc dependency is resolved when building our drivers
COPY ./metabase/modules                                /app/source/modules
COPY ./metabase/modules/drivers/stratio-repo.edn       /root/.clojure/deps.edn

# build our drivers
RUN bin/build-drivers.sh



# ###############################################################################################
# # STAGE 2: runner
# ###############################################################################################

FROM qa.int.stratio.com/stratio/oauth-base:0.4.0 as runner

WORKDIR /app

ENV FC_LANG en-US
ENV LC_CTYPE en_US.UTF-8
ENV DISCOVERY_VERSION 0.43.4

################################
###       DEPENDENCIES       ###
################################
# apt things
RUN apt-get update \
  &&  apt-get install -y ttf-dejavu fontconfig wget zip \
  && apt-get --only-upgrade install -y libfreetype6 libssl1.1 openssl libsystemd0 libudev1\
  && apt-get -qq clean \
  && rm -rf /var/lib/apt/lists/*

# install JAVA 11
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
RUN apt update \
    && apt install -y openjdk-11-jdk
ENV PATH=$JAVA_HOME/bin:$PATH

################################
###        METABASE          ###
################################
# add Metabase uberjar
RUN mkdir -p target/uberjar
COPY --from=builder /app/source/target/uberjar/metabase.jar /app/target/uberjar/

# adapt MANIFEST.MF to implementation discovery version
RUN jar xf /app/target/uberjar/metabase.jar META-INF/MANIFEST.MF  \
    && sed -i '1s;^;Implementation-Version: '"$DISCOVERY_VERSION"'\n;' META-INF/MANIFEST.MF \
    && zip /app/target/uberjar/metabase.jar META-INF/MANIFEST.MF \
    && rm -rf META-INF


# Append to the default migration yaml our 100_stratio.yaml and to the default "init" sql script
# our subscription_triggers.sql scripts and overwrite them inside the jar
COPY ./metabase/resources/migrations/100_stratio.yaml           100_stratio.yaml
COPY ./metabase/resources/migrations/subscription_triggers.sql  subscription_triggers.sql

RUN jar xf /app/target/uberjar/metabase.jar migrations/000_legacy_migrations.yaml           \
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
COPY --from=builder /app/source/resources/modules/* /plugins/


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
# rename base image rc.local so we can use our own rc.local
RUN  mv /etc/rc.local /etc/rc.local.base
COPY rc.local /etc/rc.local
RUN chmod +x /etc/rc.local

# add metabase and discovery-cicd supervised services
COPY service /etc/service
RUN chmod +x /etc/service/metabase/run /etc/service/discovery-cicd/run

# nginx confs
COPY openresty/nginx/conf/* /usr/local/openresty/nginx/conf/

# entrypoint
COPY /docker-entrypoint.sh /docker-entrypoint.sh
COPY /docker-entrypoint /docker-entrypoint
