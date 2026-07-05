package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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

    @FXML private Label avatarLabel;
    @FXML private Label userNameLabel;
    @FXML private Label specialtyLabel;
    @FXML private Label doctorStatusLabel;
    @FXML private Label topSubLabel;
    @FXML private Label queueTitle;
    @FXML private Label emptyLabel;
    @FXML private VBox queueList;
    @FXML private VBox currentPatientCard;
    @FXML private Label currentPatientAvatar;
    @FXML private Label currentPatientName;
    @FXML private Label currentPatientSymptoms;
    @FXML private Label currentPatientClass;
    @FXML private Button btnAvailable;
    @FXML private Button btnBusy;
    @FXML private Button btnOnHold;

    @FXML
    public void initialize() {
        Doctor doctor = SessionManager.getDoctor();
        if (doctor == null) return;

        // refresh from db
        DoctorDAO dao = new DoctorDAO();
        doctor = dao.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);

        avatarLabel.setText(getInitials(doctor.getName()));
        userNameLabel.setText("Dr. " + doctor.getName());
        specialtyLabel.setText(doctor.getSpecialty());

        updateDoctorStatus(doctor);
        updateAvailToggle(doctor.getStatus());
        loadCurrentPatient(doctor);
        loadQueue(doctor);
    }

    private void updateDoctorStatus(Doctor doctor) {
        String status = doctor.getStatus();
        if ("available".equals(status)) {
            doctorStatusLabel.setText("Available");
            doctorStatusLabel.getStyleClass().setAll("status-available");
        } else if ("busy".equals(status)) {
            doctorStatusLabel.setText("Busy");
            doctorStatusLabel.getStyleClass().setAll("status-busy");
        } else {
            doctorStatusLabel.setText("On hold");
            doctorStatusLabel.getStyleClass().setAll("status-pending");
        }
    }

    private void updateAvailToggle(String status) {
        String active = "-fx-background-color: white; -fx-text-fill: #127566; -fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 6 14; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;";
        String inactive = "-fx-background-color: transparent; -fx-text-fill: #6B6F76; -fx-font-size: 12; -fx-font-weight: bold; -fx-padding: 6 14; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;";

        btnAvailable.setStyle("available".equals(status) ? active : inactive);
        btnBusy.setStyle("busy".equals(status) ? active : inactive);
        btnOnHold.setStyle("on_hold".equals(status) ? active : inactive);
    }

    private void loadCurrentPatient(Doctor doctor) {
        PatientDAO patientDAO = new PatientDAO();
        Patient current = patientDAO.getInConsultForDoctor(doctor.getId());

        if (current != null) {
            currentPatientCard.setVisible(true);
            currentPatientCard.setManaged(true);
            currentPatientAvatar.setText(getInitials(current.getName()));
            currentPatientName.setText(current.getName());
            currentPatientClass.setText(current.getIllnessClass() != null ? current.getIllnessClass() : "\u2014");
            currentPatientSymptoms.setText(current.getSymptoms() != null ? current.getSymptoms() : "No symptoms recorded");
        }
    }

    private void loadQueue(Doctor doctor) {
        PatientDAO patientDAO = new PatientDAO();
        List<Patient> pending = patientDAO.getPendingForDoctor(doctor.getId());

        int count = pending.size();
        queueTitle.setText("Patient queue \u00B7 " + count + " waiting");
        topSubLabel.setText("Your patient queue \u00B7 " + count + " waiting");
        queueList.getChildren().clear();

        if (pending.isEmpty()) {
            Label empty = new Label("No pending patients in your queue.");
            empty.setStyle("-fx-font-size: 14; -fx-text-fill: #9A9EA5;");
            queueList.getChildren().add(empty);
            return;
        }

        boolean first = true;
        for (Patient patient : pending) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: #FBFAF7; -fx-background-radius: 12; -fx-padding: 14; -fx-border-color: #E8E4DC; -fx-border-radius: 12;");

            Label avatar = new Label(getInitials(patient.getName()));
            avatar.setStyle("-fx-background-color: #DDF0EB; -fx-text-fill: #127566; -fx-background-radius: 12; -fx-pref-width: 42; -fx-pref-height: 42; -fx-alignment: center; -fx-font-weight: bold;");

            VBox info = new VBox(2);
            HBox nameRow = new HBox(9);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(patient.getName());
            name.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1A1C20;");
            nameRow.getChildren().add(name);

            if (first) {
                Label nextBadge = new Label("NEXT");
                nextBadge.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #1F4D8F; -fx-background-radius: 5; -fx-padding: 2 7;");
                nameRow.getChildren().add(nextBadge);
                first = false;
            }

            Label classLabel = new Label(patient.getIllnessClass() != null ? patient.getIllnessClass() : "\u2014");
            classLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #8A8F94;");
            info.getChildren().addAll(nameRow, classLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            VBox rightSide = new VBox(5);
            rightSide.setAlignment(Pos.CENTER_RIGHT);
            Label statusPill = new Label("Pending");
            statusPill.getStyleClass().add("status-pending");
            rightSide.getChildren().add(statusPill);

            Button acceptBtn = new Button("Accept");
            acceptBtn.getStyleClass().add("btn-primary");
            acceptBtn.setStyle("-fx-font-size: 13; -fx-padding: 8 16 8 16;");
            acceptBtn.setOnAction(e -> acceptPatient(patient));

            row.getChildren().addAll(avatar, info, spacer, rightSide, acceptBtn);
            queueList.getChildren().add(row);
        }
    }

    private void acceptPatient(Patient patient) {
        Doctor doctor = SessionManager.getDoctor();

        PatientDAO patientDAO = new PatientDAO();
        Patient current = patientDAO.getInConsultForDoctor(doctor.getId());
        if (current != null) {
            // already consulting — show feedback
            topSubLabel.setText("Finish your current consultation first.");
            topSubLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #B14A33; -fx-font-weight: bold;");
            return;
        }

        patientDAO.updateStatus(patient.getId(), "in_consult");
        DoctorDAO doctorDAO = new DoctorDAO();
        doctorDAO.updateStatus(doctor.getId(), "busy");

        doctor = doctorDAO.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);
        loadScreen("doctor-chat.fxml");
    }

    @FXML
    private void setAvailable() {
        Doctor doctor = SessionManager.getDoctor();
        DoctorDAO dao = new DoctorDAO();
        dao.updateStatus(doctor.getId(), "available");
        doctor = dao.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);
        initialize();
    }

    @FXML
    private void setBusy() {
        Doctor doctor = SessionManager.getDoctor();
        DoctorDAO dao = new DoctorDAO();
        dao.updateStatus(doctor.getId(), "busy");
        doctor = dao.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);
        initialize();
    }

    @FXML
    private void setOnHold() {
        Doctor doctor = SessionManager.getDoctor();
        DoctorDAO dao = new DoctorDAO();
        dao.updateStatus(doctor.getId(), "on_hold");

        // re-queue pending patients to another available doctor of the same specialty
        PatientDAO patientDAO = new PatientDAO();
        List<Patient> pending = patientDAO.getPendingForDoctor(doctor.getId());
        for (Patient p : pending) {
            Doctor other = dao.findAvailableBySpecialty(doctor.getSpecialty());
            if (other != null && other.getId() != doctor.getId()) {
                patientDAO.assignDoctor(p.getId(), other.getId());
            }
        }

        doctor = dao.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);
        initialize();
    }

    @FXML
    private void onNextFromDash() {
        Doctor doctor = SessionManager.getDoctor();
        PatientDAO patientDAO = new PatientDAO();
        Patient current = patientDAO.getInConsultForDoctor(doctor.getId());
        if (current != null) {
            // only discharge if not already admitted or transferred
            String currentStatus = current.getStatus();
            if ("in_consult".equals(currentStatus)) {
                patientDAO.updateStatus(current.getId(), "discharged");
            }
        }
        DoctorDAO doctorDAO = new DoctorDAO();
        doctorDAO.updateStatus(doctor.getId(), "available");
        doctor = doctorDAO.getById(doctor.getId());
        SessionManager.loginAsDoctor(doctor);
        initialize();
    }

    @FXML
    private void goToTransfer() { loadScreen("doctor-transfer.fxml"); }

    @FXML
    private void goToConsultation() {
        loadScreen("doctor-chat.fxml");
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
            Stage stage = (Stage) queueList.getScene().getWindow();
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
