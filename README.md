Guardian: Advanced AI Scam Interceptor
Guardian is a privacy-first, offline-first mobile security application designed specifically to protect elderly users from telephonic financial scams. Using on-device AI, it monitors active calls in real-time, detects suspicious patterns, and takes immediate preventive action.

🌟 Key Features
1. Real-Time Scam Interception
Live Transcript Overlay: Displays a non-intrusive textual transcript of the incoming caller's speech directly on top of the call screen.
4-Layer NLP Engine: Detects threats using a combination of:
Keyword Blacklist: Immediate detection of dangerous words.
Regex Pattern Matching: Detects suspicious phrases like "KYC Expiring" or "OTP required".
Finite State Machine (FSM): Tracks the "Intent Sequence" (Authority -> Urgency -> Action).
Vector Similarity (FAISS): Semantic intent classification using TensorFlow Lite.
2. Radical Prevention
Automatic Hardware Mute: At the moment a scam is confirmed, the app mutes the phone's microphone to prevent the user from accidentally sharing sensitive information (OTP, PIN, etc.).
Continuous Alerts: Triggers a loud siren (customizable) and continuous haptic feedback (vibration) that persists until the user disconnects.
3. Privacy & Security
100% Offline Processing: Uses Vosk STT and TFLite for all analysis. No audio or transcript ever leaves the device—ensuring total privacy.
Whitelist Integration: Automatically disables scanning for trusted contacts (family members/friends) to ensure zero false positives for loved ones.
4. Elderly-Centric Design
Localized Experience: Full UI and detection support for English, Hindi, Marathi, and Bengali.
Simplified UI: Large, bold warnings and high-contrast visuals designed for accessibility.
Simulated Call Mode: A built-in training module that allows users to test the app's triggers in a safe, controlled environment.
🛠 Tech Stack
Language: Kotlin (Android Native)
UI Framework: Jetpack Compose
Speech-to-Text: Vosk-Android (Offline)
AI/NLP Engine: FAISS (Vector Inference)
System Integration: Android Accessibility Service (for real-time overlay)
Hardware Control: AudioManager (for hardware-level muting)
🚀 Getting Started
Prerequisites
Android 8.0 (Oreo) or higher.
Physical device recommended for testing the Call Overlay.
Target SDK: 34 (Optimized for 16 KB Page Size devices).
Installation
Clone the repository.
Open in Android Studio Ladybug (or newer).
Ensure you have the required Vosk model directory (model-en-in) placed in the assets folder.
Build and Run.
Required Permissions
Guardian requires specific permissions to function correctly:

Accessibility Service: To render the overlay and monitor call events.
Record Audio: To process STT (offline only).
Read Phone State & Contacts: To manage the whitelist and detect active calls.
Display Over Other Apps: For the warning alerts.
⚠️ Disclaimer
Guardian is an assistive tool and does not guarantee 100% protection against all types of advanced social engineering. It is designed to act as a "guardian angel" that alerts users to standard, documented scam patterns.

How To Start
Step 1: Clone the repo -- git clone https://github.com/raju-raushan/scam-call-interceptor Step 2: Open the folder in code editor (Android Studio) and run the command in terminal -- /gradlew build Step 3: Start the app from the start button after selecting your device to run the app

To use full power of app we need to install it as a system app

Built for the safety of our elders.
