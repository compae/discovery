#!/usr/bin/env bash

function pem_to_jks() {
    local pem_in="$1"
    local key_in="$2"
    local jks_out="$3"
    local -n pass_holding_var="$4" # this parameter is the name of the variable that will hold the password

    # random password
    local password
    password=$(openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | head -c 32)

    # first create p12, needed to create JKS
    p12_tmp=$(mktemp -p /dev/shm)
    if openssl_msg=$(openssl pkcs12 -export \
        -in "$pem_in" \
        -inkey "$key_in" \
        -passout "pass:$password" \
        -out "$p12_tmp" 2>&1)
    then
        INFO "[CERTS] P12 cert from PEM and KEY $pem_in, $key_in created"
        DEBUG "[CERTS] OpenSSL msg: $openssl_msg"
    else
        ERROR "[CERTS] error creating P12 as pre-step for creating $jks_out JKS"
        DEBUG "[CERTS] OpenSSL msg: $openssl_msg"
        return 1
    fi

    # create JKS
    if keytool_msg=$(keytool -importkeystore \
        -srckeystore "$p12_tmp" \
        -srcstorepass "$password" \
        -srcstoretype PKCS12 \
        -destkeystore "$jks_out" \
        -deststorepass "$password" \
        -noprompt 2>&1)
    then
        INFO "[CERTS] $jks_out created"
        DEBUG "[CERTS] keytool msg: $keytool_msg"
    else
        ERROR "[CERTS] error creating $jks_out JKS"
        DEBUG "[CERTS] keytool msg: $keytool_msg"
        return 1
    fi
    rm -rf "$p12_tmp"

    #return password
    export pass_holding_var="$password"
}


ca_bundle_to_jks() {
    local ca_pem="$1"
    local jks_out="$2"
    local -n pass_holding_var="$3" # this parameter is the name of the variable that will hold the password

    if [[ ! -f "$ca_pem" ]]; then
        ERROR "[CERTS] PEM ca-bundle not found: $ca_pem" >&2
        return 1
    fi

    local password
    password=$(openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | head -c 32)

    local tmp_dir
    tmp_dir=$(mktemp -d)

    # split the bundle into several separate certs (keytool needs to have them separated)
    if ! csplit -z -f "$tmp_dir/ca_" -b "%02d.pem" "$ca_pem" '/-----BEGIN CERTIFICATE-----/' '{*}' >/dev/null
    then
        rm -rf "$tmp_dir"
        ERROR "[CERTS] Error splitting ca-bundle into separate certs"
        return 1
    fi

    # import each individual file to the truststore
    for ca_file in "$tmp_dir"/ca_*.pem; do
        if keytool_msg=$(keytool -importcert \
            -keystore "$jks_out" \
            -storetype JKS \
            -storepass "$password" \
            -alias "$(basename "$ca_file" .pem)" \
            -file "$ca_file" \
            -noprompt 2>&1)
        then
            INFO "[CERTS] CA $ca_file imported to ca-bundle.jks"
            DEBUG "[CERTS] keytool msg: $keytool_msg"
        else
            ERROR "[CERTS] Error importing CA $ca_file to ca-bundle.jks"
            DEBUG "[CERTS] keytool msg: $keytool_msg"
        fi
    done
    rm -rf "$tmp_dir"

    export pass_holding_var="$password"
}
