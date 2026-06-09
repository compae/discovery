#!/usr/bin/env bash

# load pem_to_jks and ca_bundle_to_jsk aux functions to create JKS certs
source /docker-entrypoint/cert-utils.sh

# The vault injector provides secrets in /vault/secrets. We just need to copy them to the
# "standard" location to ensure retrocompatibility (some virtualizer db connectcions may
# refer to the /etc/pki paths)
cp /vault/secrets/cert.crt       /etc/pki/discovery.pem
cp /vault/secrets/cert.key       /etc/pki/discovery.key
cp /vault/secrets/ca-bundle.pem  /etc/pki/ca-bundle.pem

# Create pkcs8 cert to use in jdbc postgres connection string.
if openssl pkcs8 -topk8 -inform pem \
        -outform der                \
        -nocrypt                    \
        -in  /etc/pki/discovery.key \
        -out /etc/pki/discovery.pk8; then
    INFO "[CERTS] Discovery certificate converted to pk8"
else
    ERROR "[CERTS] Discovery certificate could not be converted to pkcs8"
    exit 1
fi

# Convert certs to JKS format and get random password
pem_to_jks /etc/pki/discovery.pem /etc/pki/discovery.key /etc/pki/discovery.jks KEYSTORE_PASSWORD || exit 1
echo "$KEYSTORE_PASSWORD" > /env_vars/KEYSTORE_PASSWORD
export KEYSTORE_PASSWORD

# Convert ca-bundle to JKS and get generated password
ca_bundle_to_jks /etc/pki/ca-bundle.pem /etc/pki/ca-bundle.jks TRUSTSTORE_PASSWORD || exit 1
echo "$TRUSTSTORE_PASSWORD" > /env_vars/TRUSTSTORE_PASSWORD
export TRUSTSTORE_PASSWORD


# download jwt validation key from vault
if [ "$AUTHENTICATOR" = "gosec-sso" ]; then
    JWT_PUBLIC_KEY_FILE="/etc/pki/jwt-public-key.pem"
    cp /vault/secrets/jwt-public-key.pem "$JWT_PUBLIC_KEY_FILE"
fi

# all in all, we have the following secrets
# - certs in pem format:   /etc/pki/discovery.pem and /etc/pki/discovery.key
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


