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
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            messageLabel.getStyleClass().setAll("error-label");
            messageLabel.setText("Please fill in all fields.");
            return;
        }

        if (containsUppercase(email)) {
            messageLabel.getStyleClass().setAll("error-label");
            messageLabel.setText("Email addresses must be inputted strictly using lowercase characters.");
            return;
        }

        if (!isStrictlyValidEmail(email)) {
            messageLabel.getStyleClass().setAll("error-label");
            messageLabel.setText("Please enter a valid email address.");
            return;
        }

        if (selectedRole.equals("patient")) {
            PatientDAO dao = new PatientDAO();
            Patient patient = dao.login(email, password);
            if (patient != null) {
                SessionManager.loginAsPatient(patient);
                loadScreen("patient-dashboard.fxml");
            } else {
                messageLabel.getStyleClass().setAll("error-label");
                messageLabel.setText("Wrong email or password.");
            }
        } else if (selectedRole.equals("doctor")) {
            DoctorDAO dao = new DoctorDAO();
            Doctor doctor = dao.login(email, password);
            if (doctor != null) {
                SessionManager.loginAsDoctor(doctor);
                loadScreen("doctor-dashboard.fxml");
            } else {
                messageLabel.getStyleClass().setAll("error-label");
                messageLabel.setText("Wrong email or password.");
            }
        } else if (selectedRole.equals("admin")) {
            HospitalAdminDAO dao = new HospitalAdminDAO();
            HospitalAdmin admin = dao.login(email, password);
            if (admin != null) {
                SessionManager.loginAsAdmin(admin);
                loadScreen("admin-dashboard.fxml");
            } else {
                messageLabel.getStyleClass().setAll("error-label");
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
            stage.setScene(new Scene(loader.load(), 1500, 900));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }

    private boolean containsUppercase(String email) {
        if (email == null) return false;
        for (char c : email.toCharArray()) {
            if (Character.isUpperCase(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStrictlyValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        if (email.length() > 320) return false;

        int atCount = 0;
        for (char c : email.toCharArray()) {
            if (c == '@') atCount++;
        }
        if (atCount != 1) return false;

        String[] parts = email.split("@");
        if (parts.length != 2) return false;

        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() < 1 || localPart.length() > 64) return false;
        if (domain.length() < 3 || domain.length() > 255) return false;
        if (localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) return false;

        if (!localPart.matches("^[a-z0-9!#$%&'*+\\-/=?^_`{|}~.]+$")) return false;
        if (domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) return false;

        String[] labels = domain.split("\\.");
        if (labels.length < 2) return false;

        for (int i = 0; i < labels.length - 1; i++) {
            String label = labels[i];
            if (label.length() < 1 || label.length() > 63) return false;
            if (label.startsWith("-") || label.endsWith("-")) return false;
            if (!label.matches("^[a-z0-9-]+$")) return false;
        }

        String tld = labels[labels.length - 1];
        if (tld.length() < 2 || tld.length() > 63) return false;
        if (!tld.matches("^[a-z]+$")) return false;

        return true;
    }
}
