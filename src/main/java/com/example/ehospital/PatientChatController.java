package com.example.ehospital;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PatientChatController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label doctorChatAvatar;
    @FXML private Label doctorChatName;
    @FXML private Label doctorChatSpec;
    @FXML private ScrollPane chatScroll;
    @FXML private VBox chatBox;
    @FXML private TextField messageField;
    @FXML private VBox rxPanel;
    @FXML private Label detailsName;
    @FXML private Label detailsInfo;

    private Patient patient;
    private Doctor doctor;
    private ChatDAO chatDAO = new ChatDAO();
    private Timer pollTimer;
    private int lastMessageCount = 0;

    @FXML
    public void initialize() {
        patient = SessionManager.getPatient();
        if (patient == null) return;

        PatientDAO patientDAO = new PatientDAO();
        patient = patientDAO.getById(patient.getId());
        SessionManager.loginAsPatient(patient);

        avatarLabel.setText(getInitials(patient.getName()));
        userNameLabel.setText(patient.getName());

        detailsName.setText(patient.getName());
        detailsInfo.setText(patient.getEmail());

        if (patient.getAssignedDoctorId() > 0) {
            DoctorDAO doctorDAO = new DoctorDAO();
            doctor = doctorDAO.getById(patient.getAssignedDoctorId());
            if (doctor != null) {
                doctorChatAvatar.setText(getInitials(doctor.getName()));
                doctorChatName.setText("Dr. " + doctor.getName());
                doctorChatSpec.setText("\u25CF " + doctor.getSpecialty() + ", Online");
            }
        }

        if (doctor != null) {
            loadMessages();
            checkPrescription();
            startPolling();
        }
    }

    private void loadMessages() {
        if (doctor == null) return;

        List<Message> messages = chatDAO.getMessages(patient.getId(), doctor.getId());

        if (messages.size() == lastMessageCount) return;
        lastMessageCount = messages.size();

        chatBox.getChildren().clear();

        for (Message msg : messages) {
            HBox row = new HBox();
            row.setPadding(new Insets(2, 0, 2, 0));

            Label bubble = new Label(msg.getContent());
            bubble.setWrapText(true);
            bubble.setMaxWidth(350);
            bubble.setPadding(new Insets(10, 14, 10, 14));

            if (msg.getSenderRole().equals("patient")) {
                bubble.setStyle("-fx-background-color: #1F4D8F; -fx-text-fill: white; -fx-background-radius: 16 16 4 16; -fx-font-size: 14;");
                row.setAlignment(Pos.CENTER_RIGHT);
            } else {
                bubble.setStyle("-fx-background-color: white; -fx-text-fill: #1A1C20; -fx-background-radius: 16 16 16 4; -fx-font-size: 14; -fx-border-color: #E8E4DC; -fx-border-radius: 16 16 16 4;");
                row.setAlignment(Pos.CENTER_LEFT);
            }

            row.getChildren().add(bubble);
            chatBox.getChildren().add(row);
        }

        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    private void checkPrescription() {
        if (patient == null) return;

        PrescriptionDAO rxDAO = new PrescriptionDAO();
        Prescription rx = rxDAO.getByPatientId(patient.getId());

        rxPanel.getChildren().clear();

        if (rx != null) {
            VBox rxCard = new VBox();
            rxCard.setStyle("-fx-background-color: white; -fx-border-color: #D7E3F4; -fx-border-radius: 15; -fx-background-radius: 15;");

            HBox rxHeader = new HBox(9);
            rxHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            rxHeader.setStyle("-fx-background-color: #1F4D8F; -fx-padding: 14 16; -fx-background-radius: 15 15 0 0;");
            Label rxSymbol = new Label("\u211E");
            rxSymbol.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: white;");
            Label rxTitle = new Label("Active prescription");
            rxTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: white;");
            rxHeader.getChildren().addAll(rxSymbol, rxTitle);

            VBox rxBody = new VBox(3);
            rxBody.setStyle("-fx-padding: 16;");

            Label med = new Label(rx.getMedicine());
            med.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            med.setWrapText(true);

            Label dose = new Label(rx.getDosage());
            dose.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1F4D8F;");

            String notesStr = rx.getNotes() != null && !rx.getNotes().isEmpty() ? rx.getNotes() : "";
            VBox notesBox = new VBox();
            if (!notesStr.isEmpty()) {
                Label notes = new Label(notesStr);
                notes.setStyle("-fx-font-size: 13; -fx-text-fill: #6B6F76; -fx-padding: 12 0 0 0;");
                notes.setWrapText(true);
                notesBox.getChildren().add(notes);
            }

            String docName = doctor != null ? "Dr. " + doctor.getName() : "";
            String dateStr = rx.getCreatedAt() != null ? rx.getCreatedAt().toString().substring(0, 16) : "";
            Label footer = new Label("Issued by " + docName + ", " + dateStr);
            footer.setStyle("-fx-font-size: 12; -fx-text-fill: #8A8F94; -fx-padding: 14 0 0 0; -fx-border-color: #EDEAE2; -fx-border-width: 1 0 0 0;");

            rxBody.getChildren().addAll(med, dose);
            if (!notesStr.isEmpty()) rxBody.getChildren().add(notesBox);
            rxBody.getChildren().add(footer);

            rxCard.getChildren().addAll(rxHeader, rxBody);
            rxPanel.getChildren().add(rxCard);
        } else {
            VBox empty = new VBox(8);
            empty.setAlignment(javafx.geometry.Pos.CENTER);
            empty.setStyle("-fx-background-color: white; -fx-border-color: #D9D4CA; -fx-border-style: dashed; -fx-border-radius: 15; -fx-background-radius: 15; -fx-padding: 24 18;");

            Label rxIcon = new Label("\u211E");
            rxIcon.setStyle("-fx-font-size: 30; -fx-text-fill: #C9C4BA;");

            Label desc = new Label("No prescription yet. It will appear here the moment your doctor issues one.");
            desc.setStyle("-fx-font-size: 13; -fx-text-fill: #9A9EA5;");
            desc.setWrapText(true);

            empty.getChildren().addAll(rxIcon, desc);
            rxPanel.getChildren().add(empty);
        }
    }

    @FXML
    private void onSend() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || doctor == null) return;

        chatDAO.sendMessage(patient.getId(), doctor.getId(), "patient", text);
        messageField.clear();
        lastMessageCount = 0;
        loadMessages();
    }

    private void startPolling() {
        pollTimer = new Timer(true);
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    loadMessages();
                    checkPrescription();
                });
            }
        }, 2000, 2000);
    }

    @FXML
    private void goToDashboard() {
        stopPolling();
        loadScreen("patient-dashboard.fxml");
    }

    @FXML
    private void goToSymptom() {
        stopPolling();
        loadScreen("symptom-view.fxml");
    }

    @FXML
    private void goToPrescription() {
        stopPolling();
        loadScreen("patient-prescriptions.fxml");
    }

    @FXML
    private void onLogout() {
        stopPolling();
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private void stopPolling() {
        if (pollTimer != null) {
            pollTimer.cancel();
            pollTimer = null;
        }
    }

    private void loadScreen(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) chatBox.getScene().getWindow();
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
