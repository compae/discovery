#! /usr/bin/env bash

set -euo pipefail

source "./bin/check-clojure-cli.sh"
check_clojure_cli

cd bin/write-mb-checksum
clojure -M -m write-mb-checksum
