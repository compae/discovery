#!/usr/bin/env bash


# b-log sourcing and configuration
source /usr/local/lib/b-log.sh
export DOCKER_LOG_LEVEL=${DOCKER_LOG_LEVEL:-${BASH_LOG_LEVEL:-INFO}}
eval LOG_LEVEL_${DOCKER_LOG_LEVEL}
B_LOG --stdout true

# set nginx configuration
source /docker-entrypoint/nginx-conf.sh

# download secrets
if [[ "$DOWNLOAD_SECRETS" = "true" ]]; then
  source /docker-entrypoint/secrets.sh
  source /docker-entrypoint/crossdata.sh
fi

# prepare some environment variables for metabase
source /docker-entrypoint/metabase-environment.sh
