package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class TransferDAO {

    public boolean create(int patientId, int doctorId, int toHospitalId, String urgency, String clinicalNote, boolean fileSent) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "INSERT INTO transfers (patient_id, doctor_id, to_hospital_id, urgency, clinical_note, file_sent) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, patientId);
            stmt.setInt(2, doctorId);
            stmt.setInt(3, toHospitalId);
            stmt.setString(4, urgency);
            stmt.setString(5, clinicalNote);
            stmt.setBoolean(6, fileSent);
            stmt.executeUpdate();
            conn.close();
            return true;
        } catch (Exception e) {
            System.out.println("Error creating transfer: " + e.getMessage());
            return false;
        }
    }

    public List<Transfer> getByHospitalId(int hospitalId) {
        List<Transfer> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM transfers WHERE to_hospital_id = ? ORDER BY created_at DESC";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, hospitalId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(buildTransfer(rs));
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return list;
    }

    public Transfer getById(int id) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM transfers WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Transfer t = buildTransfer(rs);
                conn.close();
                return t;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    public Transfer getByDoctorAndPatient(int doctorId, int patientId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM transfers WHERE doctor_id = ? AND patient_id = ? ORDER BY created_at DESC LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, doctorId);
            stmt.setInt(2, patientId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Transfer t = buildTransfer(rs);
                conn.close();
                return t;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    public Transfer getActiveByDoctorAndPatient(int doctorId, int patientId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM transfers WHERE doctor_id = ? AND patient_id = ? AND status IN ('new', 'accepted') ORDER BY created_at DESC LIMIT 1";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, doctorId);
            stmt.setInt(2, patientId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Transfer t = buildTransfer(rs);
                conn.close();
                return t;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return null;
    }

    public void updateStatus(int id, String status) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE transfers SET status = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public void decline(int id, String reason) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE transfers SET status = 'declined', decline_reason = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, reason);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public void requestFile(int id) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE transfers SET file_requested = TRUE WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public void approveFile(int id) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE transfers SET file_approved = TRUE WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private Transfer buildTransfer(ResultSet rs) throws Exception {
        Transfer t = new Transfer();
        t.setId(rs.getInt("id"));
        t.setPatientId(rs.getInt("patient_id"));
        t.setDoctorId(rs.getInt("doctor_id"));
        t.setFromHospitalId(rs.getInt("from_hospital_id"));
        t.setToHospitalId(rs.getInt("to_hospital_id"));
        t.setUrgency(rs.getString("urgency"));
        t.setClinicalNote(rs.getString("clinical_note"));
        t.setFileSent(rs.getBoolean("file_sent"));
        t.setFileRequested(rs.getBoolean("file_requested"));
        t.setFileApproved(rs.getBoolean("file_approved"));
        t.setStatus(rs.getString("status"));
        t.setDeclineReason(rs.getString("decline_reason"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        return t;
    }
}
