#! /usr/bin/env bash

set -euo pipefail

source "./bin/check-clojure-cli.sh"
check_clojure_cli

cd /app/source/bin/update-metabase-dep
clojure -M -m update-metabase-dep
