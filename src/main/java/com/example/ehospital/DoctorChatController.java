package com.example.ehospital;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DoctorChatController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label docSpecLabel;
    @FXML private Label patientChatAvatar;
    @FXML private Label patientChatName;
    @FXML private Label patientChatInfo;
    @FXML private Label patientStatusPill;
    @FXML private ScrollPane chatScroll;
    @FXML private VBox chatBox;
    @FXML private TextField messageField;
    @FXML private Label symptomText;
    @FXML private Label classChipLabel;
    @FXML private Button prescribeBtn;
    @FXML private VBox rxForm;
    @FXML private TextField medicineField;
    @FXML private TextField dosageField;
    @FXML private TextArea notesField;
    @FXML private Label rxMessage;
    @FXML private VBox afterConsult;

    private Doctor doctor;
    private Patient patient;
    private ChatDAO chatDAO = new ChatDAO();
    private Timer pollTimer;
    private int lastMessageCount = 0;

    @FXML
    public void initialize() {
        doctor = SessionManager.getDoctor();
        if (doctor == null) return;

        // refresh doctor from db
        DoctorDAO doctorDAO = new DoctorDAO();
        doctor = doctorDAO.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);

        // sidebar
        avatarLabel.setText(getInitials(doctor.getName()));
        userNameLabel.setText("Dr. " + doctor.getName());
        docSpecLabel.setText(doctor.getSpecialty());

        // load current in-consult patient
        PatientDAO patientDAO = new PatientDAO();
        patient = patientDAO.getInConsultForDoctor(doctor.getId());

        if (patient != null) {
            patientChatAvatar.setText(getInitials(patient.getName()));
            patientChatName.setText(patient.getName());
            patientChatInfo.setText(patient.getIllnessClass() != null ?
                    patient.getIllnessClass() + " — " + patient.getEmail() : patient.getEmail());

            // symptom summary
            symptomText.setText(patient.getSymptoms() != null ? patient.getSymptoms() : "No symptoms recorded");
            classChipLabel.setText(patient.getIllnessClass() != null ? patient.getIllnessClass() : "—");
        } else {
            patientChatName.setText("No patient");
            patientChatInfo.setText("No patient currently in consultation");
            patientStatusPill.setText("Idle");
            patientStatusPill.getStyleClass().setAll("status-available");
            messageField.setDisable(true);
        }

        loadMessages();
        startPolling();
    }

    private void loadMessages() {
        if (patient == null) return;

        List<Message> messages = chatDAO.getMessages(doctor.getId(), patient.getId());

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

            if (msg.getSenderRole().equals("doctor")) {
                // doctor message — right side, blue
                bubble.setStyle("-fx-background-color: #1F4D8F; -fx-text-fill: white; -fx-background-radius: 16 16 4 16; -fx-font-size: 14;");
                row.setAlignment(Pos.CENTER_RIGHT);
            } else {
                // patient message — left side, white
                bubble.setStyle("-fx-background-color: white; -fx-text-fill: #1A1C20; -fx-background-radius: 16 16 16 4; -fx-font-size: 14; -fx-border-color: #E8E4DC; -fx-border-radius: 16 16 16 4;");
                row.setAlignment(Pos.CENTER_LEFT);
            }

            row.getChildren().add(bubble);
            chatBox.getChildren().add(row);
        }

        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    @FXML
    private void onSend() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || patient == null) return;

        chatDAO.sendMessage(doctor.getId(), patient.getId(), "doctor", text);
        messageField.clear();
        lastMessageCount = 0;
        loadMessages();
    }

    @FXML
    private void onPrescribe() {
        // toggle prescription form
        boolean showing = rxForm.isVisible();
        rxForm.setVisible(!showing);
        rxForm.setManaged(!showing);
        prescribeBtn.setText(showing ? "Prescribe drugs" : "Cancel prescription");
    }

    @FXML
    private void onIssuePrescription() {
        if (patient == null) return;

        String medicine = medicineField.getText().trim();
        String dosage = dosageField.getText().trim();
        String notes = notesField.getText().trim();

        if (medicine.isEmpty() || dosage.isEmpty()) {
            rxMessage.setText("Medicine and dosage are required.");
            rxMessage.setStyle("-fx-text-fill: #B14A33; -fx-font-size: 13;");
            return;
        }

        PrescriptionDAO rxDAO = new PrescriptionDAO();
        boolean saved = rxDAO.save(patient.getId(), doctor.getId(), medicine, dosage, notes);

        if (saved) {
            // update patient status to prescribed
            PatientDAO patientDAO = new PatientDAO();
            patientDAO.updateStatus(patient.getId(), "prescribed");

            // send a system message in chat
            chatDAO.sendMessage(doctor.getId(), patient.getId(), "doctor",
                    "Prescription issued: " + medicine + " — " + dosage);

            rxMessage.setText("Prescription issued successfully!");
            rxMessage.setStyle("-fx-text-fill: #127566; -fx-font-size: 13;");

            // hide form, show after-consult options
            rxForm.setVisible(false);
            rxForm.setManaged(false);
            prescribeBtn.setDisable(true);
            prescribeBtn.setText("Prescribed");

            patientStatusPill.setText("Prescribed");
            patientStatusPill.getStyleClass().setAll("status-available");

            afterConsult.setVisible(true);
            afterConsult.setManaged(true);

            lastMessageCount = 0;
            loadMessages();
        } else {
            rxMessage.setText("Failed to save prescription. Try again.");
            rxMessage.setStyle("-fx-text-fill: #B14A33; -fx-font-size: 13;");
        }
    }

    @FXML
    private void onAdmit() {
        if (patient == null) return;

        PatientDAO patientDAO = new PatientDAO();
        patientDAO.updateStatus(patient.getId(), "admitted");

        chatDAO.sendMessage(doctor.getId(), patient.getId(), "doctor",
                "Patient has been admitted to hospital for further care.");

        patientStatusPill.setText("Admitted");
        patientStatusPill.getStyleClass().setAll("status-busy");

        prescribeBtn.setDisable(true);
        afterConsult.setVisible(true);
        afterConsult.setManaged(true);

        lastMessageCount = 0;
        loadMessages();
    }

    @FXML
    private void onNextPatient() {
        stopPolling();

        DoctorDAO doctorDAO = new DoctorDAO();
        doctorDAO.updateStatus(doctor.getId(), "available");
        doctor = doctorDAO.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);

        // check if there's a next pending patient
        PatientDAO patientDAO = new PatientDAO();
        List<Patient> pending = patientDAO.getPendingForDoctor(doctor.getId());

        if (!pending.isEmpty()) {
            // accept first pending patient
            Patient next = pending.get(0);
            patientDAO.updateStatus(next.getId(), "in_consult");
            doctorDAO.updateStatus(doctor.getId(), "busy");
            doctor = doctorDAO.getById(doctor.getId());
            SessionManager.loginAsDoctor(doctor);

            // reload this chat screen with new patient
            loadScreen("doctor-chat.fxml");
        } else {
            // no pending patients, go to dashboard
            loadScreen("doctor-dashboard.fxml");
        }
    }

    @FXML
    private void onHold() {
        stopPolling();

        DoctorDAO doctorDAO = new DoctorDAO();
        doctorDAO.updateStatus(doctor.getId(), "on_hold");
        doctor = doctorDAO.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);

        loadScreen("doctor-dashboard.fxml");
    }

    @FXML
    private void onTransfer() {
        stopPolling();
        loadScreen("doctor-transfer.fxml");
    }

    @FXML
    private void goToDashboard() {
        stopPolling();
        loadScreen("doctor-dashboard.fxml");
    }

    @FXML
    private void goToAdmission() {
        stopPolling();
        loadScreen("doctor-admissions.fxml");
    }

    @FXML
    private void goToPrescription() {
        stopPolling();
        loadScreen("doctor-prescriptions.fxml");
    }

    @FXML
    private void onLogout() {
        stopPolling();
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private void startPolling() {
        pollTimer = new Timer(true);
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> loadMessages());
            }
        }, 2000, 2000);
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
            stage.setScene(new Scene(loader.load(), 900, 600));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }

    private String getInitials(String name) {
        String[] parts = name.split(" ");
        if (parts.length >= 2) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return ("" + parts[0].charAt(0)).toUpperCase();
    }
}
