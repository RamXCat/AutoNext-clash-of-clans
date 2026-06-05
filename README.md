# AutoNext CoC

<div align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" />
</div>

<br/>

**AutoNext CoC** is an autonomous, non-intrusive macro application designed for *Clash of Clans* on Android. It leverages on-device Machine Learning (ML Kit) and Android's Accessibility Services to automate the repetitive process of finding multiplayer bases that meet user-defined resource thresholds.

> **Disclaimer**: This application is a third-party tool and is not affiliated with, endorsed by, or sponsored by Supercell. Usage of macros or auto-clickers may violate the terms of service of the game. Use at your own risk.

---

## ✨ Features

- **Real-Time OCR Analysis:** Utilizes Google ML Kit's Text Recognition to accurately read Gold and Elixir quantities from the screen in real-time, directly from a dynamically captured image.
- **Automated Searching:** Simulates human touch using the Android Accessibility Service API to continuously tap the "Next" button until a base satisfying the specified criteria is found.
- **Floating HUD Interface:** A sleek, draggable System Alert Window provides an intuitive overlay to control scanning parameters, view logs, and manually start/stop the bot without leaving the game.
- **Robust Error Correction:** Includes a built-in `LootEvaluator` engine designed to sanitize and normalize common OCR misreadings specific to stylized gaming fonts.
- **Non-Intrusive:** Requires no root access or modifications to the original game's APK or memory.

## 🛠 Prerequisites

- Android device running **Android 8.0 (API Level 26)** or higher.
- **Permissions Required:**
  - **Draw Over Other Apps (System Alert Window):** Required to display the control panel and scanner targeting box.
  - **Accessibility Service:** Required to perform the automated tapping gestures.
  - **Screen Recording (Media Projection):** Required to capture the game screen for OCR analysis.

## 🚀 Installation

1. Clone this repository:
   ```bash
   git clone https://github.com/RamXCat/AutoNext-clash-of-clans.git
   ```
2. Open the project in **Android Studio**.
3. Allow Gradle to sync and build the project.
4. Run the app on an Android Emulator or a physical device connected via ADB.

## 📖 Usage Guide

1. **Launch AutoNext CoC** and grant the initial setup permissions (Overlays and Accessibility).
2. Tap **"LAUNCH OVERLAY"** and allow screen capture when prompted.
3. Open *Clash of Clans* and start a multiplayer battle search.
4. On the floating **Control Panel**, set your desired minimum thresholds for Gold and Elixir.
5. Drag the **Scanner Box** (transparent rectangle) over the area where the opponent's available loot is displayed.
6. Drag the **Click Pointer** (small circular target) directly over the game's "Next" button.
7. Tap **"START"** on the control panel. The bot will automatically capture the screen, read the loot, and either stop to alert you or tap "Next" and repeat the process.

## 🏗 Architecture

- **`MainActivity` (Jetpack Compose):** Handles the modern configuration UI and securely requests system-level permissions.
- **`FloatingWidgetService`:** The core orchestrator running as a Foreground Service. It manages the `WindowManager` overlays, captures the screen via `MediaProjection`, and processes images using `TextRecognition`.
- **`AutoClickService`:** An Android `AccessibilityService` dedicated solely to dispatching global touch gestures.
- **`LootEvaluator`:** The business logic class responsible for parsing the raw unstructured string output from ML Kit into structured numerical data with fallback heuristics.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
