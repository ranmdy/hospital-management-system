package com.example.ehospital;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://hayabusa.proxy.rlwy.net:52944/railway");
        config.setUsername("root");
        config.setPassword("blYcAOrbvpLUSaRZCTpvfHAKyQfyRrCw");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(3);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
