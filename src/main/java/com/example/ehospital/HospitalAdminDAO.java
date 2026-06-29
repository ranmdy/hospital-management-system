package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class HospitalAdminDAO {

    public boolean register(String name, String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "INSERT INTO hospital_admins (name, email, password) VALUES (?, ?, ?)";
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

    public HospitalAdmin login(String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM hospital_admins WHERE email = ? AND password = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                HospitalAdmin admin = new HospitalAdmin();
                admin.setId(rs.getInt("id"));
                admin.setName(rs.getString("name"));
                admin.setEmail(rs.getString("email"));
                admin.setHospitalId(rs.getInt("hospital_id"));
                conn.close();
                return admin;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Login failed: " + e.getMessage());
        }
        return null;
    }
}
