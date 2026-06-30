package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class HospitalDAO {

    public List<Hospital> getAll() {
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
