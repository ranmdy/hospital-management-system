# E-HealthCare Management — Our Project Roadmap

This is our plan for building Ranmdy Healthcare Center, a virtual doctor platform built in JavaFX with MySQL through XAMPP. A patient describes their symptoms, gets matched to a doctor, they chat, and the patient either gets a prescription, gets admitted to a bed, or gets transferred to a partner hospital.

After reviewing the final frontend design, a few things changed from our original plan. We added a hospital transfer feature, a patient file screen, and updated how the doctor queue and admission work. Everything else stays the same.

## What changed from the original plan

We now have three user roles instead of two. Doctor and patient stay the same but we added a hospital admin role that handles incoming transfer requests from doctors. The transfer feature is brand new. The doctor can refer a patient to a partner hospital, set an urgency level, write a clinical note, and choose whether to send the patient file now or let the hospital request it. The hospital admin can then accept or decline the transfer, request the file if it was not sent, and confirm when the patient arrives. The patient file screen is also new. It shows the full clinical record including demographics, vitals, symptoms, classification, prescriptions, admission history, and the full chat log.

## Chunk 0: Setup

We install XAMPP and start MySQL, create the ehealthcare database, and run schema.sql to build the tables. We set up the JavaFX project in IntelliJ and add the MySQL JDBC connector. We also set up the GitHub repo so the whole team can pull and push.

We are done when Java can connect to MySQL and print a row.

## Chunk 1: Login and Signup

We build the login and signup screen with a role toggle for patient, doctor, and hospital admin. We create our Patient, Doctor, and Hospital models and their DAOs. We add a SessionManager to track who is logged in. Patient lands on the patient dashboard. Doctor lands on the doctor dashboard. Hospital admin lands on the transfer requests screen.

We are done when all three roles can register, log in, and reach their correct screen.

## Chunk 2: Patient enters symptoms

We build the patient dashboard and the symptom intake form. We write the IllnessClassifier that reads the symptom text and maps it to an illness class and a doctor specialty. The result shows as a chip before the patient submits.

We are done when a patient submits symptoms, the illness gets classified, and the entry is saved to the database.

> At this point we have something we can demo.

## Chunk 3: Doctor queue and accept flow

This is the heart of the system. We add a status column to both the patients and doctors tables. Patient statuses are PENDING, IN_CONSULT, PRESCRIBED, ADMITTED, and DISCHARGED. Doctor statuses are AVAILABLE, BUSY, and ON_HOLD. A doctor handles one patient at a time. When a doctor goes on hold, pending patients re-queue to another available doctor of the same specialty if one exists. If none exists they stay pending.

We are done when a patient shows up in the right doctor queue and the doctor can accept them one at a time.

## Chunk 4: Chat

We create the messages table and build the ChatDAO to save and load messages. We build the chat screen for both sides. New messages appear automatically by polling every 2 seconds. The prescription panel appears on the right side of the patient chat once the doctor issues one.

We are done when doctor and patient can message each other live.

## Chunk 5: Prescription

We build the prescription form with medicine, dosage, and notes. The doctor chooses either prescribe drugs for a mild case or admit to hospital for a severe case. When a prescription is issued it saves to the database and appears immediately on the patient side. The doctor then picks next patient or goes on hold.

We are done when a doctor can close a consult by issuing a prescription the patient can see.

> At this point we have a full working app.

## Chunk 6: Beds and admission

We build the Hospital model and DAO. When the doctor clicks admit we check available beds. We show a grid of free and occupied beds. The doctor taps a free bed to pair it with the patient and the attending doctor. That decrements available beds and sets the patient to ADMITTED. If no beds are free the screen shows a fallback and offers to prescribe instead.

We are done when admitting a patient takes a bed, and a full hospital falls back to a prescription.

## Chunk 7: Hospital transfer (new)

This is the new chunk from the updated design. The doctor fills a transfer form with a destination hospital, Lagos LGA branch, urgency level (Routine, Urgent, Emergency), a clinical note, and a choice to either send the patient file now or let the hospital request it later.

The request lands in the hospital admin inbox tagged NEW. The admin can accept, decline, or request the file if it was not sent. If they request the file it notifies the doctor who can approve or deny sharing. When the admin accepts, a bed is reserved at the receiving hospital. When the patient arrives the admin confirms it and the status updates for the doctor and patient.

We are done when the full transfer loop works from doctor submission to hospital arrival confirmation.

## Chunk 8: Patient file screen (new)

The patient file is a full clinical record that can be opened two ways. The doctor opens it directly from the sidebar. The hospital admin opens it after the doctor shares it as part of a transfer. The file shows demographics, vitals, symptoms and illness classification, prescriptions, admission history, and the full consultation chat log.

We are done when the file renders correctly for both the doctor view and the shared transfer view.

## Chunk 9: Final polish

We handle the trickiest edge cases and clean everything up. We complete the re-queue logic when a doctor goes on hold. We build the discharge flow that frees the bed. We add input validation and error handling throughout. We make sure the status lifecycle indicators show correctly everywhere across all three user views.

We are done when the whole system runs clean end to end with no broken flows.

## Our database tables

The doctors table holds doctor accounts, specialty, license number, and status. The patients table holds patient accounts, symptoms, illness class, and status. The hospitals table holds bed counts for total and available. The prescriptions table holds medicine and dosage per patient. The messages table holds the full chat between doctor and patient. The transfers table, which is new, holds all transfer requests with urgency, reason, file sharing status, and the current state of the request.

## How we are working together

One of us creates a private GitHub repo and adds the team as collaborators. We all clone it. The rule is pull before we start and push when we finish, and nobody merges their own pull request. We are also hosting one MySQL database online so all our apps connect to the same data. We split work by chunk so no two of us touch the same files at the same time.
