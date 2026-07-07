package com.example.ehospital;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class SymptomController {

    @FXML private TextArea symptomArea;
    @FXML private HBox classChip;
    @FXML private Label classLabel;
    @FXML private Label specialtyLabel;
    @FXML private Label classHint;
    @FXML private Label queueHint;
    @FXML private Button submitBtn;
    @FXML private Label messageLabel;
    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;

    private String illnessClass;
    private String specialty;
    private java.util.Timer redirectTimer;

    @FXML
    public void initialize() {
        Patient patient = SessionManager.getPatient();
        if (patient != null) {
            avatarLabel.setText(getInitials(patient.getName()));
            userNameLabel.setText(patient.getName());
        }
    }

    @FXML
    private void onClassify() {
        String symptoms = symptomArea.getText();
        if (symptoms.isEmpty()) {
            messageLabel.setText("Please describe your symptoms first.");
            return;
        }

        illnessClass = IllnessClassifier.classify(symptoms);
        specialty = IllnessClassifier.getSpecialty(illnessClass);

        classLabel.setText(illnessClass);
        specialtyLabel.setText(specialty);
        classChip.setVisible(true);
        classChip.setManaged(true);
        classHint.setVisible(false);
        classHint.setManaged(false);
        submitBtn.setDisable(false);
        messageLabel.setText("");
        queueHint.setText("You'll be placed in " + specialty + " queue. Average wait \u00B7 under 10 min.");
        queueHint.setVisible(true);
        queueHint.setManaged(true);
    }

    @FXML
    private void onSubmit() {
        Patient patient = SessionManager.getPatient();
        String symptoms = symptomArea.getText();

        if (symptoms.isEmpty() || illnessClass == null) {
            messageLabel.setText("Please classify your symptoms first.");
            return;
        }

        PatientDAO patientDAO = new PatientDAO();
        patientDAO.saveSymptoms(patient.getId(), symptoms, illnessClass);

        // find a doctor with the right specialty — available preferred, busy accepted
        DoctorDAO doctorDAO = new DoctorDAO();
        Doctor doctor = doctorDAO.findBySpecialtyAny(specialty);

        if (doctor != null) {
            patientDAO.assignDoctor(patient.getId(), doctor.getId());
            messageLabel.getStyleClass().setAll("success-label");
            messageLabel.setText("Matched with Dr. " + doctor.getName() + " (" + doctor.getSpecialty() + "). Waiting for doctor to accept.");
        } else {
            // no doctor of that specialty — try general practitioner
            doctor = doctorDAO.findBySpecialtyAny("General Practitioner");
            if (doctor != null) {
                patientDAO.assignDoctor(patient.getId(), doctor.getId());
                messageLabel.getStyleClass().setAll("success-label");
                messageLabel.setText("No " + specialty + " available. Matched with Dr. " + doctor.getName() + " (General Practitioner). Waiting for acceptance.");
            } else {
                messageLabel.getStyleClass().setAll("error-label");
                messageLabel.setText("No doctors available right now. Your symptoms are saved. Please check back later.");
                return;
            }
        }

        // refresh session
        patient = patientDAO.getById(patient.getId());
        SessionManager.loginAsPatient(patient);

        submitBtn.setDisable(true);

        // go to dashboard after 2 seconds so patient sees the message
        redirectTimer = new java.util.Timer(true);
        redirectTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> loadScreen("patient-dashboard.fxml"));
            }
        }, 2000);
    }

    @FXML
    private void goToDashboard() {
        loadScreen("patient-dashboard.fxml");
    }

    @FXML
    private void goToConsultation() {
        Patient patient = SessionManager.getPatient();
        if (patient != null && patient.getAssignedDoctorId() > 0) {
            loadScreen("patient-chat.fxml");
        } else {
            loadScreen("symptom-view.fxml");
        }
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
        if (redirectTimer != null) {
            redirectTimer.cancel();
            redirectTimer = null;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) symptomArea.getScene().getWindow();
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
}
