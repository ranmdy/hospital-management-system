package com.example.ehospital;

public class SessionManager {

    private static Patient loggedInPatient;
    private static Doctor loggedInDoctor;
    private static HospitalAdmin loggedInAdmin;
    private static String role; // "patient", "doctor", or "admin"

    public static void loginAsPatient(Patient patient) {
        loggedInPatient = patient;
        loggedInDoctor = null;
        loggedInAdmin = null;
        role = "patient";
    }

    public static void loginAsDoctor(Doctor doctor) {
        loggedInDoctor = doctor;
        loggedInPatient = null;
        loggedInAdmin = null;
        role = "doctor";
    }

    public static void loginAsAdmin(HospitalAdmin admin) {
        loggedInAdmin = admin;
        loggedInPatient = null;
        loggedInDoctor = null;
        role = "admin";
    }

    public static Patient getPatient() { return loggedInPatient; }
    public static Doctor getDoctor() { return loggedInDoctor; }
    public static HospitalAdmin getAdmin() { return loggedInAdmin; }
    public static String getRole() { return role; }

    public static void logout() {
        loggedInPatient = null;
        loggedInDoctor = null;
        loggedInAdmin = null;
        role = null;
    }
}
