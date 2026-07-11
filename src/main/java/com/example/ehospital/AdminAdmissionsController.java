package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdminAdmissionsController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label specialtyLabel;
    @FXML private VBox admitContent;

    private Hospital hospital;
    private Patient currentPatient;
    private Transfer selectedTransfer;

    @FXML
    public void initialize() {
        admitContent.getChildren().clear();

        HospitalAdmin admin = SessionManager.getAdmin();
        if (admin == null) return;

        avatarLabel.setText(getInitials(admin.getName()));
        userNameLabel.setText(admin.getName());
        if (specialtyLabel != null) specialtyLabel.setText("Hospital Administrator");

        HospitalDAO hospitalDAO = new HospitalDAO();
        hospital = hospitalDAO.getByAdminId(admin.getId());

        if (hospital == null) {
            Label warn = new Label("No hospital linked. Please link a hospital to manage admissions.");
            warn.setStyle("-fx-font-size: 16; -fx-text-fill: #B14A33;");
            admitContent.getChildren().add(warn);
            return;
        }

        int totalBeds = hospital.getTotalBeds();
        int freeCount = hospital.getAvailableBeds();

        // transfer selector dropdown
        VBox selectorBox = new VBox(5);
        Label selectorLabel = new Label("Select an incoming Transfer Request to place:");
        selectorLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #4B3FA6;");

        ComboBox<Transfer> transferCombo = new ComboBox<>();
        transferCombo.setPromptText("Select a pending transfer...");
        transferCombo.setPrefWidth(450);
        transferCombo.setStyle("-fx-background-color: white; -fx-border-color: #D7E3F4; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 4;");

        TransferDAO transferDAO = new TransferDAO();
        List<Transfer> allTransfers = transferDAO.getByHospitalId(hospital.getId());
        List<Transfer> pendingTransfers = new ArrayList<>();

        if (allTransfers != null) {
            for (Transfer t : allTransfers) {
                String status = (t.getStatus() != null) ? t.getStatus().trim().toLowerCase() : "";

                if ("accepted".equals(status) || "admitted".equals(status)) {
                    pendingTransfers.add(t);
                }
            }
        }

        transferCombo.getItems().clear();
        transferCombo.getItems().addAll(pendingTransfers);

        PatientDAO patientDAO = new PatientDAO();
        DoctorDAO doctorDAO = new DoctorDAO();

        transferCombo.setConverter(new StringConverter<Transfer>() {
            @Override
            public String toString(Transfer t) {
                if (t == null) return "";
                Patient p = patientDAO.getById(t.getPatientId());
                Doctor d = doctorDAO.getById(t.getDoctorId());
                String pName = p != null ? p.getName() : "Unknown Patient";
                String dName = d != null ? d.getName() : "Unknown Doctor";
                return pName + " (From: Dr. " + dName + " · " + capitalize(t.getUrgency()) + " · " + capitalize(t.getStatus()) + ")";
            }
            @Override
            public Transfer fromString(String string) { return null; }
        });

        if (selectedTransfer != null) {
            transferCombo.setValue(selectedTransfer);
            currentPatient = patientDAO.getById(selectedTransfer.getPatientId());
        }

        transferCombo.setOnAction(e -> {
            selectedTransfer = transferCombo.getValue();
            if (selectedTransfer != null) {
                currentPatient = patientDAO.getById(selectedTransfer.getPatientId());
                initialize();
            }
        });

        selectorBox.getChildren().addAll(selectorLabel, transferCombo);
        admitContent.getChildren().add(selectorBox);

        VBox titleBox = new VBox(3);
        String pairingName = currentPatient != null ? currentPatient.getName() : "[Select a Patient above]";
        Label title = new Label("Pair a bed for " + pairingName);
        title.setStyle("-fx-font-size: 19; -fx-font-weight: bold; -fx-text-fill: #1A1C20; -fx-padding: 10 0 0 0;");

        Label subtitle = new Label(hospital.getName() + " · " + hospital.getLocation());
        subtitle.setStyle("-fx-font-size: 13; -fx-text-fill: #8A8F94;");
        titleBox.getChildren().addAll(title, subtitle);
        admitContent.getChildren().add(titleBox);

        HBox freeIndicator = new HBox(7);
        freeIndicator.setAlignment(Pos.CENTER_LEFT);
        Label greenDot = new Label("\u25CF");
        greenDot.setStyle("-fx-font-size: 8; -fx-text-fill: #2E8B63;");
        Label freeLabel = new Label(freeCount + " free bed" + (freeCount != 1 ? "s" : "") + " available");
        freeLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #1F6E4F;");
        freeIndicator.getChildren().addAll(greenDot, freeLabel);
        admitContent.getChildren().add(freeIndicator);

        FlowPane bedGrid = new FlowPane(13, 13);
        bedGrid.setPrefWrapLength(680);

        for (int i = 1; i <= totalBeds; i++) {
            String bedId = "B-0" + i;
            VBox bedCard = new VBox(0);
            bedCard.setPrefWidth(200);
            bedCard.setMinWidth(200);
            bedCard.setPadding(new Insets(16));

            Patient admittedPat = patientDAO.getAdmittedPatientByBed(bedId);
            boolean isOccupied = (admittedPat != null);

            if (isOccupied) {
                bedCard.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 16; -fx-opacity: 0.8; -fx-cursor: hand;");

                HBox topRow = new HBox();
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label idLabel = new Label(bedId);
                idLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
                Region sp = new Region();
                HBox.setHgrow(sp, javafx.scene.layout.Priority.ALWAYS);
                Label tag = new Label("Occupied");
                tag.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #B14A33; -fx-background-color: #FBEEE9; -fx-background-radius: 6; -fx-padding: 2 8;");
                topRow.getChildren().addAll(idLabel, sp, tag);

                String patName = admittedPat.getName();
                Label patNameLabel = new Label(patName);
                patNameLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #4B3FA6; -fx-padding: 10 0 2 0;");

                Label actionLabel = new Label("Tap to discharge \u2192");
                actionLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #8A8F94;");

                bedCard.getChildren().addAll(topRow, patNameLabel, actionLabel);

                final String clickedBed = bedId;
                bedCard.setOnMouseClicked(e -> showDischargeDialog(clickedBed, admittedPat));
            }
            else {
                bedCard.setStyle("-fx-background-color: white; -fx-border-color: #D7E3F4; -fx-border-width: 1.5; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 16; -fx-cursor: hand;");
                HBox topRow = new HBox();
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label idLabel = new Label(bedId);
                idLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
                Region sp = new Region();
                HBox.setHgrow(sp, javafx.scene.layout.Priority.ALWAYS);
                Label tag = new Label("Free");
                tag.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #1F6E4F; -fx-background-color: #E8F5E9; -fx-background-radius: 6; -fx-padding: 2 8;");
                topRow.getChildren().addAll(idLabel, sp, tag);

                Label assignLabel = new Label("Tap to assign \u2192");
                assignLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #1F6E4F; -fx-padding: 10 0 0 0;");
                bedCard.getChildren().addAll(topRow, assignLabel);

                final String bed = bedId;
                bedCard.setOnMouseClicked(e -> assignBed(bed));
            }
            bedGrid.getChildren().add(bedCard);
        }
        admitContent.getChildren().add(bedGrid);

        HBox pairingBar = new HBox(14);
        pairingBar.setAlignment(Pos.CENTER_LEFT);
        pairingBar.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 16 18;");

        Label pairAvatar = new Label(currentPatient != null ? getInitials(currentPatient.getName()) : "?");
        pairAvatar.setStyle("-fx-background-color: #DDF0EB; -fx-text-fill: #127566; -fx-background-radius: 12; -fx-pref-width: 42; -fx-pref-height: 42; -fx-alignment: center; -fx-font-weight: bold; -fx-font-size: 14;");

        VBox pairInfo = new VBox(2);
        Label pairLabel = new Label("PAIRING");
        pairLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #9A9EA5; -fx-font-weight: bold;");
        String contextDesc = currentPatient != null ? currentPatient.getName() + " \u2192 allocating bed placement" : "No patient selected from transfer queue.";
        Label pairDesc = new Label(contextDesc);
        pairDesc.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
        pairInfo.getChildren().addAll(pairLabel, pairDesc);

        Region barSpacer = new Region();
        HBox.setHgrow(barSpacer, javafx.scene.layout.Priority.ALWAYS);

        Label selectHint = new Label(currentPatient != null ? "Select a free bed card above to confirm assignment" : "Please select a transfer request first");
        selectHint.setStyle("-fx-font-size: 12; -fx-text-fill: #8A8F94;");

        pairingBar.getChildren().addAll(pairAvatar, pairInfo, barSpacer, selectHint);
        admitContent.getChildren().add(pairingBar);

        if (freeCount == 0) {
            VBox warningBox = new VBox(10);
            warningBox.setAlignment(Pos.CENTER);
            warningBox.setStyle("-fx-background-color: #FBEEE9; -fx-border-color: #F0D9D0; -fx-border-radius: 16; -fx-background-radius: 16; -fx-padding: 30;");

            Label warnTitle = new Label("Facility Beds Exhausted");
            warnTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #B14A33;");

            Label warnDesc = new Label("All physical beds allocated to " + hospital.getName() + " are filled.");
            warnDesc.setStyle("-fx-font-size: 14; -fx-text-fill: #8A6A60;");
            warnDesc.setWrapText(true);

            Button dashboardBtn = new Button("Return to Dashboard \u2192");
            dashboardBtn.setStyle("-fx-background-color: #4B3FA6; -fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;");
            dashboardBtn.setOnAction(e -> goToTransfers());

            warningBox.getChildren().addAll(warnTitle, warnDesc, dashboardBtn);
            admitContent.getChildren().add(warningBox);
        }
    }

    private void assignBed(String bedId) {
        if (currentPatient == null || selectedTransfer == null) return;

        TransferDAO transferDAO = new TransferDAO();
        HospitalDAO hospitalDAO = new HospitalDAO();
        PatientDAO patientDAO = new PatientDAO();
        ChatDAO chatDAO = new ChatDAO();

        boolean isReassignment = "admitted".equals(selectedTransfer.getStatus());

        if (isReassignment) {
            patientDAO.assignToBed(currentPatient.getId(), bedId);
            chatDAO.sendMessage(hospital.getId(), currentPatient.getId(), "admin",
                    "Your bed assignment has been changed. You are now allocated to bed location " + bedId + ".");
        } else {
            hospitalDAO.decrementBed(hospital.getId());
            transferDAO.updateStatus(selectedTransfer.getId(), "admitted");
            patientDAO.assignToBed(currentPatient.getId(), bedId);

            chatDAO.sendMessage(hospital.getId(), currentPatient.getId(), "admin",
                    "Your transfer request has been confirmed. You are admitted to bed location " + bedId + ".");
        }

        currentPatient = null;
        selectedTransfer = null;
        admitContent.getChildren().clear();
        initialize();
    }

    private void showDischargeDialog(String bedId, Patient patient) {
        if (patient == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Discharge");
        alert.setHeaderText("Discharge Patient");
        alert.setContentText("Are you sure you want to discharge " + patient.getName() + "? This action will release assigned bed assets.");

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: #FFFFFF; " +
                        "-fx-padding: 20px; " +
                        "-fx-font-family: 'Segoe UI', Helvetica, Arial, sans-serif;"
        );

        dialogPane.lookup(".header-panel").setStyle("-fx-background-color: #FFFFFF; -fx-padding: 0 0 8 0;");
        Label headerLabel = (Label) dialogPane.lookup(".header-panel .label");
        if (headerLabel != null) {
            headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1A1C1E;");
        }

        Label contentLabel = (Label) dialogPane.lookup(".content.label");
        if (contentLabel != null) {
            contentLabel.setStyle("-fx-text-fill: #6B6F76; -fx-font-size: 13px; -fx-line-spacing: 1.4;");
        }

        Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        Button cancelButton = (Button) dialogPane.lookupButton(ButtonType.CANCEL);

        if (okButton != null) {
            okButton.setText("Confirm Discharge");
            okButton.setStyle("-fx-background-color: #B14A33; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand;");
        }
        if (cancelButton != null) {
            cancelButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #6B6F76; -fx-border-color: #E8E4DC; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand;");
        }

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            HospitalDAO hospitalDAO = new HospitalDAO();
            PatientDAO patientDAO = new PatientDAO();

            hospitalDAO.incrementBed(hospital.getId());
            patientDAO.dischargeFromBed(patient.getId());

            admitContent.getChildren().clear();
            initialize();
        }
    }

    @FXML private void goToRecords() { loadScreen("admin-records.fxml"); }

    @FXML private void goToTransfers() { loadScreen("admin-dashboard.fxml"); }

    @FXML private void onRefresh() { initialize(); }

    @FXML private void onLogout() {
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private void loadScreen(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) admitContent.getScene().getWindow();
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
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
