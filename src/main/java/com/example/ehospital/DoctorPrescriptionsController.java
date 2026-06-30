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

public class DoctorPrescriptionsController {

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label specialtyLabel;
    @FXML private VBox rxList;

    @FXML
    public void initialize() {
        Doctor doctor = SessionManager.getDoctor();
        if (doctor == null) return;

        avatarLabel.setText(getInitials(doctor.getName()));
        userNameLabel.setText("Dr. " + doctor.getName());
        specialtyLabel.setText(doctor.getSpecialty());

        PrescriptionDAO rxDAO = new PrescriptionDAO();
        List<Prescription> prescriptions = rxDAO.getAllByDoctorId(doctor.getId());

        if (prescriptions.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-background-color: white; -fx-border-color: #D9D4CA; -fx-border-style: dashed; -fx-border-radius: 18; -fx-background-radius: 18; -fx-padding: 50 30;");

            Label rxIcon = new Label("\u211E");
            rxIcon.setStyle("-fx-font-size: 40; -fx-text-fill: #C9C4BA;");

            Label title = new Label("No prescriptions issued");
            title.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");

            Label desc = new Label("Prescriptions you issue during consultations will appear here.");
            desc.setStyle("-fx-font-size: 14; -fx-text-fill: #9A9EA5;");
            desc.setWrapText(true);
            desc.setMaxWidth(360);

            empty.getChildren().addAll(rxIcon, title, desc);
            rxList.getChildren().add(empty);
            return;
        }

        PatientDAO patientDAO = new PatientDAO();

        for (Prescription rx : prescriptions) {
            Patient pat = patientDAO.getById(rx.getPatientId());

            VBox card = new VBox();
            card.setStyle("-fx-background-color: white; -fx-border-color: #E8E4DC; -fx-border-radius: 18; -fx-background-radius: 18;");

            // top row — patient info + date
            HBox topRow = new HBox(14);
            topRow.setAlignment(Pos.CENTER_LEFT);
            topRow.setStyle("-fx-padding: 18 22; -fx-border-color: #EDEAE2; -fx-border-width: 0 0 1 0;");

            Label patAvatar = new Label(pat != null ? getInitials(pat.getName()) : "?");
            patAvatar.setStyle("-fx-background-color: #EEF4FC; -fx-text-fill: #1F4D8F; -fx-background-radius: 12; -fx-pref-width: 42; -fx-pref-height: 42; -fx-alignment: center; -fx-font-weight: bold;");

            VBox patInfo = new VBox(2);
            Label patName = new Label(pat != null ? pat.getName() : "Unknown");
            patName.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            Label patClass = new Label(pat != null && pat.getIllnessClass() != null ? pat.getIllnessClass() : "—");
            patClass.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #1F4D8F;");
            patInfo.getChildren().addAll(patName, patClass);

            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            String dateStr = rx.getCreatedAt() != null ? rx.getCreatedAt().toString().substring(0, 16) : "";
            Label dateLabel = new Label(dateStr);
            dateLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #9A9EA5;");

            topRow.getChildren().addAll(patAvatar, patInfo, spacer, dateLabel);

            // medication section
            VBox medSection = new VBox(6);
            medSection.setStyle("-fx-padding: 18 22;");

            HBox medRow = new HBox(10);
            medRow.setAlignment(Pos.CENTER_LEFT);
            Label rxSymbol = new Label("\u211E");
            rxSymbol.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #1F4D8F;");
            Label medName = new Label(rx.getMedicine());
            medName.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            medRow.getChildren().addAll(rxSymbol, medName);

            Label dose = new Label(rx.getDosage());
            dose.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1F4D8F;");

            String notesStr = rx.getNotes() != null && !rx.getNotes().isEmpty() ? rx.getNotes() : "";
            medSection.getChildren().addAll(medRow, dose);
            if (!notesStr.isEmpty()) {
                Label notes = new Label(notesStr);
                notes.setStyle("-fx-font-size: 13; -fx-text-fill: #6B6F76;");
                notes.setWrapText(true);
                medSection.getChildren().add(notes);
            }

            card.getChildren().addAll(topRow, medSection);
            rxList.getChildren().add(card);
        }
    }

    @FXML
    private void goToDashboard() { loadScreen("doctor-dashboard.fxml"); }

    @FXML
    private void goToConsultation() { loadScreen("doctor-chat.fxml"); }

    @FXML
    private void goToAdmission() { loadScreen("doctor-admissions.fxml"); }

    @FXML
    private void goToTransfer() { loadScreen("doctor-transfer.fxml"); }

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
