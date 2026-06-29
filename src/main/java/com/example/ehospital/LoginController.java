package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private VBox patientCard;
    @FXML private VBox doctorCard;
    @FXML private VBox adminCard;
    @FXML private Label messageLabel;

    private String selectedRole = "patient";

    @FXML
    private void selectPatient() {
        selectedRole = "patient";
        patientCard.getStyleClass().setAll("role-card-active");
        doctorCard.getStyleClass().setAll("role-card");
        adminCard.getStyleClass().setAll("role-card");
    }

    @FXML
    private void selectDoctor() {
        selectedRole = "doctor";
        patientCard.getStyleClass().setAll("role-card");
        doctorCard.getStyleClass().setAll("role-card-active");
        adminCard.getStyleClass().setAll("role-card");
    }

    @FXML
    private void selectAdmin() {
        selectedRole = "admin";
        patientCard.getStyleClass().setAll("role-card");
        doctorCard.getStyleClass().setAll("role-card");
        adminCard.getStyleClass().setAll("role-card-active");
    }

    @FXML
    private void onLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please fill in all fields.");
            return;
        }

        if (selectedRole.equals("patient")) {
            PatientDAO dao = new PatientDAO();
            Patient patient = dao.login(email, password);
            if (patient != null) {
                SessionManager.loginAsPatient(patient);
                loadScreen("patient-dashboard.fxml");
            } else {
                messageLabel.setText("Wrong email or password.");
            }
        } else if (selectedRole.equals("doctor")) {
            DoctorDAO dao = new DoctorDAO();
            Doctor doctor = dao.login(email, password);
            if (doctor != null) {
                SessionManager.loginAsDoctor(doctor);
                loadScreen("doctor-dashboard.fxml");
            } else {
                messageLabel.setText("Wrong email or password.");
            }
        } else if (selectedRole.equals("admin")) {
            HospitalAdminDAO dao = new HospitalAdminDAO();
            HospitalAdmin admin = dao.login(email, password);
            if (admin != null) {
                SessionManager.loginAsAdmin(admin);
                loadScreen("admin-dashboard.fxml");
            } else {
                messageLabel.setText("Wrong email or password.");
            }
        }
    }

    @FXML
    private void goToSignup() {
        loadScreen("signup-view.fxml");
    }

    private void loadScreen(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 900, 600));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }
}
