package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

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
                Patient patient = buildPatient(rs);
                conn.close();
                return patient;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Login failed: " + e.getMessage());
        }
        return null;
    }

    public Patient getById(int id) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM patients WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Patient p = buildPatient(rs);
                conn.close();
                return p;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get patient failed: " + e.getMessage());
        }
        return null;
    }

    public void saveSymptoms(int patientId, String symptoms, String illnessClass) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE patients SET symptoms = ?, illness_class = ?, status = 'pending' WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, symptoms);
            stmt.setString(2, illnessClass);
            stmt.setInt(3, patientId);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Save symptoms failed: " + e.getMessage());
        }
    }

    public void assignDoctor(int patientId, int doctorId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE patients SET assigned_doctor_id = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, doctorId);
            stmt.setInt(2, patientId);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Assign doctor failed: " + e.getMessage());
        }
    }

    public void updateStatus(int patientId, String status) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE patients SET status = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, status);
            stmt.setInt(2, patientId);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Update status failed: " + e.getMessage());
        }
    }

    /*public List<Patient> getPendingBySpecialty(String specialty) {
        List<Patient> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT p.* FROM patients p " +
                    "JOIN doctors d ON p.assigned_doctor_id = d.id " +
                    "WHERE p.status = 'pending' AND d.specialty = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, specialty);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(buildPatient(rs));
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get pending patients failed: " + e.getMessage());
        }
        return list;
    }*/

    public List<Patient> getPendingForDoctor(int doctorId) {
        List<Patient> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM patients WHERE assigned_doctor_id = ? AND status = 'pending'";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, doctorId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(buildPatient(rs));
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get pending for doctor failed: " + e.getMessage());
        }
        return list;
    }

    public Patient getInConsultForDoctor(int doctorId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM patients WHERE assigned_doctor_id = ? AND status = 'in_consult'";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, doctorId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Patient p = buildPatient(rs);
                conn.close();
                return p;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get in-consult failed: " + e.getMessage());
        }
        return null;
    }

    private Patient buildPatient(ResultSet rs) throws Exception {
        Patient p = new Patient();
        p.setId(rs.getInt("id"));
        p.setName(rs.getString("name"));
        p.setEmail(rs.getString("email"));
        p.setSymptoms(rs.getString("symptoms"));
        p.setIllnessClass(rs.getString("illness_class"));
        p.setStatus(rs.getString("status"));
        p.setAssignedDoctorId(rs.getInt("assigned_doctor_id"));
        return p;
    }

    public void assignToBed(int patientId, String bedId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE patients SET status = 'admitted', bed_id = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, bedId);
            stmt.setInt(2, patientId);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Bed assignment failed: " + e.getMessage());
        }
    }

    public void dischargeFromBed(int patientId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "UPDATE patients SET status = 'new', bed_id = NULL WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, patientId);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Discharge failed: " + e.getMessage());
        }
    }

    public Patient getAdmittedPatientByBed(String bedId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM patients WHERE bed_id = ? AND status = 'admitted'";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, bedId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Patient p = buildPatient(rs);
                // Manually set bed_id since we didn't add it to buildPatient()
                p.setBedId(rs.getString("bed_id"));
                conn.close();
                return p;
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get patient by bed failed: " + e.getMessage());
        }
        return null;
    }

}
