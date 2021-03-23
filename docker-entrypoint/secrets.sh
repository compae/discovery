#!/usr/bin/env bash

# instance name uppercase and with dots and dashes replaced by underscores
# this is the prefix tha kms utils uses for the env variables that populates
# with secrets
NORMALIZED_INSTANCE_NAME=${DISCOVERY_INSTANCE_NAME//[.-]/_}
NORMALIZED_INSTANCE_NAME=${NORMALIZED_INSTANCE_NAME^^}

# source kms utils (blog sourced in entrypoint)
source /usr/local/lib/kms_utils.sh

# VAULT_HOSTS variable
declare -a VAULT_HOSTS
IFS_OLD=$IFS
IFS=',' read -r -a VAULT_HOSTS <<< "$VAULT_HOST"
IFS=$IFS_OLD

# the login in vault has done in the oauth-base rc.local

# Download certs in jks format
if getCert userland "$DISCOVERY_INSTANCE_NAME" "$DISCOVERY_INSTANCE_NAME" JKS /etc/pki/; then
    INFO "Discovery certificate downloaded successfully"
    PASS_ENV_VAR="${NORMALIZED_INSTANCE_NAME}_KEYSTORE_PASS"
    export KEYSTORE_PASSWORD=${!PASS_ENV_VAR}
    echo "$KEYSTORE_PASSWORD" > /env_vars/KEYSTORE_PASSWORD
else
    ERROR "Discovery certificate download failed"
    exit 1
fi
mv "/etc/pki/$DISCOVERY_INSTANCE_NAME.jks" /etc/pki/discovery.jks

# Download cabundle in jks format to /etc/pki/ca-bundle.jks
if getCAbundle /etc/pki JKS; then
    INFO "CA bundle downloaded successfully"
    export TRUSTSTORE_PASSWORD="$DEFAULT_KEYSTORE_PASS"
    echo "$TRUSTSTORE_PASSWORD" > /env_vars/TRUSTSTORE_PASSWORD
else
    ERROR "CA bundle certificate download failed"
    exit 1
fi

# Create pkcs8 cert to use in jdbc postgres connection string. The cert in PEM format
# has already been downloaded to be used by openresty in the rc.local script and stored
# in /etc/pki/proxy.pem and /etc/pki/proxy.key (the names that openresty wants...)
cp /etc/pki/proxy.pem /etc/pki/discovery.pem
cp /etc/pki/proxy.key /etc/pki/discovery.key
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


# all in all, between oauth-base rc.local and this, we have the following secrets
# - certs in pem format:   /etc/pki/[proxy|discovery].pem and /etc/pki/[proxy|discovery].key
# - certs in jks format:   /etc/pki/discovery.jks
# - certs in pk8 format:   /etc/pki/discovery.pk8
#
# - ca in pem format:      /etc/pki/ca-bundle.pem
# - ca in jks format:      /etc/pki/ca-bundle.jks
#
# - jks keystore passworkd in env. variable KEYSTORE_PASSWORD
# - jks trustore passworkd in env. variable TRUSTSTORE_PASSWORD


if [[ "$LOG_ACCESS_DATA" = "true" ]]; then
  INFO "Obtaining passwords to encrypt data audit"
  INFO "Log data audit enabled, getting encrypt passwords"

  # Get passwords to encrypt logs of data audit as $DATA_AUDIT_ENCRYPTION-KEY_PASS and $DATA_AUDIT_ENCRYPTION-IV_PASS
  if getPass "userland" "$DISCOVERY_INSTANCE_NAME" "encryption-key"; then
      INFO "Encryption key pass downloaded successfully"
      prefix=${DISCOVERY_INSTANCE_NAME//-/_}
      prefix=${prefix//./_}
      prefix=${prefix^^}
      MYVAR_USER=${prefix}_ENCRYPTION_KEY_USER
      MYVAR_PASS=${prefix}_ENCRYPTION_KEY_PASS
      echo ${!MYVAR_PASS} > /env_vars/DATA_AUDIT_ENCRYPTION_KEY_PASS
  else
      ERROR "Encryption key pass download failed"
      exit 1
  fi

  if getPass "userland" "$DISCOVERY_INSTANCE_NAME" "encryption-iv"; then
      INFO "Encryption IV pass downloaded successfully"
      prefix=${DISCOVERY_INSTANCE_NAME//-/_}
      prefix=${prefix//./_}
      prefix=${prefix^^}
      MYVAR_USER=${prefix}_ENCRYPTION_IV_USER
      MYVAR_PASS=${prefix}_ENCRYPTION_IV_PASS
      echo ${!MYVAR_PASS} > /env_vars/DATA_AUDIT_ENCRYPTION_IV_PASS
  else
      ERROR "Encryption IV pass download failed"
      exit 1
  fi
  INFO "Passwords to encrypt data audit: OK"
fi