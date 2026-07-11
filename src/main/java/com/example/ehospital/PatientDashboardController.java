package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    @FXML private VBox rxCard;
    @FXML private Label rxCardText;
    @FXML private Label rxViewLink;
    @FXML private Label admitCardText;
    @FXML private Label doctorLicenseLabel;
    @FXML private Button consultButton;

    @FXML
    public void initialize() {
        Patient patient = SessionManager.getPatient();
        if (patient == null) return;

        // refresh from database
        PatientDAO dao = new PatientDAO();
        patient = dao.getById(patient.getId());
        SessionManager.loginAsPatient(patient);

        String name = patient.getName() != null ? patient.getName() : "User";
        String initials = getInitials(name);
        String firstName = name.contains(" ") ? name.split(" ")[0] : name;
        greetingLabel.setText("Good day, " + firstName);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d"));
        subLabel.setText("Here is where your care stands today, " + today);
        avatarLabel.setText(initials);
        userNameLabel.setText(name);

        // update status pill
        String status = patient.getStatus();
        if (status != null) {
            statusLabel.setText(capitalize(status));
            if ("in_consult".equals(status)) {
                statusLabel.getStyleClass().setAll("status-busy");
            } else if ("prescribed".equals(status) || "discharged".equals(status)) {
                statusLabel.getStyleClass().setAll("status-available");
            } else if ("admitted".equals(status)) {
                statusLabel.getStyleClass().setAll("status-busy");
            } else if ("pending".equals(status)) {
                statusLabel.getStyleClass().setAll("status-pending");
            }
        }

        // update consultation card
        if (patient.getIllnessClass() != null && !patient.getIllnessClass().isEmpty()) {
            String specialty = IllnessClassifier.getSpecialty(patient.getIllnessClass());
            consultTitle.setText(specialty + " consult");
            consultDesc.setText("Your symptoms were classified as " + patient.getIllnessClass()
                    + " and routed to a " + specialty + ".");
            if (patient.getAssignedDoctorId() > 0) {
                consultButton.setText("Open consultation \u2192");
                consultButton.setOnAction(e -> goToConsultation());
            }
        }

        // update doctor card
        if (patient.getAssignedDoctorId() > 0) {
            DoctorDAO doctorDAO = new DoctorDAO();
            Doctor doctor = doctorDAO.getById(patient.getAssignedDoctorId());
            if (doctor != null) {
                doctorAvatar.setText(getInitials(doctor.getName()));
                doctorNameLabel.setText("Dr. " + doctor.getName());
                doctorSpecLabel.setText(doctor.getSpecialty());
                if ("available".equals(doctor.getStatus())) {
                    doctorStatusLabel.setText("\u25CF Available now");
                    doctorStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #127566; -fx-font-weight: bold;");
                } else {
                    doctorStatusLabel.setText("\u25CF " + capitalize(doctor.getStatus()));
                    doctorStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #E65100; -fx-font-weight: bold;");
                }
                if (doctor.getLicenseNumber() != null && !doctor.getLicenseNumber().isEmpty()) {
                    doctorLicenseLabel.setText("License " + doctor.getLicenseNumber());
                }
            }
        }

        // update prescription card
        PrescriptionDAO rxDAO = new PrescriptionDAO();
        Prescription rx = rxDAO.getByPatientId(patient.getId());
        if (rx != null) {
            rxCardText.setText(rx.getMedicine() + "\n" + rx.getDosage());
            rxCardText.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            rxViewLink.setText("View \u2192");
        }

        if (status != null && status.equals("admitted")) {
            admitCardText.setText("You have been admitted to the hospital for further care.");
            admitCardText.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #B14A33;");

            // Disable the consultation button
            if (consultButton != null) {
                consultButton.setText("Admitted - Consult Paused");
                consultButton.setDisable(true);
            }
        }
    }

    @FXML
    private void goToConsultation() {
        Patient patient = SessionManager.getPatient();
        if (patient != null) {
            PatientDAO dao = new PatientDAO();
            patient = dao.getById(patient.getId());
            SessionManager.loginAsPatient(patient);

            // Security check: Block navigation if admitted
            if ("admitted".equals(patient.getStatus())) {
                return;
            }

            if (patient.getAssignedDoctorId() > 0) {
                loadScreen("patient-chat.fxml");
                return;
            }
        }
        loadScreen("symptom-view.fxml");
    }

    @FXML
    private void goToSymptom() {
        Patient patient = SessionManager.getPatient();

        // Security check: Block navigation if admitted
        if (patient != null && "admitted".equals(patient.getStatus())) {
            return;
        }

        loadScreen("symptom-view.fxml");
    }

    @FXML
    private void goToPrescription() {
        loadScreen("patient-prescriptions.fxml");
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
            stage.setScene(new Scene(loader.load(), 1500, 900));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split(" ");
        if (parts.length >= 2) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return ("" + parts[0].charAt(0)).toUpperCase();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("_", " ");
    }
}
