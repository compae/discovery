#!/usr/bin/env bash

# instance name uppercase and with dots and dashes replaced by underscores
# this is the prefix tha kms utils uses for the env variables that populates
# with secrets
NORMALIZED_INSTANCE_NAME=${DISCOVERY_INSTANCE_NAME//[.-]/_}
NORMALIZED_INSTANCE_NAME=${NORMALIZED_INSTANCE_NAME^^}

# source kms utils (blog sourced in entrypoint)
source "${STRATIO_UTILS}/kms_utils.sh"

# VAULT_HOSTS variable
declare -a VAULT_HOSTS
IFS_OLD=$IFS
IFS=',' read -r -a VAULT_HOSTS <<< "$VAULT_HOST"
IFS=$IFS_OLD

# login in vault
OLD_IFS=${IFS}
IFS=',' read -r -a VAULT_HOSTS <<< "$VAULT_HOST"
IFS=${OLD_IFS}
INFO "Dynamic login with vault"
if login; then
    INFO "Vault login successful"
    export VAULT_TOKEN
else
    ERROR "Vault login failed"
    exit 1
fi

# Download certs and ca in pem format
if getCert userland "$DISCOVERY_INSTANCE_NAME" "$DISCOVERY_INSTANCE_NAME" PEM /etc/pki/; then
    INFO "Discovery certificate in PEM format downloaded successfully"
else
    ERROR "Discovery certificate in PEM format download failed"
    exit 1
fi

if getCAbundle /etc/pki PEM; then
    INFO "CA bundle in PEM format downloaded successfully"
else
    ERROR "CA bundle in PEM format certificate download failed"
    exit 1
fi

# Fold certificates just in case
for file in "$DISCOVERY_INSTANCE_NAME.pem" "$DISCOVERY_INSTANCE_NAME.key" "ca-bundle.pem"; do
    src_file_path=/etc/pki/$file.orig
    dest_file_path=/etc/pki/$file
    mv "$dest_file_path" "$src_file_path"
    fold -w 64 "$src_file_path" > "$dest_file_path"
done

# rename certs to discovery (it's what we use in the drivers connection strings), and also create
# proxy.pem and proxy.key since we used to have these names and maybe some metabase database is
# still using these paths in its config
if [ "$DISCOVERY_INSTANCE_NAME" != "discovery" ]; then
    mv "/etc/pki/$DISCOVERY_INSTANCE_NAME.pem" /etc/pki/discovery.pem
    mv "/etc/pki/$DISCOVERY_INSTANCE_NAME.key" /etc/pki/discovery.key
fi
cp /etc/pki/discovery.pem /etc/pki/proxy.pem
cp /etc/pki/discovery.key /etc/pki/proxy.key

# Create pkcs8 cert to use in jdbc postgres connection string.
if openssl pkcs8 -topk8 -inform pem \
        -outform der                \
        -nocrypt                    \
        -in  /etc/pki/discovery.key \
        -out /etc/pki/discovery.pk8; then
    INFO "Discovery certificate converted to pk8"
else
    ERROR "Discovery certificate could not be converted to pkcs8"
    exit 1
fi

# Download certs in jks format
if getCert userland "$DISCOVERY_INSTANCE_NAME" "$DISCOVERY_INSTANCE_NAME" JKS /tmp/; then
    INFO "Discovery certificate downloaded successfully"
    PASS_ENV_VAR="${NORMALIZED_INSTANCE_NAME}_KEYSTORE_PASS"
    export KEYSTORE_PASSWORD=${!PASS_ENV_VAR}
    echo "$KEYSTORE_PASSWORD" > /env_vars/KEYSTORE_PASSWORD
else
    ERROR "Discovery certificate download failed"
    exit 1
fi
mv "/tmp/$DISCOVERY_INSTANCE_NAME.jks" /etc/pki/discovery.jks

# Download cabundle in jks format to /etc/pki/ca-bundle.jks
if getCAbundle /etc/pki JKS; then
    INFO "CA bundle downloaded successfully"
    export TRUSTSTORE_PASSWORD="$DEFAULT_KEYSTORE_PASS"
    echo "$TRUSTSTORE_PASSWORD" > /env_vars/TRUSTSTORE_PASSWORD
else
    ERROR "CA bundle certificate download failed"
    exit 1
fi


# download jwt validation key from vault
if [ "$AUTHENTICATOR" = "gosec-sso" ]; then
  INFO "downloading JWT validation public key downloaed"
  # populates INGRESS_NGINX_CONTROLLER_KEOS_INGRESS_JWT_KEYS_PUBLIC_USER (KEOS stores the cert in the key "user" ¯\_(ツ)_/¯)
  if getPass "userland" "ingress-nginx-controller.keos-ingress/jwt-keys" "public"; then
    INFO "JWT validation public key downloaed successfully from Vault"
    JWT_PUBLIC_KEY_FILE="/etc/pki/jwt-public-key.pem"
    echo "$INGRESS_NGINX_CONTROLLER_KEOS_INGRESS_JWT_KEYS_PUBLIC_USER" > "$JWT_PUBLIC_KEY_FILE"
  else
    ERROR "Error while downloading from Vault JWT validation public key."
    exit 1
  fi
fi

# all in all, we have the following secrets
# - certs in pem format:   /etc/pki/[proxy|discovery].pem and /etc/pki/[proxy|discovery].key
# - certs in jks format:   /etc/pki/discovery.jks
# - certs in pk8 format:   /etc/pki/discovery.pk8
#
# - ca in pem format:      /etc/pki/ca-bundle.pem
# - ca in jks format:      /etc/pki/ca-bundle.jks
#
# - jks keystore passworkd in env. variable KEYSTORE_PASSWORD
# - jks trustore passworkd in env. variable TRUSTSTORE_PASSWORD
#
# - when using gosec-sso authenticator, we also have the JWT validation key: /etc/pki/jwt-public-key.pem
#


