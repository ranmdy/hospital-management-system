package com.example.ehospital;

public class IllnessClassifier {

    public static String classify(String symptoms) {
        String lower = symptoms.toLowerCase();

        if (lower.contains("cough") || lower.contains("flu") || lower.contains("breathing")
                || lower.contains("asthma") || lower.contains("lung") || lower.contains("pneumonia")) {
            return "Respiratory";
        }
        if (lower.contains("chest pain") || lower.contains("heart") || lower.contains("palpitation")
                || lower.contains("blood pressure") || lower.contains("cardiac")) {
            return "Cardiac";
        }
        if (lower.contains("rash") || lower.contains("acne") || lower.contains("itch")
                || lower.contains("skin") || lower.contains("allergy") || lower.contains("hives")) {
            return "Skin";
        }
        if (lower.contains("cold") || lower.contains("headache") || lower.contains("fatigue")
                || lower.contains("nausea") || lower.contains("fever") || lower.contains("stomach")
                || lower.contains("diarrhea") || lower.contains("vomit")) {
            return "General";
        }
        return "Other";
    }

    public static String getSpecialty(String illnessClass) {
        switch (illnessClass) {
            case "Respiratory": return "Pulmonologist";
            case "Cardiac": return "Cardiologist";
            case "Skin": return "Dermatologist";
            case "General": return "General";
            default: return "General";
        }
    }
}
