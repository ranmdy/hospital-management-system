package com.example.ehospital;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class DoctorDashboardController {

    @FXML private Label welcomeLabel;

    @FXML
    public void initialize() {
        Doctor doctor = SessionManager.getDoctor();
        if (doctor != null) {
            welcomeLabel.setText("Welcome, Dr. " + doctor.getName() + "!");
        }
    }

    @FXML
    private void onLogout() {
        SessionManager.logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 500));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }
}
