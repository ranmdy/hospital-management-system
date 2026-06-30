package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class HospitalDAO {

    public List<Hospital> getAll() {
        seedIfEmpty();
        List<Hospital> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM hospitals ORDER BY name";
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(buildHospital(rs));
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Error loading hospitals: " + e.getMessage());
        }
        return list;
    }

    private void seedIfEmpty() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.prepareStatement("SELECT COUNT(*) FROM hospitals").executeQuery();
            rs.next();
            int count = rs.getInt(1);
            if (count == 0) {
                String sql = "INSERT INTO hospitals (name, location, total_beds, available_beds) VALUES (?, ?, ?, ?)";
                String[][] data = {
                    {"Lagoon Hospital", "Victoria Island", "120", "18"},
                    {"Reddington Hospital", "Ikeja", "80", "12"},
                    {"St. Nicholas Hospital", "Lagos Island", "60", "8"},
                    {"EKO Hospital", "Surulere", "100", "15"},
                    {"First Consultant Hospital", "Ikoyi", "50", "6"}
                };
                for (String[] row : data) {
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, row[0]);
                    stmt.setString(2, row[1]);
                    stmt.setInt(3, Integer.parseInt(row[2]));
                    stmt.setInt(4, Integer.parseInt(row[3]));
                    stmt.executeUpdate();
                }
                System.out.println("Seeded 5 sample hospitals.");
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Error seeding hospitals: " + e.getMessage());
        }
    }

    public Hospital getById(int id) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM hospitals WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Hospital h = buildHospital(rs);
                conn.close();
                return h;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    public Hospital getByAdminId(int adminId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT h.* FROM hospitals h JOIN hospital_admins a ON a.hospital_id = h.id WHERE a.id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, adminId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Hospital h = buildHospital(rs);
                conn.close();
                return h;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    public void incrementBed(int hospitalId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE hospitals SET available_beds = available_beds + 1 WHERE id = ? AND available_beds < total_beds";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, hospitalId);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Error incrementing bed: " + e.getMessage());
        }
    }

    public void decrementBed(int hospitalId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE hospitals SET available_beds = available_beds - 1 WHERE id = ? AND available_beds > 0";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, hospitalId);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Error decrementing bed: " + e.getMessage());
        }
    }

    private Hospital buildHospital(ResultSet rs) throws Exception {
        Hospital h = new Hospital();
        h.setId(rs.getInt("id"));
        h.setName(rs.getString("name"));
        h.setLocation(rs.getString("location"));
        h.setTotalBeds(rs.getInt("total_beds"));
        h.setAvailableBeds(rs.getInt("available_beds"));
        return h;
    }
}
