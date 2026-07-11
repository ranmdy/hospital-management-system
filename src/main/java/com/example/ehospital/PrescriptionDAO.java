package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class PrescriptionDAO {

    public boolean save(int patientId, int doctorId, String medicine, String dosage, String notes) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "INSERT INTO prescriptions (patient_id, doctor_id, medicine, dosage, notes) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, patientId);
            stmt.setInt(2, doctorId);
            stmt.setString(3, medicine);
            stmt.setString(4, dosage);
            stmt.setString(5, notes);
            stmt.executeUpdate();
            conn.close();
            return true;
        } catch (Exception e) {
            System.out.println("Save prescription failed: " + e.getMessage());
            return false;
        }
    }

    public Prescription getByPatientId(int patientId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM prescriptions WHERE patient_id = ? ORDER BY created_at DESC LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, patientId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Prescription p = new Prescription();
                p.setId(rs.getInt("id"));
                p.setPatientId(rs.getInt("patient_id"));
                p.setDoctorId(rs.getInt("doctor_id"));
                p.setMedicine(rs.getString("medicine"));
                p.setDosage(rs.getString("dosage"));
                p.setNotes(rs.getString("notes"));
                p.setCreatedAt(rs.getTimestamp("created_at"));
                conn.close();
                return p;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get prescription failed: " + e.getMessage());
        }
        return null;
    }

    public List<Prescription> getAllByPatientId(int patientId) {
        List<Prescription> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM prescriptions WHERE patient_id = ? ORDER BY created_at DESC";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, patientId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(buildPrescription(rs));
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get all prescriptions failed: " + e.getMessage());
        }
        return list;
    }

    public List<Prescription> getAllByDoctorId(int doctorId) {
        List<Prescription> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM prescriptions WHERE doctor_id = ? ORDER BY created_at DESC";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, doctorId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(buildPrescription(rs));
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get doctor prescriptions failed: " + e.getMessage());
        }
        return list;
    }

    private Prescription buildPrescription(ResultSet rs) throws Exception {
        Prescription p = new Prescription();
        p.setId(rs.getInt("id"));
        p.setPatientId(rs.getInt("patient_id"));
        p.setDoctorId(rs.getInt("doctor_id"));
        p.setMedicine(rs.getString("medicine"));
        p.setDosage(rs.getString("dosage"));
        p.setNotes(rs.getString("notes"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        return p;
    }
}
