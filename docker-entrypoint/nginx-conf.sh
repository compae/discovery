#!/usr/bin/env bash

nginx_conf_dir=/usr/local/openresty/nginx/conf

export NGINX_ERROR_LOG_LEVEL="${NGINX_ERROR_LOG_LEVEL:-error}"
export PROXY_READ_TIMEOUT="${PROXY_READ_TIMEOUT:-120}"
export EMBED_TOKEN_USER_CLAIM="${EMBED_TOKEN_USER_CLAIM:-sub}"
export EXTRA_NGINX_LOG_PROPERTIES="${EXTRA_NGINX_LOG_PROPERTIES:-escape=json}"

if [ "$AUTHENTICATOR" = "gosec-sso" ]; then
    # we are delegating authentication in dcos-oauth so we use the corresponding nginx.conf
    if [ "$LOG_ACCESS_DATA" = "true" ]; then
        # We log all the responses in the nginx
        envsubst '\$NGINX_ERROR_LOG_LEVEL \$PROXY_READ_TIMEOUT \$EXTRA_NGINX_LOG_PROPERTIES \$EMBED_TOKEN_USER_CLAIM' < ${nginx_conf_dir}/nginx.conf.log-access-data > ${nginx_conf_dir}/nginx.conf
    else
        envsubst '\$NGINX_ERROR_LOG_LEVEL \$PROXY_READ_TIMEOUT' < ${nginx_conf_dir}/nginx.conf.sso-auth > ${nginx_conf_dir}/nginx.conf

    fi
# TODO elifs for headers and jwt authenticators to audit log Datio users...
else
    # we are using native authentication so we use the corresponding nginx.conf
    envsubst '\$NGINX_ERROR_LOG_LEVEL \$PROXY_READ_TIMEOUT' < ${nginx_conf_dir}/nginx.conf.native-auth > ${nginx_conf_dir}/nginx.conf

fi

# if we do not have secrets modify nginx.conf to not use ssl
if [[ "$DOWNLOAD_SECRETS" = "false" ]]; then
    sed -i 's/listen 443 ssl/listen 443/ ; /ssl_certificate/d' ${nginx_conf_dir}/nginx.conf
fi
