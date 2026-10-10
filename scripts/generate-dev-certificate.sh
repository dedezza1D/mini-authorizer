#!/usr/bin/env sh
# Generates the certificates for running the "tls" profile locally, mTLS included:
#   certs/server.crt, certs/server.key    HTTPS certificate for localhost
#   certs/client-ca.crt                   CA trusted for client certificates
#   certs/client.p12 (password changeit)  client certificate for the API user "username"
# Output goes to certs/, which is git-ignored: private keys never enter the repository.
set -eu

cd "$(dirname "$0")/.."
mkdir -p certs
export MSYS_NO_PATHCONV=1

openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days 365 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
  -keyout certs/server.key -out certs/server.crt

openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days 365 \
  -subj "/CN=Mini Authorizer Dev Client CA" \
  -keyout certs/client-ca.key -out certs/client-ca.crt

openssl req -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes \
  -subj "/CN=username" -keyout certs/client.key -out certs/client.csr
printf 'extendedKeyUsage=clientAuth\n' > certs/client.ext
openssl x509 -req -in certs/client.csr -CA certs/client-ca.crt -CAkey certs/client-ca.key \
  -CAcreateserial -days 30 -extfile certs/client.ext -out certs/client.crt
openssl pkcs12 -export -in certs/client.crt -inkey certs/client.key -out certs/client.p12 \
  -passout pass:changeit -name client
rm -f certs/client.csr certs/client.ext certs/client-ca.srl

echo "Created the server certificate, the client CA and certs/client.p12 (CN=username)."
