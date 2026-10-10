package com.miniauthorizer.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for tests that need a database. A single container running the same MySQL 5.7
 * as the docker-compose file is shared by the whole run, so Spring can reuse cached contexts.
 */
@Tag("integration")
public abstract class MySqlIntegrationTest {

    // Same TLS requirement as the application's default datasource URL.
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:5.7")
            .withDatabaseName("miniautorizador")
            .withUrlParam("sslMode", "REQUIRED")
            .withUrlParam("tlsVersions", "TLSv1.2");

    static {
        MYSQL.start();
    }
}
