package com.example.ehospital;

public class IllnessClassifier {

    public static String classify(String symptoms) {
        String lower = symptoms.toLowerCase();
        // Neurologist: Migraine / Neurological issues
        if (lower.contains("flashing lights") || lower.contains("blind spot") || lower.contains("throbbing headache") || lower.contains("one side of the head")) {
            return "Neurological / Migraine";
        }

// Cardiologist: Heart Attack / Cardiac issues
        if (lower.contains("chest pain") || lower.contains("elephant sitting on chest") || lower.contains("left arm") || lower.contains("heart attack")) {
            return "Cardiac";
        }

// Pulmonologist / Sleep Specialist: Asthma, COPD, and Advanced Airway Disorders
        if (lower.contains("whistling") || lower.contains("wheezing") || lower.contains("breathe out") || lower.contains("shortness of breath") || lower.contains("waking up choking") || lower.contains("gasping for air") || lower.contains("lips turning blue") || lower.contains("extreme snoring")) {
            return "Pulmonology";
        }

// Gastroenterologist: GI Bleed, Stomach issues, and Inflammatory Bowel Disease (IBD)
        if (lower.contains("throwing up blood") || lower.contains("pitch-black") || lower.contains("tarry") || lower.contains("poop") || lower.contains("bloody diarrhea") || lower.contains("crohn's") || lower.contains("ulcerative colitis") || lower.contains("severe cramping after eating")) {
            return "Gastrointestinal / IBD";
        }

// Endocrinologist: Graves' disease, Thyroid, Diabetes, Bone Mineral Density, and Parathyroid
        if (lower.contains("bulging") || lower.contains("popping out") || lower.contains("swollen neck") || lower.contains("thyroid") || lower.contains("osteoporosis") || lower.contains("bones breaking easily") || lower.contains("high calcium in the blood") || lower.contains("parathyroid tumor")) {
            return "Endocrinology / Bone Metabolism";
        }

// Rheumatologist: Lupus, Joint issues, and Crystal-Induced Arthropathies
        if (lower.contains("sunburn-like rash") || lower.contains("butterfly") || lower.contains("across both cheeks") || lower.contains("bridge of the nose") || lower.contains("sudden severe pain in the big toe") || lower.contains("gout attack") || lower.contains("uric acid crystals") || lower.contains("hot swollen toe joint")) {
            return "Rheumatology / Gout / Lupus";
        }

// Dermatologist: Skin Cancer, Melanoma, Hair Loss, and Scalp Disorders
        if (lower.contains("mole") || lower.contains("skin spot") || lower.contains("jagged borders") || lower.contains("changes color") || lower.contains("bald patches appearing suddenly") || lower.contains("severe dandruff with scaling") || lower.contains("hair thinning rapidly") || lower.contains("scalp psoriasis")) {
            return "Dermatology / Trichology";
        }

// Urologist: Bladder / Kidney / Urinary issues
        if (lower.contains("blood in your urine") || lower.contains("blood in your pee") || lower.contains("dark red urine") || lower.contains("bright red urine")) {
            return "Urology";
        }

// Gynecologist: Postmenopausal, Uterine issues, Urogynecology, and Pelvic Floor Issues
        if (lower.contains("bleeding from the vagina") || lower.contains("months after menopause") || lower.contains("years after menopause") || lower.contains("leaking urine when coughing") || lower.contains("leaking urine when sneezing") || lower.contains("pelvic organ prolapse") || lower.contains("feeling a bulge in the vagina")) {
            return "Gynecology / Urogynecology";
        }

// Hematologist / Oncologist: Lymphoma, Blood issues, Clotting Disorders, Bone and Soft Tissue Tumors
        if (lower.contains("lumps in the neck") || lower.contains("armpits") || lower.contains("groin") || lower.contains("don't hurt") || lower.contains("lymph nodes") || lower.contains("blood clot in the leg") || lower.contains("deep vein thrombosis") || lower.contains("dvt") || lower.contains("pulmonary embolism") || lower.contains("frequent miscarriages due to clotting") || lower.contains("deep bone pain at night") || lower.contains("rapidly growing lump in the muscle") || lower.contains("sarcoma") || lower.contains("unexplained bone fracture")) {
            return "Hematology / Oncology / Coagulation";
        }

// Allergist / Immunologist: Anaphylaxis, Severe Allergies, Contact Dermatitis, and Primary Immunodeficiency
        if (lower.contains("throat swelling") || lower.contains("trouble breathing") || lower.contains("hives all over") || lower.contains("anaphylaxis") || lower.contains("four or more ear infections in a year") || lower.contains("two or more serious sinus infections") || lower.contains("persistent thrush") || lower.contains("need for intravenous antibiotics") || lower.contains("rash from wearing jewelry") || lower.contains("poison ivy rash") || lower.contains("latex allergy") || lower.contains("contact dermatitis")) {
            return "Allergy / Immunology";
        }

// Psychiatrist: Psychotic disorders, OCD, and Mental Health
        if (lower.contains("hearing voices") || lower.contains("seeing things") || lower.contains("aren't actually there") || lower.contains("hallucinations") || lower.contains("repeatedly checking the locks") || lower.contains("excessive hand washing") || lower.contains("intrusive unwanted thoughts") || lower.contains("ocd")) {
            return "Psychiatry / OCD";
        }

// Otolaryngologist: ENT, Meniere's disease, Neurotology, and Balance Surgery
        if (lower.contains("ringing in the ears") || lower.contains("buzzing in the ears") || lower.contains("dizziness that makes the whole room spin") || lower.contains("vertigo") || lower.contains("fluid leaking from the ear") || lower.contains("permanent hearing loss in one ear") || lower.contains("benign paroxysmal positional vertigo") || lower.contains("bppv")) {
            return "Otolaryngology / Neurotology";
        }

// Nephrologist: Kidney Failure, Renal issues, and Advanced Kidney Electrolyte Issues
        if (lower.contains("hardly peeing") || lower.contains("swelling in the ankles") || lower.contains("swelling in the legs") || lower.contains("metallic taste") || lower.contains("dangerously high potassium") || lower.contains("severe sodium imbalance") || lower.contains("chronic fluid overload") || lower.contains("kidney stones recurring constantly")) {
            return "Nephrology / Kidney";
        }

// Ophthalmologist: Detached Retina, Eye issues, Cornea, and Anterior Segment Issues
        if (lower.contains("blurry vision") || lower.contains("dark curtain") || lower.contains("shadow falling across your eye") || lower.contains("detached retina") || lower.contains("scratch on the eyeball") || lower.contains("corneal ulcer") || lower.contains("severe dry eye that burns") || lower.contains("cloudy cornea")) {
            return "Ophthalmology / Cornea";
        }

// Infectious Disease Specialist: Lyme Disease and Advanced Infections
        if (lower.contains("target rash") || lower.contains("bullseye") || lower.contains("tick bite") || lower.contains("lyme disease")) {
            return "Infectious Disease";
        }

// Hepatologist: Liver, Gallbladder, Pancreas, and Biliary Tree issues
        if (lower.contains("yellowing of the skin") || lower.contains("yellow eyes") || lower.contains("jaundice") || lower.contains("pale stools") || lower.contains("dark urine") || lower.contains("pain after eating fatty foods") || lower.contains("gallbladder attack") || lower.contains("biliary colic") || lower.contains("biliary tract infection")) {
            return "Hepatology / Liver / Biliary Medicine";
        }

// Podiatrist: Foot and Ankle Disorders
        if (lower.contains("heel pain") || lower.contains("pain when walking") || lower.contains("ingrown toenail") || lower.contains("diabetic foot ulcer")) {
            return "Podiatry / Foot Care";
        }

// Geriatrician: Aging and Elderly Care
        if (lower.contains("frequent falls") || lower.contains("sudden confusion") || lower.contains("delirium") || lower.contains("too many medications") || lower.contains("polypharmacy")) {
            return "Geriatrics / Senior Care";
        }

// Physiatrist: Physical Mobility and Rehabilitation
        if (lower.contains("loss of physical function") || lower.contains("muscle spasticity") || lower.contains("recovering physical function") || lower.contains("stroke recovery")) {
            return "Physical Medicine and Rehabilitation";
        }

// Sleep Medicine Specialist: Sleep and Circadian Disorders
        if (lower.contains("daytime sleepiness") || lower.contains("chronic snoring") || lower.contains("gasping for air during sleep") || lower.contains("restless sensations in the legs")) {
            return "Sleep Medicine";
        }

// Medical Geneticist: Genetic and Inherited Disorders
        if (lower.contains("developmental delays") || lower.contains("distinct facial features") || lower.contains("metabolic issues at birth") || lower.contains("family history of rare conditions")) {
            return "Medical Genetics";
        }

// Andrologist: Male Hormonal and Reproductive Health
        if (lower.contains("loss of muscle mass") || lower.contains("erectile dysfunction") || lower.contains("male infertility") || lower.contains("low testosterone")) {
            return "Andrology / Male Reproductive Health";
        }

// Primary Care Physician: General or Multi-System Illnesses
        if (lower.contains("mild fever") || lower.contains("sore throat") || lower.contains("general fatigue") || lower.contains("minor body aches") || lower.contains("routine screening")) {
            return "Primary Care / General Medicine";
        }

// Orthopedic Surgeon: Bone, Joint, and Structural Musculoskeletal Injuries
        if (lower.contains("snapping sound") || lower.contains("popping sound in joint") || lower.contains("unable to bear weight") || lower.contains("crooked bone") || lower.contains("suspected fracture")) {
            return "Orthopedic Surgery";
        }

// Vascular Surgeon / Specialist: Diseases of the Arteries and Veins (Excluding the Heart) / PAD
        if (lower.contains("cramping in calves while walking") || lower.contains("varicose veins") || lower.contains("cold leg") || lower.contains("legs turning blue or pale") || lower.contains("wounds on toes that won't heal") || lower.contains("shiny hairless skin on legs") || lower.contains("pain in feet when resting in bed") || lower.contains("pad")) {
            return "Vascular Medicine / Surgery";
        }

// Proctologist / Colorectal Surgeon: Lower GI and Rectal Conditions
        if (lower.contains("bright red blood when wiping") || lower.contains("painful lump near the anus") || lower.contains("leaking stool") || lower.contains("rectal pain")) {
            return "Colorectal Surgery / Proctology";
        }

// Audiologist: Hearing and Balance Disorders (Non-Surgical)
        if (lower.contains("muffled hearing") || lower.contains("difficulty understanding speech in crowds") || lower.contains("need to turn up the TV volume") || lower.contains("ear mold fitting")) {
            return "Audiology";
        }

// Dentist / Oral Surgeon: Teeth, Jaw, and Oral Cavity Disorders
        if (lower.contains("toothache") || lower.contains("bleeding gums") || lower.contains("pain when chewing") || lower.contains("impacted wisdom teeth") || lower.contains("jaw clicking")) {
            return "Dentistry / Oral Surgery";
        }

// Pain Management Specialist: Chronic, Unremitting Pain Conditions
        if (lower.contains("chronic lower back pain") || lower.contains("burning nerve pain") || lower.contains("sciatica") || lower.contains("pain that doesn't respond to over-the-counter medication")) {
            return "Pain Management";
        }

// Travel Medicine Specialist: Pre-Travel Health and Tropical Illness Prevention
        if (lower.contains("yellow fever vaccine") || lower.contains("malaria pills") || lower.contains("traveling international") || lower.contains("vaccines for travel")) {
            return "Travel Medicine";
        }

// Endocrinologist (Reproductive): Infertility and Reproductive Hormones
        if (lower.contains("unable to get pregnant after a year") || lower.contains("pcos") || lower.contains("missed periods without pregnancy") || lower.contains("ivf")) {
            return "Reproductive Endocrinology / Infertility";
        }

// Bariatric Specialist: Medical and Surgical Weight Management
        if (lower.contains("severe obesity") || lower.contains("weight loss surgery") || lower.contains("gastric bypass") || lower.contains("medical weight management")) {
            return "Bariatric Medicine";
        }

// Neurosurgeon: Surgical Spine and Brain Conditions
        if (lower.contains("spinal cord compression") || lower.contains("brain tumor") || lower.contains("sciatica radiating down leg") || lower.contains("severe neck pain with numbness")) {
            return "Neurosurgery";
        }

// Thoracic Surgeon: Non-Cardiac Chest Surgery
        if (lower.contains("collapsed lung") || lower.contains("lung nodule") || lower.contains("esophageal tumor") || lower.contains("chest wall deformity")) {
            return "Thoracic Surgery";
        }

// Plastic & Reconstructive Surgeon: Tissue Restoration and Repair
        if (lower.contains("cleft palate") || lower.contains("severe burn scar") || lower.contains("breast reconstruction") || lower.contains("skin graft")) {
            return "Plastic and Reconstructive Surgery";
        }

// Toxicologist: Poisoning and Adverse Chemical Exposure
        if (lower.contains("swallowed household cleaner") || lower.contains("carbon monoxide poisoning") || lower.contains("snake bite") || lower.contains("overdose on medication")) {
            return "Medical Toxicology / Emergency Medicine";
        }

// Sports Medicine Specialist: Non-Surgical Athletic Injuries
        if (lower.contains("runner's knee") || lower.contains("tennis elbow") || lower.contains("shin splints") || lower.contains("concussion from sports") || lower.contains("rotator cuff strain")) {
            return "Sports Medicine";
        }
        return "Other";


    }

    public static String getSpecialty(String illnessClass) {
        switch (illnessClass) {
            case "Neurological": return "Neurologist";
            case "Cardiac": return "Cardiologist";
            case "Pulmonology": return "Pulmonologist";
            case "Gastrointestinal / IBD": return "Gastroenterologist";
            case "Endocrinology / Bone Metabolism": return "Endocrinologist";
            case "Rheumatology / Gout / Lupus": return "Rheumatologist";
            case "Dermatology / Trichology": return "Dermatologist";
            case "Urology": return "Urologist";
            case "Gynecology / Urogynecology": return "Gynecologist";
            case "Hematology / Oncology / Coagulation": return "Oncologist";
            case "Allergy / Immunology symptoms": return "Allergist";
            case "Psychiatry / OCD": return "Psychiatrist";
            case "Otolaryngology / Neurotology": return "Otolaryngologist";
            case "Nephrology / Kidney": return "Nephrologist";
            case "Ophthalmology / Cornea": return "Ophthalmologist";
            case "Infectious Disease": return "Infectious Disease Specialist";
            case "Hepatology / Liver / Biliary Medicine": return "Hepatologist";
            case "Podiatry / Foot Care": return "Podiatrist";
            case "Geriatrics / Senior Care": return "Geriatrician";
            case "Physical Medicine and Rehabilitation": return "Physiatrist";
            case "Sleep Medicine": return "Sleep Specialist";
            case "Medical Genetics": return "Geneticist";
            case "Andrology / Male Reproductive Health": return "Andrologist";
            case "Primary Care / General Medicine": return "General Practitioner";
            case "Orthopedic Surgery": return "Orthopedic Surgeon";
            case "Vascular Medicine / Surgery": return "Vascular Surgeon";
            case "Colorectal Surgery / Proctology": return "Colorectal Surgeon";
            case "Audiology": return "Audiologist";
            case "Dentistry / Oral Surgery": return "Dentist";
            case "Pain Management": return "Pain Specialist";
            case "Travel Medicine": return "Travel Medicine Specialist";
            case "Reproductive Endocrinology / Infertility": return "Fertility Specialist";
            case "Bariatric Medicine": return "Bariatrician";
            case "Neurosurgery": return "Neurosurgeon";
            case "Thoracic Surgery": return "Thoracic Surgeon";
            case "Plastic and Reconstructive Surgery": return "Plastic Surgeon";
            case "Medical Toxicology / Emergency Medicine": return "Toxicologist";
            case "Sports Medicine": return "Sports Medicine Specialist";
            default: return "General Practitioner";
        }
    }
}
