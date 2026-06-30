package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class AdminRecordsController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label topSubLabel;
    @FXML private Label listTitle;
    @FXML private VBox patientList;
    @FXML private VBox detailPanel;
    @FXML private Label detailAvatar;
    @FXML private Label detailName;
    @FXML private Label detailEmail;
    @FXML private Label detailStatusPill;
    @FXML private Label detailClass;
    @FXML private Label detailDoctor;
    @FXML private Label detailTransferStatus;
    @FXML private Label detailSymptoms;
    @FXML private Label detailClinicalNote;
    @FXML private VBox detailRxList;
    @FXML private VBox detailChatList;

    private Hospital hospital;

    @FXML
    public void initialize() {
        HospitalAdmin admin = SessionManager.getAdmin();
        if (admin == null) return;

        avatarLabel.setText(getInitials(admin.getName()));
        userNameLabel.setText(admin.getName());

        HospitalDAO hospitalDAO = new HospitalDAO();
        hospital = hospitalDAO.getByAdminId(admin.getId());

        loadPatients();
    }

    private void loadPatients() {
        if (hospital == null) {
            topSubLabel.setText("No hospital linked");
            return;
        }

        TransferDAO transferDAO = new TransferDAO();
        List<Transfer> transfers = transferDAO.getByHospitalId(hospital.getId());

        patientList.getChildren().clear();

        if (transfers.isEmpty()) {
            listTitle.setText("PATIENTS \u00B7 0");
            Label empty = new Label("No patient records yet. Patients transferred to your hospital will appear here.");
            empty.setStyle("-fx-font-size: 14; -fx-text-fill: #9A9EA5;");
            empty.setWrapText(true);
            patientList.getChildren().add(empty);
            return;
        }

        PatientDAO patientDAO = new PatientDAO();
        DoctorDAO doctorDAO = new DoctorDAO();

        // count unique patients
        int count = 0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        for (Transfer t : transfers) {
            if (!seen.add(t.getPatientId())) continue;
            count++;

            Patient pat = patientDAO.getById(t.getPatientId());
            Doctor doc = doctorDAO.getById(t.getDoctorId());

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14; -fx-cursor: hand;");

            Label avatar = new Label(pat != null ? getInitials(pat.getName()) : "?");
            avatar.setStyle("-fx-background-color: #EEF4FC; -fx-text-fill: #1F4D8F; -fx-pref-width: 42; -fx-pref-height: 42; -fx-background-radius: 12; -fx-alignment: center; -fx-font-weight: bold;");

            VBox info = new VBox(2);
            info.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);

            Label name = new Label(pat != null ? pat.getName() : "Unknown");
            name.setStyle("-fx-font-size: 15; -fx-font-weight: bold;");

            String subText = (pat != null && pat.getIllnessClass() != null ? pat.getIllnessClass() : "")
                    + " \u00B7 Dr. " + (doc != null ? doc.getName() : "Unknown");
            Label sub = new Label(subText);
            sub.setStyle("-fx-font-size: 12; -fx-text-fill: #8A8F94;");

            // status pill
            Label statusPill = new Label(capitalize(pat != null ? pat.getStatus() : ""));
            if (pat != null && "admitted".equals(pat.getStatus())) {
                statusPill.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: #B14A33; -fx-background-color: #FBEEE9; -fx-background-radius: 5; -fx-padding: 2 6;");
            } else {
                statusPill.getStyleClass().add("status-available");
            }

            info.getChildren().addAll(name, sub);
            row.getChildren().addAll(avatar, info, statusPill);

            row.setOnMouseClicked(e -> selectPatient(t));
            patientList.getChildren().add(row);
        }

        listTitle.setText("PATIENTS \u00B7 " + count);
    }

    private void selectPatient(Transfer transfer) {
        PatientDAO patientDAO = new PatientDAO();
        DoctorDAO doctorDAO = new DoctorDAO();
        Patient pat = patientDAO.getById(transfer.getPatientId());
        Doctor doc = doctorDAO.getById(transfer.getDoctorId());

        detailPanel.setVisible(true);
        detailPanel.setManaged(true);

        detailAvatar.setText(pat != null ? getInitials(pat.getName()) : "?");
        detailName.setText(pat != null ? pat.getName() : "Unknown");
        detailEmail.setText(pat != null ? pat.getEmail() : "");

        // status pill
        String status = pat != null ? pat.getStatus() : "";
        detailStatusPill.setText(capitalize(status));
        if ("admitted".equals(status)) {
            detailStatusPill.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #B14A33; -fx-background-radius: 6; -fx-padding: 3 8;");
        } else if ("prescribed".equals(status)) {
            detailStatusPill.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #1F6E4F; -fx-background-color: #EAF5EE; -fx-background-radius: 6; -fx-padding: 3 8;");
        } else {
            detailStatusPill.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #6B6F76; -fx-background-color: #F0EEEA; -fx-background-radius: 6; -fx-padding: 3 8;");
        }

        // classification
        detailClass.setText(pat != null && pat.getIllnessClass() != null ? pat.getIllnessClass() : "\u2014");

        // doctor
        detailDoctor.setText(doc != null ? "Dr. " + doc.getName() : "Unknown");

        // transfer status
        String tStatus = transfer.getStatus();
        detailTransferStatus.setText(capitalize(tStatus));
        if ("accepted".equals(tStatus)) {
            detailTransferStatus.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #1F6E4F;");
        } else if ("declined".equals(tStatus)) {
            detailTransferStatus.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #B14A33;");
        } else {
            detailTransferStatus.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #7A5A12;");
        }

        // symptoms
        detailSymptoms.setText(pat != null && pat.getSymptoms() != null ? pat.getSymptoms() : "No symptoms recorded.");

        // clinical note from transfer
        detailClinicalNote.setText(transfer.getClinicalNote() != null ? transfer.getClinicalNote() : "No clinical note.");

        // prescriptions
        detailRxList.getChildren().clear();
        if (pat != null) {
            PrescriptionDAO rxDAO = new PrescriptionDAO();
            List<Prescription> prescriptions = rxDAO.getAllByPatientId(pat.getId());

            if (prescriptions.isEmpty()) {
                Label noRx = new Label("No prescriptions on file.");
                noRx.setStyle("-fx-font-size: 13; -fx-text-fill: #9A9EA5;");
                detailRxList.getChildren().add(noRx);
            } else {
                for (Prescription rx : prescriptions) {
                    VBox rxCard = new VBox(3);
                    rxCard.setStyle("-fx-background-color: #F3F7FD; -fx-border-color: #D7E3F4; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 12;");

                    Label med = new Label("\u211E " + rx.getMedicine());
                    med.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
                    Label dose = new Label(rx.getDosage());
                    dose.setStyle("-fx-font-size: 13; -fx-text-fill: #1F4D8F; -fx-font-weight: bold;");
                    rxCard.getChildren().addAll(med, dose);

                    if (rx.getNotes() != null && !rx.getNotes().isEmpty()) {
                        Label notes = new Label(rx.getNotes());
                        notes.setStyle("-fx-font-size: 12; -fx-text-fill: #6B6F76;");
                        notes.setWrapText(true);
                        rxCard.getChildren().add(notes);
                    }

                    detailRxList.getChildren().add(rxCard);
                }
            }
        }

        // consultation transcript
        detailChatList.getChildren().clear();
        if (pat != null && doc != null) {
            ChatDAO chatDAO = new ChatDAO();
            List<Message> messages = chatDAO.getMessages(doc.getId(), pat.getId());

            if (messages.isEmpty()) {
                Label noChat = new Label("No consultation messages.");
                noChat.setStyle("-fx-font-size: 13; -fx-text-fill: #9A9EA5;");
                detailChatList.getChildren().add(noChat);
            } else {
                for (Message msg : messages) {
                    String sender = msg.getSenderRole().equals("doctor") ? "Dr. " + doc.getName() : pat.getName();
                    Label line = new Label(sender + ": " + msg.getContent());
                    line.setWrapText(true);
                    if (msg.getSenderRole().equals("doctor")) {
                        line.setStyle("-fx-font-size: 12; -fx-text-fill: #1F4D8F;");
                    } else {
                        line.setStyle("-fx-font-size: 12; -fx-text-fill: #3A3F47;");
                    }
                    detailChatList.getChildren().add(line);
                }
            }
        }
    }

    @FXML
    private void goToTransfers() { loadScreen("admin-dashboard.fxml"); }

    @FXML
    private void onRefresh() { initialize(); }

    @FXML
    private void onLogout() {
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private void loadScreen(String fxml) {
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

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace("_", " ");
    }
}
