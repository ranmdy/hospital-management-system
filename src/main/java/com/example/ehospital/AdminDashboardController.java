package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

public class AdminDashboardController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label hospitalInfoLabel;
    @FXML private Label totalBedsLabel;
    @FXML private Label occupiedLabel;
    @FXML private Label freeBedsLabel;
    @FXML private Label inboxTitle;
    @FXML private VBox inboxList;
    @FXML private VBox detailPanel;
    @FXML private Label detailAvatar;
    @FXML private Label detailPatientName;
    @FXML private Label detailPatientInfo;
    @FXML private Label detailUrgencyPill;
    @FXML private Label detailStatusPill;
    @FXML private Label detailDoctorName;
    @FXML private Label detailDate;
    @FXML private Label detailReason;
    @FXML private Label fileStatusLabel;
    @FXML private Button fileActionBtn;
    @FXML private HBox actionButtons;
    @FXML private HBox resultBox;
    @FXML private Label resultLabel;
    @FXML private Button arrivedBtn;

    private Hospital hospital;
    private Transfer selectedTransfer;

    @FXML
    public void initialize() {
        HospitalAdmin admin = SessionManager.getAdmin();
        if (admin == null) return;

        avatarLabel.setText(getInitials(admin.getName()));
        userNameLabel.setText(admin.getName());

        // load hospital
        HospitalDAO hospitalDAO = new HospitalDAO();
        hospital = hospitalDAO.getByAdminId(admin.getId());

        if (hospital != null) {
            hospitalInfoLabel.setText(hospital.getName() + " \u00B7 " + hospital.getLocation());
            totalBedsLabel.setText(String.valueOf(hospital.getTotalBeds()));
            int occupied = hospital.getTotalBeds() - hospital.getAvailableBeds();
            occupiedLabel.setText(String.valueOf(occupied));
            freeBedsLabel.setText(String.valueOf(hospital.getAvailableBeds()));
        } else {
            hospitalInfoLabel.setText("No hospital linked");
            totalBedsLabel.setText("0");
            occupiedLabel.setText("0");
            freeBedsLabel.setText("0");
        }

        loadTransfers();
    }

    private void loadTransfers() {
        if (hospital == null) return;

        TransferDAO transferDAO = new TransferDAO();
        List<Transfer> transfers = transferDAO.getByHospitalId(hospital.getId());

        inboxTitle.setText("INCOMING REQUESTS \u00B7 " + transfers.size());
        inboxList.getChildren().clear();

        if (transfers.isEmpty()) {
            Label empty = new Label("No transfer requests yet. Transfers from doctors will appear here.");
            empty.setStyle("-fx-font-size: 14; -fx-text-fill: #9A9EA5;");
            empty.setWrapText(true);
            inboxList.getChildren().add(empty);
            return;
        }

        PatientDAO patientDAO = new PatientDAO();
        DoctorDAO doctorDAO = new DoctorDAO();

        for (Transfer t : transfers) {
            Patient pat = patientDAO.getById(t.getPatientId());
            Doctor doc = doctorDAO.getById(t.getDoctorId());

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14; -fx-cursor: hand;");

            Label avatar = new Label(pat != null ? getInitials(pat.getName()) : "?");
            avatar.setStyle("-fx-background-color: #E9E7F7; -fx-text-fill: #4B3FA6; -fx-pref-width: 42; -fx-pref-height: 42; -fx-background-radius: 12; -fx-alignment: center; -fx-font-weight: bold;");

            VBox info = new VBox(2);
            info.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);

            HBox nameRow = new HBox(8);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(pat != null ? pat.getName() : "Unknown");
            name.setStyle("-fx-font-size: 15; -fx-font-weight: bold;");
            nameRow.getChildren().add(name);

            if ("new".equals(t.getStatus())) {
                Label newBadge = new Label("NEW");
                newBadge.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: #4B3FA6; -fx-background-color: #E9E7F7; -fx-background-radius: 5; -fx-padding: 2 6;");
                nameRow.getChildren().add(newBadge);
            }

            String subText = (pat != null && pat.getIllnessClass() != null ? pat.getIllnessClass() : "") +
                    " \u00B7 from " + (doc != null ? "Dr. " + doc.getName() : "Unknown");
            Label sub = new Label(subText);
            sub.setStyle("-fx-font-size: 12; -fx-text-fill: #8A8F94;");

            HBox pillRow = new HBox(7);
            pillRow.setAlignment(Pos.CENTER_LEFT);
            Label urgPill = createUrgencyPill(t.getUrgency());
            Label statusPill = createStatusPill(t.getStatus());
            pillRow.getChildren().addAll(urgPill, statusPill);

            info.getChildren().addAll(nameRow, sub, pillRow);

            row.getChildren().addAll(avatar, info);

            row.setOnMouseClicked(e -> selectTransfer(t));
            inboxList.getChildren().add(row);
        }
    }

    private void selectTransfer(Transfer t) {
        selectedTransfer = t;

        PatientDAO patientDAO = new PatientDAO();
        DoctorDAO doctorDAO = new DoctorDAO();
        Patient pat = patientDAO.getById(t.getPatientId());
        Doctor doc = doctorDAO.getById(t.getDoctorId());

        detailPanel.setVisible(true);
        detailPanel.setManaged(true);

        detailAvatar.setText(pat != null ? getInitials(pat.getName()) : "?");
        detailPatientName.setText(pat != null ? pat.getName() : "Unknown");
        detailPatientInfo.setText(pat != null ? (pat.getIllnessClass() != null ? pat.getIllnessClass() : "") + " \u00B7 " + pat.getEmail() : "");

        // urgency pill
        String urg = t.getUrgency();
        if ("emergency".equals(urg)) {
            detailUrgencyPill.setText("Emergency");
            detailUrgencyPill.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #B14A33; -fx-background-radius: 6; -fx-padding: 3 8;");
        } else if ("urgent".equals(urg)) {
            detailUrgencyPill.setText("Urgent");
            detailUrgencyPill.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #7A5A12; -fx-background-color: #FAF0D9; -fx-background-radius: 6; -fx-padding: 3 8;");
        } else {
            detailUrgencyPill.setText("Routine");
            detailUrgencyPill.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #6B6F76; -fx-background-color: #F0EEEA; -fx-background-radius: 6; -fx-padding: 3 8;");
        }

        // status pill
        String status = t.getStatus();
        if ("new".equals(status)) {
            detailStatusPill.setText("New");
            detailStatusPill.getStyleClass().setAll("status-pending");
        } else if ("accepted".equals(status)) {
            detailStatusPill.setText("Accepted");
            detailStatusPill.getStyleClass().setAll("status-available");
        } else if ("declined".equals(status)) {
            detailStatusPill.setText("Declined");
            detailStatusPill.getStyleClass().setAll("status-busy");
        } else {
            detailStatusPill.setText("Arrived");
            detailStatusPill.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #4B3FA6; -fx-background-color: #E9E7F7; -fx-background-radius: 20; -fx-padding: 4 10;");
        }

        detailDoctorName.setText(doc != null ? "Dr. " + doc.getName() : "Unknown");
        detailDate.setText(t.getCreatedAt() != null ? t.getCreatedAt().toString().substring(0, 16) : "");
        detailReason.setText(t.getClinicalNote() != null ? t.getClinicalNote() : "No notes provided.");

        // file status
        if (t.isFileSent() || t.isFileApproved()) {
            fileStatusLabel.setText("\u2713 File shared by doctor");
            fileStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #1F6E4F; -fx-font-weight: bold;");
            fileActionBtn.setVisible(false);
            fileActionBtn.setManaged(false);
        } else if (t.isFileRequested()) {
            fileStatusLabel.setText("File requested \u00B7 awaiting doctor approval");
            fileStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #7A5A12; -fx-font-weight: bold;");
            fileActionBtn.setVisible(false);
            fileActionBtn.setManaged(false);
        } else {
            fileStatusLabel.setText("File not shared yet");
            fileStatusLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #8A8F94;");
            if ("new".equals(status)) {
                fileActionBtn.setVisible(true);
                fileActionBtn.setManaged(true);
            } else {
                fileActionBtn.setVisible(false);
                fileActionBtn.setManaged(false);
            }
        }

        // action buttons
        if ("new".equals(status)) {
            actionButtons.setVisible(true);
            actionButtons.setManaged(true);
            resultBox.setVisible(false);
            resultBox.setManaged(false);
        } else {
            actionButtons.setVisible(false);
            actionButtons.setManaged(false);
            if ("accepted".equals(status)) {
                resultBox.setVisible(true);
                resultBox.setManaged(true);
                resultLabel.setText("\u2713 Accepted \u00B7 bed reserved");
                resultLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #1F6E4F; -fx-font-weight: bold;");
                resultBox.setStyle("-fx-background-color: #EAF5EE; -fx-border-color: #BFE0CE; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14 16;");
                arrivedBtn.setVisible(true);
                arrivedBtn.setManaged(true);
            } else if ("declined".equals(status)) {
                resultBox.setVisible(true);
                resultBox.setManaged(true);
                String reason = t.getDeclineReason() != null ? " \u00B7 " + t.getDeclineReason() : "";
                resultLabel.setText("Declined" + reason);
                resultLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #9A4A36; -fx-font-weight: bold;");
                resultBox.setStyle("-fx-background-color: #FBEEE9; -fx-border-color: #F0D9D0; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14 16;");
                arrivedBtn.setVisible(false);
                arrivedBtn.setManaged(false);
            } else if ("arrived".equals(status)) {
                resultBox.setVisible(true);
                resultBox.setManaged(true);
                resultLabel.setText("\u2713 Patient transferred & arrived");
                resultLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #4B3FA6; -fx-font-weight: bold;");
                resultBox.setStyle("-fx-background-color: #E9E7F7; -fx-border-color: #D2CCEC; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14 16;");
                arrivedBtn.setVisible(false);
                arrivedBtn.setManaged(false);
            }
        }
    }

    @FXML
    private void onAccept() {
        if (selectedTransfer == null) return;

        TransferDAO dao = new TransferDAO();
        dao.updateStatus(selectedTransfer.getId(), "accepted");

        // Bed reservation and patient status changes now happen in the Admission view.
        selectedTransfer = dao.getById(selectedTransfer.getId());
        selectTransfer(selectedTransfer);
        initialize(); // refresh stats + list
    }

    @FXML
    private void onDecline() {
        if (selectedTransfer == null) return;

        TextInputDialog dialog = new TextInputDialog("Insufficient capacity");
        dialog.setTitle("Decline Transfer");
        dialog.setHeaderText("Reason for declining this transfer:");
        dialog.setContentText("Reason:");

        // Modern UI Styling Injection
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: #FFFFFF; " +
                        "-fx-padding: 20px; " +
                        "-fx-font-family: 'Segoe UI', Helvetica, Arial, sans-serif;"
        );

        // Header Text Styling
        dialogPane.lookup(".header-panel").setStyle("-fx-background-color: #FFFFFF; -fx-padding: 0 0 10 0;");
        Label headerLabel = (Label) dialogPane.lookup(".header-panel .label");
        if (headerLabel != null) {
            headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1A1C1E;");
        }

        // Input Content Styling
        Label contentLabel = (Label) dialogPane.lookup(".content.label");
        if (contentLabel != null) {
            contentLabel.setStyle("-fx-text-fill: #6B6F76; -fx-font-size: 13px;");
        }

        TextField editor = dialog.getEditor();
        editor.setStyle(
                "-fx-background-color: #F4F1EC; " +
                        "-fx-background-radius: 8px; " +
                        "-fx-border-color: #E8E4DC; " +
                        "-fx-border-radius: 8px; " +
                        "-fx-padding: 10px; " +
                        "-fx-font-size: 14px;"
        );

        // Custom Buttons Styling
        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        Button cancelButton = (Button) dialogPane.lookupButton(ButtonType.CANCEL);

        if (okButton != null) {
            okButton.setStyle("-fx-background-color: #9A4A36; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand;");
        }
        if (cancelButton != null) {
            cancelButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #6B6F76; -fx-border-color: #E8E4DC; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand;");
        }

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent() && !result.get().trim().isEmpty()) {
            TransferDAO dao = new TransferDAO();
            dao.decline(selectedTransfer.getId(), result.get().trim());
            selectedTransfer = dao.getById(selectedTransfer.getId());
            selectTransfer(selectedTransfer);
            loadTransfers();
        }
    }

    @FXML
    private void onMarkArrived() {
        if (selectedTransfer == null) return;
        TransferDAO dao = new TransferDAO();
        dao.updateStatus(selectedTransfer.getId(), "arrived");
        selectedTransfer = dao.getById(selectedTransfer.getId());
        selectTransfer(selectedTransfer);
        loadTransfers();
    }

    @FXML
    private void onRequestFile() {
        if (selectedTransfer == null) return;
        TransferDAO dao = new TransferDAO();
        dao.requestFile(selectedTransfer.getId());
        selectedTransfer = dao.getById(selectedTransfer.getId());
        selectTransfer(selectedTransfer);
    }

    @FXML
    private void goToRecords() {
        loadScreen("admin-records.fxml");
    }

    @FXML
    private void onRefresh() {
        initialize();
    }
    @FXML
    private void goToAdmission() {
        loadScreen("admin-admissions.fxml");
    }

    @FXML
    private void onLogout() {
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private Label createUrgencyPill(String urgency) {
        Label pill = new Label(capitalize(urgency));
        if ("emergency".equals(urgency)) {
            pill.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #B14A33; -fx-background-radius: 5; -fx-padding: 2 6;");
        } else if ("urgent".equals(urgency)) {
            pill.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: #7A5A12; -fx-background-color: #FAF0D9; -fx-background-radius: 5; -fx-padding: 2 6;");
        } else {
            pill.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: #6B6F76; -fx-background-color: #F0EEEA; -fx-background-radius: 5; -fx-padding: 2 6;");
        }
        return pill;
    }

    private Label createStatusPill(String status) {
        Label pill = new Label(capitalize(status));
        if ("new".equals(status)) {
            pill.getStyleClass().add("status-pending");
        } else if ("accepted".equals(status)) {
            pill.getStyleClass().add("status-available");
        } else if ("declined".equals(status)) {
            pill.getStyleClass().add("status-busy");
        } else {
            pill.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: #4B3FA6; -fx-background-color: #E9E7F7; -fx-background-radius: 20; -fx-padding: 4 10;");
        }
        return pill;
    }

    private void loadScreen(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) avatarLabel.getScene().getWindow();
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
