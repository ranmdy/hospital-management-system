package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PatientDAO {

    public boolean register(String name, String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "INSERT INTO patients (name, email, password) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.executeUpdate();
            conn.close();
            return true;
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
            return false;
        }
    }

    public Patient login(String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM patients WHERE email = ? AND password = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Patient patient = new Patient();
                patient.setId(rs.getInt("id"));
                patient.setName(rs.getString("name"));
                patient.setEmail(rs.getString("email"));
                patient.setStatus(rs.getString("status"));
                conn.close();
                return patient;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Login failed: " + e.getMessage());
        }
        return null;
    }
}
