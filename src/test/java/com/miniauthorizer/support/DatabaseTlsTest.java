package com.miniauthorizer.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabaseTlsTest extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void encryptsTheConnectionToMySql() {
        String tlsVersion = jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Ssl_version'",
                (row, index) -> row.getString("Value"));
        String cipher = jdbc.queryForObject("SHOW SESSION STATUS LIKE 'Ssl_cipher'",
                (row, index) -> row.getString("Value"));

        assertThat(tlsVersion).isEqualTo("TLSv1.2");
        assertThat(cipher).isNotBlank();
    }
}
