package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class DoctorTransferController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label docSpecLabel;
    @FXML private Label patientAvatar;
    @FXML private Label patientNameLabel;
    @FXML private Label patientInfoLabel;
    @FXML private ComboBox<String> hospitalCombo;
    @FXML private Button btnRoutine;
    @FXML private Button btnUrgent;
    @FXML private Button btnEmergency;
    @FXML private TextArea reasonField;
    @FXML private VBox fileSendCard;
    @FXML private VBox fileRequestCard;
    @FXML private Label messageLabel;

    private Doctor doctor;
    private Patient patient;
    private List<Hospital> hospitals;
    private String urgency = "routine";
    private boolean fileSend = true;
    private java.util.Timer redirectTimer;

    @FXML
    public void initialize() {
        doctor = SessionManager.getDoctor();
        if (doctor == null) return;

        avatarLabel.setText(getInitials(doctor.getName()));
        userNameLabel.setText("Dr. " + doctor.getName());
        docSpecLabel.setText(doctor.getSpecialty());

        // load current patient
        PatientDAO patientDAO = new PatientDAO();
        patient = patientDAO.getInConsultForDoctor(doctor.getId());

        if (patient != null) {
            patientAvatar.setText(getInitials(patient.getName()));
            patientNameLabel.setText(patient.getName());
            patientInfoLabel.setText((patient.getIllnessClass() != null ? patient.getIllnessClass() : "") + " \u00B7 " + patient.getEmail());
        }

        // load hospitals
        HospitalDAO hospitalDAO = new HospitalDAO();
        hospitals = hospitalDAO.getAll();
        for (Hospital h : hospitals) {
            hospitalCombo.getItems().add(h.getName() + " \u2014 " + h.getLocation());
        }
        if (!hospitals.isEmpty()) {
            hospitalCombo.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void setRoutine() {
        urgency = "routine";
        updateUrgencyButtons();
    }

    @FXML
    private void setUrgent() {
        urgency = "urgent";
        updateUrgencyButtons();
    }

    @FXML
    private void setEmergency() {
        urgency = "emergency";
        updateUrgencyButtons();
    }

    private void updateUrgencyButtons() {
        String active = "-fx-background-color: #1F4D8F; -fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;";
        String inactive = "-fx-background-color: #EDEAE2; -fx-text-fill: #4A4F57; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;";
        btnRoutine.setStyle(urgency.equals("routine") ? active : inactive);
        btnUrgent.setStyle(urgency.equals("urgent") ? active : inactive);
        btnEmergency.setStyle(urgency.equals("emergency") ? active : inactive);
    }

    @FXML
    private void selectFileSend() {
        fileSend = true;
        fileSendCard.setStyle("-fx-background-color: white; -fx-border-color: #1F4D8F; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14; -fx-cursor: hand;");
        fileRequestCard.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14; -fx-cursor: hand;");
    }

    @FXML
    private void selectFileRequest() {
        fileSend = false;
        fileSendCard.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-width: 1.5; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14; -fx-cursor: hand;");
        fileRequestCard.setStyle("-fx-background-color: white; -fx-border-color: #1F4D8F; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14; -fx-cursor: hand;");
    }

    @FXML
    private void onSubmit() {
        if (patient == null) {
            messageLabel.setText("No patient in consultation.");
            return;
        }

        int selectedIndex = hospitalCombo.getSelectionModel().getSelectedIndex();
        if (selectedIndex < 0) {
            messageLabel.setText("Please select a destination hospital.");
            return;
        }

        String reason = reasonField.getText().trim();
        if (reason.isEmpty()) {
            messageLabel.setText("Please provide a reason for transfer.");
            return;
        }

        Hospital destHospital = hospitals.get(selectedIndex);

        TransferDAO transferDAO = new TransferDAO();
        boolean ok = transferDAO.create(patient.getId(), doctor.getId(), destHospital.getId(), urgency, reason, fileSend);

        if (ok) {
            // send chat message about transfer
            ChatDAO chatDAO = new ChatDAO();
            chatDAO.sendMessage(doctor.getId(), patient.getId(), "doctor",
                    "Transfer request sent to " + destHospital.getName() + " (" + urgency + ").");

            messageLabel.getStyleClass().setAll("success-label");
            messageLabel.setText("Transfer request sent to " + destHospital.getName() + "!");

            // go back to chat after a short delay
            redirectTimer = new java.util.Timer(true);
            redirectTimer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    javafx.application.Platform.runLater(() -> loadScreen("doctor-chat.fxml"));
                }
            }, 1500);
        } else {
            messageLabel.setText("Failed to send transfer. Try again.");
        }
    }

    @FXML
    private void goToDashboard() { loadScreen("doctor-dashboard.fxml"); }

    @FXML
    private void goToConsultation() { loadScreen("doctor-chat.fxml"); }

    @FXML
    private void goToAdmission() { loadScreen("doctor-admissions.fxml"); }

    @FXML
    private void goToPrescription() { loadScreen("doctor-prescriptions.fxml"); }

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
            Stage stage = (Stage) avatarLabel.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 900, 600));
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
