package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class SignupController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField specialtyField;
    @FXML private TextField licenseField;
    @FXML private RadioButton patientRadio;
    @FXML private RadioButton doctorRadio;
    @FXML private RadioButton adminRadio;
    @FXML private Label messageLabel;

    @FXML
    private void onRoleSwitch() {
        boolean isDoctor = doctorRadio.isSelected();
        specialtyField.setVisible(isDoctor);
        specialtyField.setManaged(isDoctor);
        licenseField.setVisible(isDoctor);
        licenseField.setManaged(isDoctor);
    }

    @FXML
    private void onSignup() {
        String name = nameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please fill in all fields.");
            return;
        }

        boolean success = false;

        if (patientRadio.isSelected()) {
            PatientDAO dao = new PatientDAO();
            success = dao.register(name, email, password);
        } else if (doctorRadio.isSelected()) {
            String specialty = specialtyField.getText();
            String license = licenseField.getText();
            if (specialty.isEmpty() || license.isEmpty()) {
                messageLabel.setText("Please enter specialty and license number.");
                return;
            }
            DoctorDAO dao = new DoctorDAO();
            success = dao.register(name, email, password, specialty, license);
        } else if (adminRadio.isSelected()) {
            HospitalAdminDAO dao = new HospitalAdminDAO();
            success = dao.register(name, email, password);
        }

        if (success) {
            messageLabel.setStyle("-fx-text-fill: green;");
            messageLabel.setText("Account created! You can now login.");
        } else {
            messageLabel.setStyle("-fx-text-fill: red;");
            messageLabel.setText("Email already exists.");
        }
    }

    @FXML
    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Stage stage = (Stage) nameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 500));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }
}
