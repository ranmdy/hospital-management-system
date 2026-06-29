package com.example.ehospital;

public class Doctor {
    private int id;
    private String name;
    private String email;
    private String password;
    private String specialty;
    private String licenseNumber;
    private String status;

    public Doctor() {}

    public Doctor(String name, String email, String password, String specialty, String licenseNumber) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.specialty = specialty;
        this.licenseNumber = licenseNumber;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
