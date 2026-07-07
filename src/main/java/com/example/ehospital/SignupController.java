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

    // Fixed: Added <String> generic type for type-safe value retrieval
    @FXML private ComboBox<String> specialty;

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
            hospitalCombo.getItems().add(h.getName() + " — " + h.getLocation());
        }
        if (!hospitals.isEmpty()) {
            hospitalCombo.getSelectionModel().selectFirst();
        }

        // 2. Populate the Doctor Specialty Dropdown with categories from IllnessClassifier
        specialty.getItems().addAll(
                "Audiologist",
                "Andrologist",
                "Bariatrician",
                "Cardiologist",
                "Colorectal Surgeon",
                "Dentist",
                "Dermatologist",
                "Endocrinologist",
                "Fertility Specialist",
                "Gastroenterologist",
                "General Practitioner",
                "Geneticist",
                "Geriatrician",
                "Gynecologist",
                "Immunologist",
                "Nephrologist",
                "Neurologist",
                "Neurosurgeon",
                "Oncologist",
                "Ophthalmologist",
                "Orthopedic Surgeon",
                "Otolaryngologist",
                "Pain Specialist",
                "Pediatrician",
                "Physiatrist",
                "Plastic Surgeon",
                "Psychiatrist",
                "Pulmonologist",
                "Rheumatologist",
                "Sleep Specialist",
                "Sports Medicine Specialist",
                "Thoracic Surgeon",
                "Toxicologist",
                "Travel Medicine Specialist",
                "Vascular Surgeon"
        );

        if (!specialty.getItems().isEmpty()) {
            specialty.getSelectionModel().selectFirst();
        }

        // Automatically updates the specialty to "Dentist" while typing, before clicking sign up and locks the specialty dropdown so the user cannot change it manually
        licenseField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && selectedRole.equals("doctor")) {
                String validatedCategory = determineCategoryFromLicense(newValue);
                if ("Dentist".equals(validatedCategory)) {
                    specialty.getSelectionModel().select("Dentist");
                    specialty.setDisable(true); // Lock the field once set to Dentist
                } else {
                    specialty.setDisable(false); // Unlock if prefix code changes to something else
                }
            }
        });
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

        // Check if current text already triggers a Dentist block on switch
        String currentLicense = licenseField.getText();
        if ("Dentist".equals(determineCategoryFromLicense(currentLicense))) {
            specialty.getSelectionModel().select("Dentist");
            specialty.setDisable(true);
        } else {
            specialty.setDisable(false);
        }
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

        // --- NEW STRICT EMAIL VALIDATION ---
        if (containsUppercase(email)) {
            messageLabel.setText("Email addresses must be inputted strictly using lowercase characters.");
            messageLabel.getStyleClass().setAll("error-label");
            return;
        }

        if (!isStrictlyValidEmail(email)) {
            messageLabel.setText("The email configuration does not conform to registration guidelines.");
            messageLabel.getStyleClass().setAll("error-label");
            return;
        }

        boolean success = false;

        if (selectedRole.equals("patient")) {
            PatientDAO dao = new PatientDAO();
            success = dao.register(name, email, password);

        } else if (selectedRole.equals("doctor")){
            String license = licenseField.getText();

            if (license.isEmpty()) {
                messageLabel.setText("Please enter specialty and license number.");
                messageLabel.getStyleClass().setAll("error-label");
                return;
            }

            // --- NEW LICENSE VALIDATION ---
            String validatedCategory = determineCategoryFromLicense(license);
            if (validatedCategory == null) {
                messageLabel.setText("License format unrecognized. Must match standard MDCN prefix formats.");
                messageLabel.getStyleClass().setAll("error-label");
                return;
            }

            String selectedSpecialty = (specialty.getValue() != null) ? specialty.getValue() : "";
            if (selectedSpecialty.isEmpty()) {
                messageLabel.setText("Please select a specialty.");
                messageLabel.getStyleClass().setAll("error-label");
                return;
            }

            DoctorDAO dao = new DoctorDAO();
            success = dao.register(name, email, password, selectedSpecialty, license);

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
            messageLabel.setText("Email already exists or registration failed.");
        }
    }

    @FXML
    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Stage stage = (Stage) nameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 1500, 900));
        } catch (Exception e) {
            System.out.println("Could not load screen: " + e.getMessage());
        }
    }

    // ==========================================================
    // VALIDATION HELPER METHODS
    // ==========================================================

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

    private String determineCategoryFromLicense(String license) {
        if (license == null) return null;
        String input = license.trim().toUpperCase();
        if (!input.startsWith("MDCN/")) return null;
        if (!input.matches("^MDCN/([A-Z]{1,3}/?)?\\d{5,7}$")) return null;

        String remaining = input.substring(5);
        String prefixCode = remaining.contains("/") ? remaining.split("/")[0] : remaining.replaceAll("\\d", "");

        switch (prefixCode) {
            case "D": case "DT": case "DEN": case "PD": case "FMD": return "Dentist";
            case "C": case "CON": case "CNS": return "Consultant";
            case "R": case "RES": case "REG": return "Resident";
            case "PM": return "Provisional Medical";
            case "T": case "TEMP": return "Temporary Medical Practitioner";
            case "M": case "MED": case "FMM": return "Medical Practitioner";
            case "": return "General Practitioner";
            default: return null;
        }
    }
}
