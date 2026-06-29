
# E-HealthCare Management

This is our plan for building a virtual doctor app. The idea is simple. A patient describes their symptoms, we match them to a doctor, they chat, and the patient either gets a prescription or gets admitted to a hospital bed or get transfared to another hospital (hospital)...{under fixing from UI}.

We are building it with JavaFX for the screens, JDBC as the connector, and MySQL through XAMPP for the database.

## How we are tackling this

We are building it from the bottom up. We finish each chunk before moving to the next one, since each part needs the one before it to work.

We have set ourselves two checkpoints along the way. After Chunk 2 we will have something we can actually show. After Chunk 5 we will have a full working app. Chunk 6 with the beds is our bonus, and it is what will make the project stand out.

We tick the boxes off as we finish them.

## Chunk 0: Setup

First we get the foundation running before touching any features.

We install XAMPP and start MySQL, then create our ehealthcare database and run schema.sql to build the tables. After that we set up the JavaFX project in IntelliJ, add the MySQL JDBC connector, and write the DatabaseConnection class to test it.

We are done when Java can connect to MySQL and print a row.

## Chunk 1: Login and Signup

This is the way into everything else, so it comes next.

We build the login and signup screen with a role toggle for patient or doctor. Then we create our Patient and Doctor models, build their DAOs for register and login, and add a SessionManager to keep track of who is logged in.

We are done when a patient and a doctor can each register, log in, and land on a blank dashboard.

## Chunk 2: Patient enters symptoms

Now we build the patient's main path.

We build the patient dashboard and the symptom intake form, then write the IllnessClassifier that turns symptoms into a type of doctor.

We are done when a patient submits symptoms and the app sorts them into a category. At this point we have something we can demo.

## Chunk 3: Doctor gets the patient (the queue)

This is the heart of our system, so we want to get it right.

We add a status column to our patients and doctors tables, then build the doctor dashboard with a pending patient queue. We add the accept, consult, and next or hold controls, and make sure a doctor only handles one patient at a time.

We are done when a patient who submits symptoms shows up in the right doctor's queue, and the doctor can accept them one at a time.

## Chunk 4: Chat

Next we let the doctor and patient actually talk.

We create the messages table and build the ChatDAO to save and load messages. Then we build the chat screen for both sides and make new messages show up automatically by refreshing every couple of seconds.

We are done when the doctor and patient can message back and forth live.

## Chunk 5: Prescription

Then we handle what comes out of the consultation.

We build the prescription form with medicine, dosage, and notes, then add the doctor's choice to either prescribe drugs or admit the patient. Finally we build the prescription view on the patient's side.

We are done when a doctor can wrap up a consult by sending a prescription the patient can see. At this point we have a full working app.

## Chunk 6: Beds and admission

This is our bonus chunk, the part we think will really set the project apart.

We build the Hospital model and DAO, then check bed availability when admitting. We pair a free bed and the attending doctor to the patient, and if there are no free beds we fall back to a prescription instead. We also build the hospital dashboard showing occupied versus free beds.

We are done when admitting a severe patient takes a bed, and a full hospital falls back to medicine.

## Chunk 7: Final polish

We are saving the trickiest bits for last.

We re-queue patients to another doctor when one goes on hold, build the discharge flow that frees the bed, and show status indicators everywhere. We also add input validation and error handling, and style everything to match our design.

We are done when the app handles the edge cases cleanly and looks finished.

## How our statuses work

Each patient moves through these states. They start as pending, then move to in consult, then to either prescribed or admitted, and finally to discharged.

Each doctor moves through available, then busy, then on hold, and back to available.

## How we are working together

We are sorting this out in Chunk 0 so we do not trip over each other later.

One of us creates a private GitHub repo and adds the rest of the team as collaborators, then we all clone it. Our rule is to pull before we start and push when we finish.

We are also hosting one MySQL database online so all our apps use the same data. We need this because a doctor on one laptop has to see a patient who registered on another.

Finally we divide the work so no two of us are editing the same file at the same time. These chunks make that easy to split.

## Our database tables

The doctors table holds doctor accounts, specialty, and status. The patients table holds patient accounts, symptoms, illness class, and status. The hospitals table holds bed counts for total and available. The prescriptions table holds medicine and dosage per patient. The messages table holds the chat between doctor and patient.
