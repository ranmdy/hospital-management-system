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
                Doctor doctor = buildDoctor(rs);
                conn.close();
                return doctor;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Login failed: " + e.getMessage());
        }
        return null;
    }

    public Doctor getById(int id) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM doctors WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Doctor d = buildDoctor(rs);
                conn.close();
                return d;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get doctor failed: " + e.getMessage());
        }
        return null;
    }

    public Doctor findAvailableBySpecialty(String specialty) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            // try exact specialty match first (case-insensitive)
            String sql = "SELECT * FROM doctors WHERE LOWER(specialty) = LOWER(?) AND status = 'available' LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, specialty);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Doctor d = buildDoctor(rs);
                conn.close();
                return d;
            }
            // fallback: any available doctor
            sql = "SELECT * FROM doctors WHERE status = 'available' LIMIT 1";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            if (rs.next()) {
                Doctor d = buildDoctor(rs);
                conn.close();
                return d;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Find doctor failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Finds a doctor by specialty who is available OR busy (not on_hold).
     * Prefers available doctors; falls back to busy ones so patients still queue
     * to a doctor who is already in a consultation.
     */
    public Doctor findBySpecialtyAny(String specialty) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            // prefer available first, then busy — skip on_hold
            String sql = "SELECT * FROM doctors WHERE LOWER(specialty) = LOWER(?) " +
                    "AND status IN ('available', 'busy') " +
                    "ORDER BY CASE status WHEN 'available' THEN 0 ELSE 1 END LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, specialty);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Doctor d = buildDoctor(rs);
                conn.close();
                return d;
            }
            // fallback: any non-on_hold doctor regardless of specialty
            sql = "SELECT * FROM doctors WHERE status IN ('available', 'busy') " +
                    "ORDER BY CASE status WHEN 'available' THEN 0 ELSE 1 END LIMIT 1";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            if (rs.next()) {
                Doctor d = buildDoctor(rs);
                conn.close();
                return d;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Find doctor failed: " + e.getMessage());
        }
        return null;
    }

    public boolean existsByLicense(String licenseNumber) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT id FROM doctors WHERE license_number = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, licenseNumber);
            ResultSet rs = stmt.executeQuery();
            boolean exists = rs.next();
            conn.close();
            return exists;
        } catch (Exception e) {
            System.out.println("License check failed: " + e.getMessage());
            return false;
        }
    }

    public void updateStatus(int doctorId, String status) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE doctors SET status = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setInt(2, doctorId);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Update status failed: " + e.getMessage());
        }
    }

    private Doctor buildDoctor(ResultSet rs) throws Exception {
        Doctor d = new Doctor();
        d.setId(rs.getInt("id"));
        d.setName(rs.getString("name"));
        d.setEmail(rs.getString("email"));
        d.setSpecialty(rs.getString("specialty"));
        d.setLicenseNumber(rs.getString("license_number"));
        d.setStatus(rs.getString("status"));
        return d;
    }
}
