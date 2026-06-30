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
    @FXML private VBox transferStatusBox;
    @FXML private VBox transferStatusCard;
    @FXML private Label transferHospitalLabel;
    @FXML private Label transferStatusLabel;
    @FXML private VBox fileRequestBox;
    @FXML private Label fileRequestLabel;
    @FXML private Button approveFileBtn;

    private Doctor doctor;
    private Patient patient;
    private ChatDAO chatDAO = new ChatDAO();
    private Timer pollTimer;
    private int lastMessageCount = 0;
    private Transfer activeTransfer;

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

        if (patient != null) {
            loadMessages();
            loadTransferStatus();
            startPolling();
        }
    }

    private void loadTransferStatus() {
        if (patient == null) return;

        TransferDAO transferDAO = new TransferDAO();
        activeTransfer = transferDAO.getActiveByDoctorAndPatient(doctor.getId(), patient.getId());

        if (activeTransfer == null) {
            // also check for any recent transfer (including declined)
            activeTransfer = transferDAO.getByDoctorAndPatient(doctor.getId(), patient.getId());
        }

        if (activeTransfer != null) {
            transferStatusBox.setVisible(true);
            transferStatusBox.setManaged(true);

            HospitalDAO hospitalDAO = new HospitalDAO();
            Hospital destHospital = hospitalDAO.getById(activeTransfer.getToHospitalId());
            transferHospitalLabel.setText(destHospital != null ? destHospital.getName() : "Hospital");

            String status = activeTransfer.getStatus();
            if ("new".equals(status)) {
                transferStatusLabel.setText("Pending — awaiting hospital response");
                transferStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #7A5A12;");
                transferStatusCard.setStyle("-fx-background-color: #FFF8EC; -fx-border-color: #F0DFB8; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14;");
            } else if ("accepted".equals(status)) {
                transferStatusLabel.setText("Accepted — bed reserved");
                transferStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #1F6E4F;");
                transferStatusCard.setStyle("-fx-background-color: #EAF5EE; -fx-border-color: #BFE0CE; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14;");
            } else if ("declined".equals(status)) {
                String reason = activeTransfer.getDeclineReason() != null ? " — " + activeTransfer.getDeclineReason() : "";
                transferStatusLabel.setText("Declined" + reason);
                transferStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #9A4A36;");
                transferStatusCard.setStyle("-fx-background-color: #FBEEE9; -fx-border-color: #F0D9D0; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14;");
            }

            // file request from hospital
            if (activeTransfer.isFileRequested() && !activeTransfer.isFileApproved() && !activeTransfer.isFileSent()) {
                fileRequestBox.setVisible(true);
                fileRequestBox.setManaged(true);
                fileRequestLabel.setText("The receiving hospital has requested this patient's medical file. Approve to share.");
            } else {
                fileRequestBox.setVisible(false);
                fileRequestBox.setManaged(false);
            }
        } else {
            transferStatusBox.setVisible(false);
            transferStatusBox.setManaged(false);
            fileRequestBox.setVisible(false);
            fileRequestBox.setManaged(false);
        }
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
    private void onApproveFile() {
        if (activeTransfer == null) return;
        TransferDAO transferDAO = new TransferDAO();
        transferDAO.approveFile(activeTransfer.getId());

        chatDAO.sendMessage(doctor.getId(), patient.getId(), "doctor",
                "Patient file has been shared with the receiving hospital.");

        lastMessageCount = 0;
        loadMessages();
        loadTransferStatus();
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
                Platform.runLater(() -> {
                    loadMessages();
                    loadTransferStatus();
                });
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
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split(" ");
        if (parts.length >= 2) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return ("" + parts[0].charAt(0)).toUpperCase();
    }
}
