package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class PatientDashboardController {

    @FXML private Label greetingLabel;
    @FXML private Label subLabel;
    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label statusLabel;
    @FXML private Label consultTitle;
    @FXML private Label consultDesc;
    @FXML private Label doctorAvatar;
    @FXML private Label doctorNameLabel;
    @FXML private Label doctorSpecLabel;
    @FXML private Label doctorStatusLabel;

    @FXML
    public void initialize() {
        Patient patient = SessionManager.getPatient();
        if (patient == null) return;

        // refresh from database
        PatientDAO dao = new PatientDAO();
        patient = dao.getById(patient.getId());
        SessionManager.loginAsPatient(patient);

        String name = patient.getName();
        String initials = getInitials(name);
        greetingLabel.setText("Good day, " + name.split(" ")[0]);
        subLabel.setText("Here is where your care stands today.");
        avatarLabel.setText(initials);
        userNameLabel.setText(name);

        // update status pill
        String status = patient.getStatus();
        if (status != null) {
            statusLabel.setText(capitalize(status));
            if (status.equals("in_consult")) {
                statusLabel.getStyleClass().setAll("status-busy");
            } else if (status.equals("prescribed") || status.equals("discharged")) {
                statusLabel.getStyleClass().setAll("status-available");
            }
        }

        // update consultation card
        if (patient.getIllnessClass() != null && !patient.getIllnessClass().isEmpty()) {
            String specialty = IllnessClassifier.getSpecialty(patient.getIllnessClass());
            consultTitle.setText(specialty + " consult");
            consultDesc.setText("Your symptoms were classified as " + patient.getIllnessClass()
                    + " and routed to a " + specialty + ".");
        }

        // update doctor card
        if (patient.getAssignedDoctorId() > 0) {
            DoctorDAO doctorDAO = new DoctorDAO();
            Doctor doctor = doctorDAO.getById(patient.getAssignedDoctorId());
            if (doctor != null) {
                doctorAvatar.setText(getInitials(doctor.getName()));
                doctorNameLabel.setText("Dr. " + doctor.getName());
                doctorSpecLabel.setText(doctor.getSpecialty());
                if (doctor.getStatus().equals("available")) {
                    doctorStatusLabel.setText("● Available now");
                    doctorStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #127566; -fx-font-weight: bold;");
                } else {
                    doctorStatusLabel.setText("● " + capitalize(doctor.getStatus()));
                    doctorStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #E65100; -fx-font-weight: bold;");
                }
            }
        }
    }

    @FXML
    private void goToSymptom() {
        loadScreen("symptom-view.fxml");
    }

    @FXML
    private void onLogout() {
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private void loadScreen(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) greetingLabel.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }

    private String getInitials(String name) {
        String[] parts = name.split(" ");
        if (parts.length >= 2) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return ("" + parts[0].charAt(0)).toUpperCase();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("_", " ");
    }
}
