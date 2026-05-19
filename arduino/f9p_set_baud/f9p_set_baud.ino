/*
 * f9p_set_baud.ino — One-time utility: change ZED-F9P UART1 baud to 115200
 *
 * The F9P ships at 38400 baud. The main GpsAndImuDataEmitterOverBluetooth sketch
 * connects at 115200. Run this sketch once to change and save the baud rate,
 * then re-flash the main sketch.
 *
 * Wiring:
 *   Same as the main sketch — F9P TX1 → MCU RX2, MCU TX2 → F9P RX1.
 *   See GpsAndImuDataEmitterOverBluetooth.ino for full wiring details.
 *
 * Usage:
 *   1. Flash this sketch.
 *   2. Open Serial Monitor at 115200 baud.
 *   3. Confirm "Done" message.
 *   4. Re-flash GpsAndImuDataEmitterOverBluetooth.ino.
 */

#include <SparkFun_u-blox_GNSS_Arduino_Library.h>

SFE_UBLOX_GNSS gps;

#ifdef ARDUINO_ARCH_ESP32
  #define GPS_RX_PIN 16
  #define GPS_TX_PIN 17
#endif

void setup() {
  Serial.begin(115200);
  delay(3000);   // give Serial Monitor time to connect before printing

  Serial.println("Connecting to F9P at 38400 (factory default)...");

#ifdef ARDUINO_ARCH_ESP32
  Serial2.begin(38400, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
#else
  Serial2.begin(38400);
#endif

  if (!gps.begin(Serial2)) {
    Serial.println("F9P not found at 38400.");
    Serial.println("It may already be running at 115200 — no action needed.");
    Serial.println("If you see this unexpectedly, check TX/RX wiring and power.");
    while (true) delay(500);
  }

  Serial.println("Connected. Changing UART1 to 115200 and saving to flash...");

  gps.setSerialRate(115200, COM_PORT_UART1);
  gps.saveConfiguration();

  Serial.println("Done. Re-flash GpsAndImuDataEmitterOverBluetooth.ino.");
}

void loop() {}
