package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class SignupController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField specialtyField;
    @FXML private TextField licenseField;
    @FXML private VBox specialtyBox;
    @FXML private VBox licenseBox;
    @FXML private VBox hospitalBox;
    @FXML private ComboBox<String> hospitalCombo;
    @FXML private VBox patientCard;
    @FXML private VBox doctorCard;
    @FXML private VBox adminCard;
    @FXML private Label messageLabel;

    private String selectedRole = "patient";
    private List<Hospital> hospitals;

    @FXML
    public void initialize() {
        HospitalDAO hospitalDAO = new HospitalDAO();
        hospitals = hospitalDAO.getAll();
        for (Hospital h : hospitals) {
            hospitalCombo.getItems().add(h.getName() + " \u2014 " + h.getLocation());
        }
        if (!hospitals.isEmpty()) {
            hospitalCombo.getSelectionModel().selectFirst();
        }
    }

    @FXML
    private void selectPatient() {
        selectedRole = "patient";
        patientCard.getStyleClass().setAll("role-card-active");
        doctorCard.getStyleClass().setAll("role-card");
        adminCard.getStyleClass().setAll("role-card");
        specialtyBox.setVisible(false);
        specialtyBox.setManaged(false);
        licenseBox.setVisible(false);
        licenseBox.setManaged(false);
        hospitalBox.setVisible(false);
        hospitalBox.setManaged(false);
    }

    @FXML
    private void selectDoctor() {
        selectedRole = "doctor";
        patientCard.getStyleClass().setAll("role-card");
        doctorCard.getStyleClass().setAll("role-card-active");
        adminCard.getStyleClass().setAll("role-card");
        specialtyBox.setVisible(true);
        specialtyBox.setManaged(true);
        licenseBox.setVisible(true);
        licenseBox.setManaged(true);
        hospitalBox.setVisible(false);
        hospitalBox.setManaged(false);
    }

    @FXML
    private void selectAdmin() {
        selectedRole = "admin";
        patientCard.getStyleClass().setAll("role-card");
        doctorCard.getStyleClass().setAll("role-card");
        adminCard.getStyleClass().setAll("role-card-active");
        specialtyBox.setVisible(false);
        specialtyBox.setManaged(false);
        licenseBox.setVisible(false);
        licenseBox.setManaged(false);
        hospitalBox.setVisible(true);
        hospitalBox.setManaged(true);
    }

    @FXML
    private void onSignup() {
        String name = nameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please fill in all fields.");
            messageLabel.getStyleClass().setAll("error-label");
            return;
        }

        boolean success = false;

        if (selectedRole.equals("patient")) {
            PatientDAO dao = new PatientDAO();
            success = dao.register(name, email, password);
        } else if (selectedRole.equals("doctor")) {
            String specialty = specialtyField.getText();
            String license = licenseField.getText();
            if (specialty.isEmpty() || license.isEmpty()) {
                messageLabel.setText("Please enter specialty and license number.");
                messageLabel.getStyleClass().setAll("error-label");
                return;
            }
            DoctorDAO dao = new DoctorDAO();
            success = dao.register(name, email, password, specialty, license);
        } else if (selectedRole.equals("admin")) {
            int selectedIndex = hospitalCombo.getSelectionModel().getSelectedIndex();
            if (selectedIndex < 0) {
                messageLabel.setText("Please select a hospital.");
                messageLabel.getStyleClass().setAll("error-label");
                return;
            }
            int hospitalId = hospitals.get(selectedIndex).getId();
            HospitalAdminDAO dao = new HospitalAdminDAO();
            success = dao.register(name, email, password, hospitalId);
        }

        if (success) {
            messageLabel.getStyleClass().setAll("success-label");
            messageLabel.setText("Account created! You can now login.");
        } else {
            messageLabel.getStyleClass().setAll("error-label");
            messageLabel.setText("Email already exists.");
        }
    }

    @FXML
    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Stage stage = (Stage) nameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 900, 600));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }
}
