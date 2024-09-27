#!/usr/bin/env bash

nginx_conf_dir=/usr/local/openresty/nginx/conf

export NGINX_ERROR_LOG_LEVEL="${NGINX_ERROR_LOG_LEVEL:-error}"
export NGINX_WORKER_PROCESSES="${NGINX_WORKER_PROCESSES:-1}"
export NGINX_EVENT_CONNECTIONS="${NGINX_EVENT_CONNECTIONS:-1024}"
export NGINX_CLIENT_MAX_BODY_SIZE="${NGINX_CLIENT_MAX_BODY_SIZE:-2M}"
export PROXY_READ_TIMEOUT="${PROXY_READ_TIMEOUT:-300}"
export EMBED_TOKEN_USER_CLAIM="${EMBED_TOKEN_USER_CLAIM:-sub}"
export EXTRA_NGINX_LOG_PROPERTIES="${EXTRA_NGINX_LOG_PROPERTIES:-escape=json}"

if [ "$LOG_ACCESS_DATA" = "true" ]; then
    # We log all the responses in the nginx
    envsubst '\$NGINX_ERROR_LOG_LEVEL \$NGINX_WORKER_PROCESSES \$NGINX_EVENT_CONNECTIONS \$NGINX_CLIENT_MAX_BODY_SIZE \$PROXY_READ_TIMEOUT \$EXTRA_NGINX_LOG_PROPERTIES \$EMBED_TOKEN_USER_CLAIM' < ${nginx_conf_dir}/nginx.conf.log-access-data > ${nginx_conf_dir}/nginx.conf
else
    envsubst '\$NGINX_ERROR_LOG_LEVEL \$NGINX_WORKER_PROCESSES \$NGINX_EVENT_CONNECTIONS \$NGINX_CLIENT_MAX_BODY_SIZE \$PROXY_READ_TIMEOUT' < ${nginx_conf_dir}/nginx.conf.template > ${nginx_conf_dir}/nginx.conf
fi


# if we do not have secrets modify nginx.conf to not use ssl
if [[ "$DOWNLOAD_SECRETS" = "false" ]]; then
    sed -i 's/listen 443 ssl/listen 443/ ; /ssl_certificate/d ; /ssl_protocols/d' ${nginx_conf_dir}/nginx.conf
fi
