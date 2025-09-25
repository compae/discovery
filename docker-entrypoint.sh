#!/usr/bin/env bash

# b-log sourcing and configuration
source "${STRATIO_UTILS}/b-log.sh"
export DOCKER_LOG_LEVEL=${DOCKER_LOG_LEVEL:-${BASH_LOG_LEVEL:-INFO}}
eval "LOG_LEVEL_${DOCKER_LOG_LEVEL}"
B_LOG --stdout true

# set nginx configuration
source /docker-entrypoint/nginx-conf.sh

# download secrets
DOWNLOAD_SECRETS=${DOWNLOAD_SECRETS:-"true"}
if [[ "$DOWNLOAD_SECRETS" = "true" ]]; then
  source /docker-entrypoint/secrets.sh
  source /docker-entrypoint/crossdata.sh
fi

# download extra jars
source /docker-entrypoint/extra-jars.sh

# set cicd configuration
source /docker-entrypoint/cicd-conf.sh

# prepare some environment variables for metabase
source /docker-entrypoint/metabase-environment.sh

exec /usr/bin/s6-svscan /etc/sv
