package com.miniauthorizer;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Starts the application with the "tls" profile and a test-only certificate, and checks what a
 * client actually negotiates. Plain {@link HttpsURLConnection} and {@link SSLSocket} are used on
 * purpose: they expose the handshake and do not depend on the HTTP client of the application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ssl.bundle.pem.server.reload-on-update=false",
        "spring.ssl.bundle.pem.server.keystore.certificate=classpath:tls/test-server.crt",
        "spring.ssl.bundle.pem.server.keystore.private-key=classpath:tls/test-server.key",
        "spring.ssl.bundle.pem.server.truststore.certificate=classpath:tls/test-client-ca.crt"
})
@ActiveProfiles("tls")
class TlsTest extends MySqlIntegrationTest {

    private static SSLContext trustingTestCertificate;
    private static TrustManagerFactory trustManagers;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void trustTheTestCertificate() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        try (InputStream certificate = new ClassPathResource("tls/test-server.crt").getInputStream()) {
            trustStore.setCertificateEntry("test-server", CertificateFactory.getInstance("X.509")
                    .generateCertificate(certificate));
        }
        trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        trustingTestCertificate = SSLContext.getInstance("TLS");
        trustingTestCertificate.init(null, trustManagers.getTrustManagers(), null);
    }

    @Test
    void authenticatesAClientByItsCertificateAlone() throws Exception {
        HttpsURLConnection connection = open("/cartoes/0000000000000000", presenting("tls/test-client-username.p12"));

        assertThat(connection.getResponseCode()).isEqualTo(404);
    }

    @Test
    void refusesCertificatesOfUnknownApiUsers() throws Exception {
        HttpsURLConnection connection = open("/cartoes/0000000000000000", presenting("tls/test-client-nobody.p12"));

        assertThat(connection.getResponseCode()).isEqualTo(401);
    }

    @Test
    void leavesClientsOfAnotherAuthorityUnauthenticated() throws Exception {
        // The server announces which CAs it accepts, so a well-behaved client does not even send
        // a certificate from another one: the request arrives without credentials.
        HttpsURLConnection connection = open("/cartoes/0000000000000000", presenting("tls/test-client-rogue.p12"));

        assertThat(connection.getResponseCode()).isEqualTo(401);
    }

    @Test
    void abortsTheHandshakeWhenAnUntrustedCertificateIsForced() throws Exception {
        HttpsURLConnection connection = open("/cartoes/0000000000000000", forcing("tls/test-client-rogue.p12"));

        assertThatThrownBy(connection::getResponseCode).isInstanceOf(IOException.class);
    }

    @Test
    void servesTheApiOverHttpsWithBasicAuthentication() throws Exception {
        HttpsURLConnection connection = open("/cartoes/0000000000000000");
        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString("username:password".getBytes(StandardCharsets.UTF_8)));

        assertThat(connection.getResponseCode()).isEqualTo(404);
    }

    @Test
    void tellsBrowsersAndClientsToStayOnHttps() throws Exception {
        HttpsURLConnection connection = open("/actuator/health");

        assertThat(connection.getResponseCode()).isEqualTo(200);
        assertThat(connection.getHeaderField("Strict-Transport-Security")).contains("max-age=31536000");
    }

    @Test
    void negotiatesTls13ByDefault() throws Exception {
        try (SSLSocket socket = socket()) {
            socket.startHandshake();

            assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.3");
        }
    }

    @Test
    void acceptsTls12WithForwardSecrecyAndAuthenticatedEncryption() throws Exception {
        try (SSLSocket socket = socket()) {
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});
            socket.setEnabledCipherSuites(new String[]{"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"});
            socket.startHandshake();

            assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.2");
        }
    }

    @Test
    void refusesTls12CipherSuitesWithoutAuthenticatedEncryption() throws Exception {
        try (SSLSocket socket = socket()) {
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});
            socket.setEnabledCipherSuites(new String[]{"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"});

            assertThatThrownBy(socket::startHandshake).isInstanceOf(SSLHandshakeException.class);
        }
    }

    private HttpsURLConnection open(String path) throws Exception {
        return open(path, trustingTestCertificate);
    }

    private HttpsURLConnection open(String path, SSLContext tls) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) URI.create("https://localhost:" + port + path)
                .toURL().openConnection();
        connection.setSSLSocketFactory(tls.getSocketFactory());
        return connection;
    }

    private static SSLContext presenting(String clientCertificate) throws Exception {
        return withClientKey(clientKeyManager(clientCertificate));
    }

    /** Sends the certificate even when its issuer is not among the CAs the server asked for. */
    private static SSLContext forcing(String clientCertificate) throws Exception {
        X509KeyManager delegate = clientKeyManager(clientCertificate);
        return withClientKey(new X509ExtendedKeyManager() {
            @Override
            public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
                return "client";
            }

            @Override
            public String chooseEngineClientAlias(String[] keyTypes, Principal[] issuers, SSLEngine engine) {
                return "client";
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return delegate.getCertificateChain(alias);
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                return delegate.getPrivateKey(alias);
            }

            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return new String[]{"client"};
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return null;
            }

            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return null;
            }
        });
    }

    private static X509KeyManager clientKeyManager(String clientCertificate) throws Exception {
        char[] password = "changeit".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream p12 = new ClassPathResource(clientCertificate).getInputStream()) {
            keyStore.load(p12, password);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);
        return (X509KeyManager) keyManagers.getKeyManagers()[0];
    }

    private static SSLContext withClientKey(KeyManager keyManager) throws Exception {
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(new KeyManager[]{keyManager}, trustManagers.getTrustManagers(), null);
        return tls;
    }

    private SSLSocket socket() throws Exception {
        return (SSLSocket) trustingTestCertificate.getSocketFactory().createSocket("localhost", port);
    }
}
