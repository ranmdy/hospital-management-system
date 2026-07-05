package com.example.ehospital;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    public static Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://hayabusa.proxy.rlwy.net:52944/railway";
        String user = "root";
        String password = "blYcAOrbvpLUSaRZCTpvfHAKyQfyRrCw";

        return DriverManager.getConnection(url, user, password);
    }
}
