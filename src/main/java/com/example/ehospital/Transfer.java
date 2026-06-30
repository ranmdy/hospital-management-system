package com.example.ehospital;

import java.sql.Timestamp;

public class Transfer {
    private int id;
    private int patientId;
    private int doctorId;
    private int fromHospitalId;
    private int toHospitalId;
    private String urgency;
    private String clinicalNote;
    private boolean fileSent;
    private boolean fileRequested;
    private boolean fileApproved;
    private String status;
    private String declineReason;
    private Timestamp createdAt;

    public Transfer() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public int getFromHospitalId() { return fromHospitalId; }
    public void setFromHospitalId(int fromHospitalId) { this.fromHospitalId = fromHospitalId; }

    public int getToHospitalId() { return toHospitalId; }
    public void setToHospitalId(int toHospitalId) { this.toHospitalId = toHospitalId; }

    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }

    public String getClinicalNote() { return clinicalNote; }
    public void setClinicalNote(String clinicalNote) { this.clinicalNote = clinicalNote; }

    public boolean isFileSent() { return fileSent; }
    public void setFileSent(boolean fileSent) { this.fileSent = fileSent; }

    public boolean isFileRequested() { return fileRequested; }
    public void setFileRequested(boolean fileRequested) { this.fileRequested = fileRequested; }

    public boolean isFileApproved() { return fileApproved; }
    public void setFileApproved(boolean fileApproved) { this.fileApproved = fileApproved; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
