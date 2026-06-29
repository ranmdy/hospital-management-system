package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private RadioButton patientRadio;
    @FXML private RadioButton doctorRadio;
    @FXML private RadioButton adminRadio;
    @FXML private Label messageLabel;

    @FXML
    private void onLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please fill in all fields.");
            return;
        }

        if (patientRadio.isSelected()) {
            PatientDAO dao = new PatientDAO();
            Patient patient = dao.login(email, password);
            if (patient != null) {
                SessionManager.loginAsPatient(patient);
                loadScreen("patient-dashboard.fxml");
            } else {
                messageLabel.setText("Wrong email or password.");
            }
        } else if (doctorRadio.isSelected()) {
            DoctorDAO dao = new DoctorDAO();
            Doctor doctor = dao.login(email, password);
            if (doctor != null) {
                SessionManager.loginAsDoctor(doctor);
                loadScreen("doctor-dashboard.fxml");
            } else {
                messageLabel.setText("Wrong email or password.");
            }
        } else if (adminRadio.isSelected()) {
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
            stage.setScene(new Scene(loader.load(), 400, 500));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }
}
