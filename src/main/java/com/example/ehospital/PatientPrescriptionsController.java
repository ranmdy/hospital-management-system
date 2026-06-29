package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class PatientPrescriptionsController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private VBox rxList;

    @FXML
    public void initialize() {
        Patient patient = SessionManager.getPatient();
        if (patient == null) return;

        avatarLabel.setText(getInitials(patient.getName()));
        userNameLabel.setText(patient.getName());

        PrescriptionDAO rxDAO = new PrescriptionDAO();
        List<Prescription> prescriptions = rxDAO.getAllByPatientId(patient.getId());

        if (prescriptions.isEmpty()) {
            // empty state — dashed border card with Rx icon
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-background-color: white; -fx-border-color: #D9D4CA; -fx-border-style: dashed; -fx-border-radius: 18; -fx-background-radius: 18; -fx-padding: 50 30;");

            Label rxIcon = new Label("\u211E");
            rxIcon.setStyle("-fx-font-size: 40; -fx-text-fill: #C9C4BA;");

            Label title = new Label("No prescription yet");
            title.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");

            Label desc = new Label("Once the doctor issues a prescription during consultation, it will appear here as a signed record.");
            desc.setStyle("-fx-font-size: 14; -fx-text-fill: #9A9EA5;");
            desc.setWrapText(true);
            desc.setMaxWidth(360);

            empty.getChildren().addAll(rxIcon, title, desc);
            rxList.getChildren().add(empty);
            return;
        }

        DoctorDAO doctorDAO = new DoctorDAO();

        for (Prescription rx : prescriptions) {
            Doctor doc = doctorDAO.getById(rx.getDoctorId());

            // outer card with rounded border
            VBox card = new VBox();
            card.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-radius: 18; -fx-background-radius: 18;");

            // navy header
            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setStyle("-fx-background-color: #13294C; -fx-padding: 22 26; -fx-background-radius: 18 18 0 0;");

            Label rxSymbol = new Label("\u211E");
            rxSymbol.setStyle("-fx-background-color: white; -fx-text-fill: #13294C; -fx-font-size: 22; -fx-font-weight: bold; -fx-pref-width: 42; -fx-pref-height: 42; -fx-background-radius: 12; -fx-alignment: center;");

            VBox headerInfo = new VBox(2);
            Label headerTitle = new Label("Prescription");
            headerTitle.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: white;");
            Label headerSub = new Label("Ranmdy Healthcare Center");
            headerSub.setStyle("-fx-font-size: 13; -fx-text-fill: #A9BBD6;");
            headerInfo.getChildren().addAll(headerTitle, headerSub);

            Region headerSpacer = new Region();
            HBox.setHgrow(headerSpacer, javafx.scene.layout.Priority.ALWAYS);

            String dateStr = rx.getCreatedAt() != null ? rx.getCreatedAt().toString().substring(0, 16) : "";
            Label dateLabel = new Label(dateStr);
            dateLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #A9BBD6;");

            header.getChildren().addAll(rxSymbol, headerInfo, headerSpacer, dateLabel);

            // body
            VBox body = new VBox(0);
            body.setStyle("-fx-padding: 24 26;");

            // patient + prescriber row
            HBox infoRow = new HBox(30);
            infoRow.setPadding(new Insets(0, 0, 22, 0));

            VBox patientInfo = new VBox(2);
            Label patLabel = new Label("PATIENT");
            patLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #9A9EA5;");
            Label patName = new Label(patient.getName());
            patName.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            Label patEmail = new Label(patient.getEmail());
            patEmail.setStyle("-fx-font-size: 13; -fx-text-fill: #8A8F94;");
            patientInfo.getChildren().addAll(patLabel, patName, patEmail);

            VBox prescriberInfo = new VBox(2);
            Label presLabel = new Label("PRESCRIBER");
            presLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #9A9EA5;");
            Label docName = new Label(doc != null ? "Dr. " + doc.getName() : "Unknown");
            docName.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            Label docSpec = new Label(doc != null ? doc.getSpecialty() + " · " + doc.getLicenseNumber() : "");
            docSpec.setStyle("-fx-font-size: 13; -fx-text-fill: #8A8F94;");
            prescriberInfo.getChildren().addAll(presLabel, docName, docSpec);

            infoRow.getChildren().addAll(patientInfo, prescriberInfo);

            // medication box (blue background)
            VBox medBox = new VBox(6);
            medBox.setStyle("-fx-background-color: #F3F7FD; -fx-border-color: #D7E3F4; -fx-border-radius: 14; -fx-background-radius: 14; -fx-padding: 20;");

            Label medLabel = new Label("MEDICATION");
            medLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #6E84A8;");
            Label medName = new Label(rx.getMedicine());
            medName.setStyle("-fx-font-size: 21; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            Label medDose = new Label(rx.getDosage());
            medDose.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #1F4D8F;");
            medBox.getChildren().addAll(medLabel, medName, medDose);

            // notes
            VBox notesBox = new VBox(4);
            notesBox.setPadding(new Insets(18, 0, 0, 0));
            Label notesLabel = new Label("NOTES");
            notesLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #9A9EA5;");
            String notesStr = rx.getNotes() != null && !rx.getNotes().isEmpty() ? rx.getNotes() : "No additional notes.";
            Label notesText = new Label(notesStr);
            notesText.setStyle("-fx-font-size: 14; -fx-text-fill: #3A3F47;");
            notesText.setWrapText(true);
            notesBox.getChildren().addAll(notesLabel, notesText);

            // footer — signature
            HBox footer = new HBox();
            footer.setAlignment(Pos.BOTTOM_LEFT);
            footer.setPadding(new Insets(24, 0, 0, 0));
            footer.setStyle("-fx-border-color: #EDEAE2; -fx-border-width: 1 0 0 0;");

            Label signedLabel = new Label("Digitally signed · " + dateStr);
            signedLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #9A9EA5;");

            Region footerSpacer = new Region();
            HBox.setHgrow(footerSpacer, javafx.scene.layout.Priority.ALWAYS);

            VBox sigBox = new VBox(2);
            sigBox.setAlignment(Pos.CENTER_RIGHT);
            Label sigName = new Label(doc != null ? "Dr. " + doc.getName() : "");
            sigName.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #1F4D8F; -fx-font-style: italic;");
            Label sigLabel = new Label("Authorized signature");
            sigLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #9A9EA5; -fx-border-color: #D9D4CA; -fx-border-width: 1 0 0 0; -fx-padding: 3 0 0 0;");
            sigBox.getChildren().addAll(sigName, sigLabel);

            footer.getChildren().addAll(signedLabel, footerSpacer, sigBox);

            body.getChildren().addAll(infoRow, medBox, notesBox, footer);
            card.getChildren().addAll(header, body);
            rxList.getChildren().add(card);
        }
    }

    @FXML
    private void goToDashboard() { loadScreen("patient-dashboard.fxml"); }

    @FXML
    private void goToSymptom() { loadScreen("symptom-view.fxml"); }

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
    private void onLogout() {
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private void loadScreen(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) rxList.getScene().getWindow();
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
