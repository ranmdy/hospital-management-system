package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class DoctorAdmissionsController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label specialtyLabel;
    @FXML private VBox admitContent;

    private static final int TOTAL_BEDS = 6;

    @FXML
    public void initialize() {
        Doctor doctor = SessionManager.getDoctor();
        if (doctor == null) return;

        avatarLabel.setText(getInitials(doctor.getName()));
        userNameLabel.setText("Dr. " + doctor.getName());
        specialtyLabel.setText(doctor.getSpecialty());

        PatientDAO patientDAO = new PatientDAO();
        List<Patient> admitted = patientDAO.getAdmittedForDoctor(doctor.getId());

        // get current in-consult patient (for pairing bar)
        Patient currentPatient = patientDAO.getInConsultForDoctor(doctor.getId());

        int freeCount = TOTAL_BEDS - admitted.size();
        if (freeCount < 0) freeCount = 0;

        // title row
        VBox titleBox = new VBox(3);
        String pairingName = currentPatient != null ? currentPatient.getName() : "patient";
        Label title = new Label("Pair a bed for " + pairingName);
        title.setStyle("-fx-font-size: 19; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");

        String ward = doctor.getSpecialty() != null ? doctor.getSpecialty() + " Ward" : "General Ward";
        Label subtitle = new Label(ward + " · attending Dr. " + doctor.getName());
        subtitle.setStyle("-fx-font-size: 13; -fx-text-fill: #8A8F94;");
        titleBox.getChildren().addAll(title, subtitle);
        admitContent.getChildren().add(titleBox);

        // free beds indicator
        HBox freeIndicator = new HBox(7);
        freeIndicator.setAlignment(Pos.CENTER_LEFT);
        Label greenDot = new Label("\u25CF");
        greenDot.setStyle("-fx-font-size: 8; -fx-text-fill: #2E8B63;");
        Label freeLabel = new Label(freeCount + " free bed" + (freeCount != 1 ? "s" : "") + " in " + ward);
        freeLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #1F6E4F;");
        freeIndicator.getChildren().addAll(greenDot, freeLabel);
        admitContent.getChildren().add(freeIndicator);

        // bed grid — 3 columns
        FlowPane bedGrid = new FlowPane(13, 13);
        bedGrid.setPrefWrapLength(680);

        for (int i = 1; i <= TOTAL_BEDS; i++) {
            String bedId = "B-0" + i;
            VBox bedCard = new VBox(0);
            bedCard.setPrefWidth(200);
            bedCard.setMinWidth(200);
            bedCard.setPadding(new Insets(16));

            // check if this bed is occupied
            Patient occupant = null;
            if (i <= admitted.size()) {
                occupant = admitted.get(i - 1);
            }

            if (occupant != null) {
                // occupied bed
                bedCard.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 16; -fx-opacity: 0.7;");

                HBox topRow = new HBox();
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label idLabel = new Label(bedId);
                idLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
                Region sp = new Region();
                HBox.setHgrow(sp, javafx.scene.layout.Priority.ALWAYS);
                Label tag = new Label("Occupied");
                tag.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #B14A33; -fx-background-color: #FBEEE9; -fx-background-radius: 6; -fx-padding: 2 8;");
                topRow.getChildren().addAll(idLabel, sp, tag);

                Label patLabel = new Label(occupant.getName());
                patLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #8A8F94; -fx-padding: 10 0 0 0;");

                bedCard.getChildren().addAll(topRow, patLabel);
            } else {
                // free bed
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
            }

            bedGrid.getChildren().add(bedCard);
        }

        admitContent.getChildren().add(bedGrid);

        // pairing bar at bottom
        HBox pairingBar = new HBox(14);
        pairingBar.setAlignment(Pos.CENTER_LEFT);
        pairingBar.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 16 18;");

        Label pairAvatar = new Label(currentPatient != null ? getInitials(currentPatient.getName()) : "?");
        pairAvatar.setStyle("-fx-background-color: #DDF0EB; -fx-text-fill: #127566; -fx-background-radius: 12; -fx-pref-width: 42; -fx-pref-height: 42; -fx-alignment: center; -fx-font-weight: bold; -fx-font-size: 14;");

        VBox pairInfo = new VBox(2);
        Label pairLabel = new Label("PAIRING");
        pairLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #9A9EA5; -fx-font-weight: bold;");
        Label pairDesc = new Label(pairingName + " \u2192 free bed · attending Dr. " + doctor.getName());
        pairDesc.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
        pairInfo.getChildren().addAll(pairLabel, pairDesc);

        Region barSpacer = new Region();
        HBox.setHgrow(barSpacer, javafx.scene.layout.Priority.ALWAYS);

        Label selectHint = new Label("Select a free bed above to confirm");
        selectHint.setStyle("-fx-font-size: 12; -fx-text-fill: #8A8F94;");

        pairingBar.getChildren().addAll(pairAvatar, pairInfo, barSpacer, selectHint);
        admitContent.getChildren().add(pairingBar);

        // if no beds free, show warning
        if (freeCount == 0) {
            VBox warningBox = new VBox(10);
            warningBox.setAlignment(Pos.CENTER);
            warningBox.setStyle("-fx-background-color: #FBEEE9; -fx-border-color: #F0D9D0; -fx-border-radius: 16; -fx-background-radius: 16; -fx-padding: 30;");

            Label warnTitle = new Label("No beds available");
            warnTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #B14A33;");

            Label warnDesc = new Label("All beds in " + ward + " are currently occupied.\nIssue a prescription and arrange follow-up instead.");
            warnDesc.setStyle("-fx-font-size: 14; -fx-text-fill: #8A6A60;");
            warnDesc.setWrapText(true);

            warningBox.getChildren().addAll(warnTitle, warnDesc);
            admitContent.getChildren().add(warningBox);
        }
    }

    @FXML
    private void goToDashboard() { loadScreen("doctor-dashboard.fxml"); }

    @FXML
    private void goToConsultation() { loadScreen("doctor-chat.fxml"); }

    @FXML
    private void goToPrescription() { loadScreen("doctor-prescriptions.fxml"); }

    @FXML
    private void onLogout() {
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private void loadScreen(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) admitContent.getScene().getWindow();
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
