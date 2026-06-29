package com.example.ehospital;

public class Patient {
    private int id;
    private String name;
    private String email;
    private String password;
    private String symptoms;
    private String illnessClass;
    private String status;
    private int assignedDoctorId;

    public Patient() {}

    public Patient(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }

    public String getIllnessClass() { return illnessClass; }
    public void setIllnessClass(String illnessClass) { this.illnessClass = illnessClass; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getAssignedDoctorId() { return assignedDoctorId; }
    public void setAssignedDoctorId(int assignedDoctorId) { this.assignedDoctorId = assignedDoctorId; }
}
