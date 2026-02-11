# Capstone Project: Health Band Integration

## 🎯 Goal
Create a "No GUI" health band for elderly and disabled individuals that:
1.  Connects to a custom wearable (TI CC2674 MCU + ADXL366 Accelerometer).
2.  Streams/Syncs health data (Heart Rate, SpO2, Steps) to Android Health Connect.
3.  Detects falls and automatically alerts an emergency contact via SMS.

## 🏗️ Architecture

### 1. Hardware (The Band)
-   **MCU:** TI CC2674R106T0RGZR (SimpleLink, BLE 5.3, Cortex-M33).
-   **Sensors:**
    -   ADXL366 (Accelerometer) for Fall Detection.
    -   PPG Sensor (for HR/SpO2) - TBD.
-   **Logic:**
    -   Advertises BLE Services.
    -   Monitors accelerometer for impact/fall patterns.
    -   Sends "Fall Detected" notification or streams data.

### 2. Android App (The Gateway)
-   **Foreground Service:** Maintains persistent BLE connection.
-   **BLE Manager:** Handles scanning, connecting, and auto-reconnection.
-   **Data Sync:** Writes health metrics to **Android Health Connect**.
-   **Safety Monitor:** Listens for Fall events. Triggers local alarm + SMS alert.

## 📝 Integration Plan

### Phase 1: The Foundation
-   [ ] **Scaffold Android App:** Setup structure with `hilt` (DI), `coroutines`, and `Jetpack Compose`.
-   [ ] **Permissions:** Request `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `SEND_SMS`.
-   [ ] **BLE Scanner:** basic scanner to find the CC2674.

### Phase 2: Connection & Data
-   [ ] **GATT Client:** Connect to the band and discover services.
-   [ ] **Heart Rate Sync:** Read standard HR UUID `0x180D`.
-   [ ] **Health Connect:** Setup API to write HR data to the phone's central repository.

### Phase 3: Safety (Fall Detection)
-   [ ] **Fall Algorithm:**
    -   *Option A (Preferred):* Band detects fall -> Sends Notification.
    -   *Option B (Fallback):* Band streams XYZ accel data -> App detects fall.
-   [ ] **Alert System:**
    -   Local: Loud alarm/vibration on phone.
    -   Remote: SMS to emergency contact with location.

### Phase 4: Polish & Power
-   [ ] **Background Optimization:** Ensure service survives "Doze Mode".
-   [ ] **Reconnection Logic:** Handle out-of-range and power-cycle events gracefully.

## 📚 References
-   **Repo:** `nathanasbury/capstone-andriod-integration`
-   **MCU:** TI CC2674R106T0RGZR
-   **Accel:** ADXL366
