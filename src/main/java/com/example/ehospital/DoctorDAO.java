package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DoctorDAO {

    public boolean register(String name, String email, String password, String specialty, String licenseNumber) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "INSERT INTO doctors (name, email, password, specialty, license_number) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, password);
            stmt.setString(4, specialty);
            stmt.setString(5, licenseNumber);
            stmt.executeUpdate();
            conn.close();
            return true;
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
            return false;
        }
    }

    public Doctor login(String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM doctors WHERE email = ? AND password = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Doctor doctor = new Doctor();
                doctor.setId(rs.getInt("id"));
                doctor.setName(rs.getString("name"));
                doctor.setEmail(rs.getString("email"));
                doctor.setSpecialty(rs.getString("specialty"));
                doctor.setLicenseNumber(rs.getString("license_number"));
                doctor.setStatus(rs.getString("status"));
                conn.close();
                return doctor;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Login failed: " + e.getMessage());
        }
        return null;
    }
}
