#!/usr/bin/env sh
# Generates a self-signed certificate for running the "tls" profile locally.
# Output goes to certs/, which is git-ignored: private keys never enter the repository.
set -eu

cd "$(dirname "$0")/.."
mkdir -p certs

MSYS_NO_PATHCONV=1 openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days 365 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
  -keyout certs/server.key -out certs/server.crt

echo "Created certs/server.crt and certs/server.key (valid for 365 days)."
