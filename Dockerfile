# ###############################################################################################
# STAGE 1: stratio drivers builder
# ###############################################################################################

FROM qa.int.stratio.com/stratio/stratio-metabase-builder:0.40.7-0.1.0-SNAPSHOT as builder

WORKDIR /app/source

ENV FC_LANG en-US
ENV LC_CTYPE en_US.UTF-8

# Add drivers source code and script to calculate correct metabase checksum
# (so the driver build proccess does not trigger a metabase rebuild)
ADD ./metabase/modules /app/source/modules
ADD ./metabase/bin     /app/source/bin

# write checksum file so drivers build does not trigger metabase build
RUN bin/write-mb-checksum.sh

# build the drivers
# Uncomment below to be able to resolve niquel.int.stratio.con in local builds (needed to build crossdata driver)
# RUN echo -e "search stratio.com\nnameserver 10.120.0.42\nnameserver 172.19.0.42\n" >> /etc/resolv.conf \
#   && bin/build-drivers.sh
RUN bin/build-drivers.sh



# ###############################################################################################
# # STAGE 2: runner
# ###############################################################################################

FROM qa.int.stratio.com/stratio/oauth-base:0.2.0-c7fbb63 as runner

WORKDIR /app

ENV FC_LANG en-US
ENV LC_CTYPE en_US.UTF-8

################################
###       DEPENDENCIES       ###
################################
# apt things
RUN apt-get update \
  &&  apt-get install -y ttf-dejavu fontconfig wget \
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

# add fixed cacerts
# COPY --from=builder /etc/ssl/certs/java/cacerts /usr/lib/jvm/default-jvm/jre/lib/security/cacerts
COPY --from=builder /etc/ssl/certs/java/cacerts $JAVA_HOME/lib/security/cacerts


################################
###        METABASE          ###
################################
# add Metabase uberjar
RUN mkdir -p bin target/uberjar
COPY --from=builder /app/source/target/uberjar/metabase.jar /app/target/uberjar/

# create the plugins directory, with writable permissions
RUN mkdir -p /plugins
RUN chmod a+rwx /plugins

# add stratio drivers jars to plugins folder
COPY --from=builder /app/source/resources/modules/* /plugins/


################################
###  PROMETHEUS, LOGS, CICD  ###
################################
# Download prometheus jar
RUN wget --no-verbose -O /app/target/uberjar/jmx_prometheus_javaagent-0.14.0.jar \
    http://sodio.stratio.com/repository/central/io/prometheus/jmx/jmx_prometheus_javaagent/0.14.0/jmx_prometheus_javaagent-0.14.0.jar

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
