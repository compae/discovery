# ###############################################################################################
# STAGE 1: stratio drivers builder
# ###############################################################################################

FROM qa.int.stratio.com/stratio/stratio-metabase-builder:0.43.4-0.1.0-SNAPSHOT as builder

WORKDIR /app/source

ENV FC_LANG en-US
ENV LC_CTYPE en_US.UTF-8

# Add drivers source code, script to replace metabase dependency, and deps.edn with srtatio mvn repo
COPY ./metabase/modules                                /app/source/modules
COPY ./metabase/bin                                    /app/source/bin
COPY ./metabase/resources/migrations/100_stratio.yaml  /app/source/resources/migrations/100_stratio.yaml
COPY ./metabase/modules/drivers/stratio-repo.edn /root/.clojure/deps.edn

# replace metabase dependency in bin/build-drivers modules so it points to the already-compiled jar
RUN bin/update-metabase-dep.sh

# build the drivers
RUN bin/build-drivers.sh



# ###############################################################################################
# # STAGE 2: runner
# ###############################################################################################

FROM qa.int.stratio.com/stratio/oauth-base:0.2.0-b174182 as runner

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
  && apt-get --only-upgrade install -y libfreetype6 libssl1.1 libssl1.0.0 openssl libsystemd0 libudev1\
  && apt-get -qq clean \
  && rm -rf /var/lib/apt/lists/*

# install JAVA 11
ENV JAVA_HOME=/usr/local/jdk-11.0.2
RUN wget http://tools.stratio.com/jdk/openjdk-11.0.2_linux-x64_bin.tar.gz \
  && tar zfx openjdk-11.0.2_linux-x64_bin.tar.gz -C /usr/local/ \
  && rm -rf $JAVA_HOME/man \
  && rm openjdk-11.0.2_linux-x64_bin.tar.gz
ENV PATH=$JAVA_HOME/bin:$PATH


################################
###        METABASE          ###
################################
# add Metabase uberjar
RUN mkdir -p bin target/uberjar
COPY --from=builder /app/source/target/uberjar/metabase.jar /app/target/uberjar/
COPY --from=builder /app/source/resources/migrations/100_stratio.yaml /app/source/resources/migrations/

# adapt MANIFEST.MF to implementation discovery version
RUN jar xf /app/target/uberjar/metabase.jar META-INF/MANIFEST.MF  \
    && sed -i '1s;^;Implementation-Version: '"$DISCOVERY_VERSION"'\n;' META-INF/MANIFEST.MF \
    && zip /app/target/uberjar/metabase.jar META-INF/MANIFEST.MF \
    && rm -rf META-INF


# Append to the default migration yaml our 100_stratio.yaml and overwrite it inside the jar
RUN jar xf /app/target/uberjar/metabase.jar migrations/000_migrations.yaml \
    && cat /app/source/resources/migrations/100_stratio.yaml >> migrations/000_migrations.yaml \
    && zip /app/target/uberjar/metabase.jar migrations/000_migrations.yaml \
    && rm -rf migrations/000_migrations.yaml

# Fix vulnerability of H2 embedded database (CVE-2022-23221)
# TODO Remove this line when Metabase fixes this vulnerability
RUN zip -d /app/target/uberjar/metabase.jar sample-database.db.mv.db

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
    http://sodio.stratio.com/repository/central/io/prometheus/jmx/jmx_prometheus_javaagent/0.17.1/jmx_prometheus_javaagent-0.17.1.jar \
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
