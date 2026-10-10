Certificates and keys used **only by `TlsTest`**. They protect nothing and must never be used
outside the test suite.

- `test-server.crt` / `test-server.key`: self-signed server certificate (CN=localhost, EC P-256).
- `test-client-ca.crt`: CA trusted for client certificates (mTLS).
- `test-client-username.p12`: client certificate CN=username, signed by the test CA.
- `test-client-nobody.p12`: client certificate CN=nobody (no such API user), signed by the test CA.
- `test-client-rogue.p12`: client certificate CN=username, signed by an untrusted CA.

The PKCS#12 password is `changeit`.
