# Remote Display & Virtual Camera Prototype

This project implements a cross-platform remote display and virtual camera framework.

- **Server**: Windows Laptop (Python)
- **Client**: Android Device (Kotlin)

## Prerequisites

### Server

- Python 3.8+
- Windows OS

### Client

- Android Studio
- Android Device (Developer Mode enabled)

## Setup & Run

### 1. Server (Windows)

1.  Navigate to the `server` directory:
    ```powershell
    cd server
    ```
2.  Install dependencies:
    ```powershell
    pip install -r requirements.txt
    ```
3.  Run the server:
    ```powershell
    python server.py
    ```
    The server will start on `0.0.0.0:8080` (Signaling) and `0.0.0.0:9999` (Input).

### 2. Client (Android)

1.  Open the `android` folder in Android Studio.
2.  Connect your Android device via USB.
3.  Build and Run the app (`app` configuration).
4.  Ensure your Android device and Laptop are on the **same network** (e.g., Laptop Hotspot).

### 3. Usage

1.  On the Android app, enter the **Laptop's IP Address** (e.g., `192.168.137.1` if using Hotspot).
2.  Click **Connect**.
3.  The Laptop screen should appear on the Android device.
4.  Touch inputs on the Android device will control the Laptop mouse.

### 4. Camera Mode

1.  Toggle the **Camera Mode** switch **before** connecting.
2.  Click **Connect**.
3.  The Android device will stream its camera to the Laptop.
4.  The Laptop server will receive the video track (currently logs to console).

## Architecture

- **Video Streaming**: WebRTC (aiortc on Server, Google WebRTC on Client).
- **Signaling**: HTTP POST (aiohttp).
- **Input**: TCP Socket (pynput).
