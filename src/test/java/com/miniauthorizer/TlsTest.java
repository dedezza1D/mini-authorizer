package com.miniauthorizer;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
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
        "spring.ssl.bundle.pem.server.keystore.private-key=classpath:tls/test-server.key"
})
@ActiveProfiles("tls")
class TlsTest extends MySqlIntegrationTest {

    private static SSLContext trustingTestCertificate;

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
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        trustingTestCertificate = SSLContext.getInstance("TLS");
        trustingTestCertificate.init(null, trustManagers.getTrustManagers(), null);
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
        HttpsURLConnection connection = (HttpsURLConnection) URI.create("https://localhost:" + port + path)
                .toURL().openConnection();
        connection.setSSLSocketFactory(trustingTestCertificate.getSocketFactory());
        return connection;
    }

    private SSLSocket socket() throws Exception {
        return (SSLSocket) trustingTestCertificate.getSocketFactory().createSocket("localhost", port);
    }
}
