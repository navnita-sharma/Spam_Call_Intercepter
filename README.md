# 🛡️ Guardian: Advanced AI Scam Interceptor

**Guardian** is a privacy-first, offline-first Android security application designed to protect elderly users from telephonic financial scams. Using on-device Artificial Intelligence, the app monitors active calls in real-time, detects suspicious scam patterns, and takes immediate preventive action before sensitive information can be disclosed.

---

## 🌟 Overview

Financial scams targeting senior citizens have become increasingly sophisticated. Fraudsters often exploit trust, urgency, and fear to obtain OTPs, banking credentials, and personal information.

Guardian acts as a real-time AI-powered companion that continuously analyzes call conversations, identifies scam attempts, and actively prevents victims from sharing sensitive information.

---

## ✨ Key Features

### 🎙️ Real-Time Scam Detection

* Live transcript overlay during ongoing calls.
* Continuous speech monitoring using offline Speech-to-Text.
* Instant scam risk assessment without cloud dependency.

### 🧠 Advanced 4-Layer NLP Engine

Guardian combines multiple detection techniques for high accuracy:

#### 1. Keyword Blacklist

Detects dangerous terms such as:

* OTP
* Bank Verification
* KYC Update
* Account Suspension
* Credit Card Block

#### 2. Regex Pattern Matching

Identifies suspicious phrases including:

* "Your KYC is expiring"
* "Share your OTP"
* "Verify your account immediately"

#### 3. Finite State Machine (FSM)

Tracks scam intent progression:

**Authority → Urgency → Action**

Example:

* "I am calling from your bank."
* "Your account will be blocked."
* "Share the OTP now."

#### 4. Semantic Intent Classification

Uses FAISS vector similarity search with TensorFlow Lite embeddings to detect scam intentions even when attackers use different wording.

---

## 🚨 Radical Prevention System

### 🎤 Automatic Microphone Muting

Once a scam is confidently detected:

* Guardian automatically mutes the device microphone.
* Prevents accidental disclosure of OTPs, PINs, passwords, or banking information.

### 🔊 Emergency Warning Alerts

* Loud customizable siren.
* Continuous vibration feedback.
* Persistent warning notification.
* Alerts remain active until the call is disconnected.

---

## 🔒 Privacy & Security

### 100% Offline Processing

Guardian performs all processing locally on the device.

No data is:

* Uploaded
* Stored externally
* Shared with third-party services

### Technologies Used

* Vosk Speech-to-Text
* TensorFlow Lite
* FAISS Vector Search

All audio and transcripts remain on the user's device.

---

## 👨‍👩‍👧‍👦 Whitelist Protection

Trusted contacts are automatically excluded from monitoring.

Examples:

* Family Members
* Friends
* Emergency Contacts

This significantly reduces false positives while ensuring uninterrupted communication with loved ones.

---

## 🌍 Elderly-Centric Design

### Multi-Language Support

* English
* Hindi
* Marathi
* Bengali

### Accessibility Features

* Large warning messages
* High-contrast visuals
* Easy-to-understand alerts
* Simplified navigation

### Simulated Scam Training Mode

Users can safely test:

* Scam triggers
* Warning alerts
* Protection mechanisms

without receiving real scam calls.

---

## 🛠️ Tech Stack

| Category           | Technology              |
| ------------------ | ----------------------- |
| Language           | Kotlin                  |
| Platform           | Android Native          |
| UI Framework       | Jetpack Compose         |
| Speech Recognition | Vosk Android            |
| AI/NLP             | TensorFlow Lite + FAISS |
| Overlay System     | Accessibility Service   |
| Hardware Control   | AudioManager            |
| Build System       | Gradle                  |

---

## 📱 System Requirements

* Android 8.0 (Oreo) or higher
* Physical Android device recommended
* Target SDK 34
* Android Studio Ladybug or newer

---

## 🚀 Installation

### Step 1: Clone Repository

```bash
git clone https://github.com/raju-raushan/scam-call-interceptor.git
```

### Step 2: Open Project

Open the project in:

```text
Android Studio Ladybug (or newer)
```

### Step 3: Add Vosk Model

Place the speech model folder inside:

```text
app/src/main/assets/model-en-in
```

### Step 4: Build Project

```bash
./gradlew build
```

### Step 5: Run Application

* Connect Android device.
* Select the device in Android Studio.
* Click Run.

---

## 🔑 Required Permissions

Guardian requires the following permissions:

### Accessibility Service

Used for:

* Overlay display
* Call monitoring

### Record Audio

Used for:

* Offline speech recognition

### Read Phone State

Used for:

* Detecting active calls

### Read Contacts

Used for:

* Managing trusted contacts whitelist

### Display Over Other Apps

Used for:

* Warning overlays
* Scam alerts

---

## 📈 Future Enhancements

* Additional Indian language support
* AI-powered scam risk scoring
* Adaptive learning from emerging scam patterns
* Emergency family notification system
* Senior citizen dashboard
* Scam analytics and reporting

---

## ⚠️ Disclaimer

Guardian is an assistive security tool and does not guarantee protection against every possible social engineering attack. It is designed to identify known scam patterns, provide timely warnings, and reduce the likelihood of sensitive information disclosure.

Users should always exercise caution when sharing personal or financial information over phone calls.

---

## ❤️ Mission

Our mission is to empower senior citizens with accessible, privacy-preserving technology that helps them stay safe from financial fraud and digital scams.

**Built with ❤️ for the safety and dignity of our elders.**

