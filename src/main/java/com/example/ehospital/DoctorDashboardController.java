package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class DoctorDashboardController {

    @FXML private Label greetingLabel;
    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label specialtyLabel;
    @FXML private Label doctorStatusLabel;
    @FXML private Label topSubLabel;
    @FXML private Label statusInfoLabel;
    @FXML private Label queueCountLabel;
    @FXML private Label emptyLabel;
    @FXML private VBox queueList;
    @FXML private VBox currentPatientCard;
    @FXML private Label currentPatientAvatar;
    @FXML private Label currentPatientName;
    @FXML private Label currentPatientSymptoms;
    @FXML private Label currentPatientClass;
    @FXML private Label currentPatientStatus;

    @FXML
    public void initialize() {
        Doctor doctor = SessionManager.getDoctor();
        if (doctor == null) return;

        // refresh from db
        DoctorDAO dao = new DoctorDAO();
        doctor = dao.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);

        greetingLabel.setText("Good day, Dr. " + doctor.getName().split(" ")[0]);
        avatarLabel.setText(getInitials(doctor.getName()));
        userNameLabel.setText("Dr. " + doctor.getName());
        specialtyLabel.setText(doctor.getSpecialty());

        updateDoctorStatus(doctor);
        loadCurrentPatient(doctor);
        loadQueue(doctor);
    }

    private void updateDoctorStatus(Doctor doctor) {
        String status = doctor.getStatus();
        if (status.equals("available")) {
            doctorStatusLabel.setText("Available");
            doctorStatusLabel.getStyleClass().setAll("status-available");
            statusInfoLabel.setText("Available");
            statusInfoLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #127566;");
        } else if (status.equals("busy")) {
            doctorStatusLabel.setText("Busy");
            doctorStatusLabel.getStyleClass().setAll("status-busy");
            statusInfoLabel.setText("In consultation");
            statusInfoLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #E65100;");
        } else {
            doctorStatusLabel.setText("On hold");
            doctorStatusLabel.getStyleClass().setAll("status-pending");
            statusInfoLabel.setText("On hold");
            statusInfoLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #6B6F76;");
        }
    }

    private void loadCurrentPatient(Doctor doctor) {
        PatientDAO patientDAO = new PatientDAO();
        Patient current = patientDAO.getInConsultForDoctor(doctor.getId());

        if (current != null) {
            currentPatientCard.setVisible(true);
            currentPatientCard.setManaged(true);
            currentPatientAvatar.setText(getInitials(current.getName()));
            currentPatientName.setText(current.getName());
            currentPatientSymptoms.setText(current.getSymptoms() != null ? current.getSymptoms() : "No symptoms recorded");
            currentPatientClass.setText(current.getIllnessClass() != null ? current.getIllnessClass() : "—");
            currentPatientStatus.setText("In consult");
        }
    }

    private void loadQueue(Doctor doctor) {
        PatientDAO patientDAO = new PatientDAO();
        List<Patient> pending = patientDAO.getPendingForDoctor(doctor.getId());

        queueCountLabel.setText(pending.size() + " patient" + (pending.size() != 1 ? "s" : ""));
        queueList.getChildren().clear();

        if (pending.isEmpty()) {
            Label empty = new Label("No pending patients in your queue.");
            empty.setStyle("-fx-font-size: 14; -fx-text-fill: #9A9EA5;");
            queueList.getChildren().add(empty);
            return;
        }

        for (Patient patient : pending) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: #FBFAF7; -fx-background-radius: 12; -fx-padding: 14; -fx-border-color: #E8E4DC; -fx-border-radius: 12;");

            Label avatar = new Label(getInitials(patient.getName()));
            avatar.setStyle("-fx-background-color: #DDF0EB; -fx-text-fill: #127566; -fx-background-radius: 12; -fx-pref-width: 42; -fx-pref-height: 42; -fx-alignment: center; -fx-font-weight: bold;");

            VBox info = new VBox(2);
            Label name = new Label(patient.getName());
            name.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            Label symptoms = new Label(patient.getSymptoms() != null ? patient.getSymptoms() : "—");
            symptoms.setStyle("-fx-font-size: 13; -fx-text-fill: #6B6F76;");
            symptoms.setMaxWidth(300);
            Label classLabel = new Label(patient.getIllnessClass() != null ? patient.getIllnessClass() : "—");
            classLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #1F4D8F;");
            info.getChildren().addAll(name, symptoms, classLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            Button acceptBtn = new Button("Accept");
            acceptBtn.getStyleClass().add("btn-primary");
            acceptBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 16 8 16;");
            acceptBtn.setOnAction(e -> acceptPatient(patient));

            row.getChildren().addAll(avatar, info, spacer, acceptBtn);
            queueList.getChildren().add(row);
        }
    }

    private void acceptPatient(Patient patient) {
        Doctor doctor = SessionManager.getDoctor();

        // check if doctor already has a patient in consult
        PatientDAO patientDAO = new PatientDAO();
        Patient current = patientDAO.getInConsultForDoctor(doctor.getId());
        if (current != null) {
            // already busy
            return;
        }

        // accept: patient → in_consult, doctor → busy
        patientDAO.updateStatus(patient.getId(), "in_consult");
        DoctorDAO doctorDAO = new DoctorDAO();
        doctorDAO.updateStatus(doctor.getId(), "busy");

        // refresh and go to chat
        doctor = doctorDAO.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);
        loadScreen("doctor-chat.fxml");
    }

    @FXML
    private void goToConsultation() {
        loadScreen("doctor-chat.fxml");
    }

    @FXML
    private void goToAdmission() {
        loadScreen("doctor-admissions.fxml");
    }

    @FXML
    private void goToPrescription() {
        loadScreen("doctor-prescriptions.fxml");
    }

    @FXML
    private void onRefresh() {
        initialize();
    }

    @FXML
    private void onLogout() {
        SessionManager.logout();
        loadScreen("login-view.fxml");
    }

    private void loadScreen(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) greetingLabel.getScene().getWindow();
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
