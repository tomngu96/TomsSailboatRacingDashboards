/*
 * GpsAndImuDataEmitterOverBluetooth.ino — Sailboat sensor hub
 *
 * Supports two hardware configurations selected automatically at compile time:
 *
 *   TEENSY (4.x) + HC-05
 *     BNO080/085       I2C      SDA=18, SCL=19
 *                               PWR: 3.3 V (Teensy 3V3 pin),  GND → GND
 *     HC-05            Serial1  TX=1,   RX=0
 *                               PWR: 5 V — use USB VBUS pin, NOT Teensy 3V3 (not enough current)
 *                               GND → GND
 *                               Must be reconfigured to 115200 baud via AT commands (see below)
 *
 *     GPS option A — ZED-F9P / NEO-D9S combo (UART)     [GPS_MODULE_F9P]
 *       Use the UART1 pins on the board directly — do NOT use the Qwiic connector.
 *       Qwiic (I2C) is too slow for 20 Hz RTK data; UART gives the throughput needed.
 *       Serial2  F9P-TX → Teensy pin 7 (RX),  F9P-RX → Teensy pin 8 (TX)
 *       PWR: 3.3 V (Teensy 3V3 pin),  GND → GND
 *       Baud: 115200 (set via GPS_BAUD below — F9P default is 38400, library auto-detects)
 *
 *     GPS option B — NEO-M9N (GPS-15712, UART)          [GPS_MODULE_M9N]
 *       Serial2  GPS-TX → Teensy pin 7 (RX),  GPS-RX → Teensy pin 8 (TX)
 *       PWR: 3.3 V (Teensy 3V3 pin),  GND → GND
 *       Baud: 9600 default
 *
 *   ESP32
 *     BNO080/085       I2C      SDA=21, SCL=22
 *                               PWR: 3.3 V (ESP32 3V3 pin),   GND → GND
 *     Bluetooth                 built-in, no extra hardware needed
 *
 *     GPS option A — ZED-F9P / NEO-D9S combo (UART)     [GPS_MODULE_F9P]
 *       Use the UART1 pins on the board directly — do NOT use the Qwiic connector.
 *       Serial2  F9P-TX → ESP32 pin 16 (RX),  F9P-RX → ESP32 pin 17 (TX)
 *       PWR: 3.3 V (ESP32 3V3 pin),  GND → GND
 *       Baud: 115200
 *
 *     GPS option B — NEO-M9N (GPS-15712, UART)          [GPS_MODULE_M9N]
 *       Serial2  GPS-TX → ESP32 pin 16 (RX),  GPS-RX → ESP32 pin 17 (TX)
 *       PWR: 3.3 V (ESP32 3V3 pin),  GND → GND
 *       Baud: 9600 default
 *
 * HC-05 AT-command setup (Teensy only):
 *   1. Flash hc05_passthrough.ino to Teensy.
 *   2. Hold HC-05 KEY button, reconnect power, release after 1 s.
 *      LED should slow-blink (~1× per 2 s) = AT mode.
 *   3. Open Serial Monitor at 115200, Both NL & CR.
 *   4. Send:  AT              → expect: OK
 *   5. Send:  AT+UART=115200,0,0   → expect: OK
 *   6. Reconnect HC-05 power without holding button to return to data mode.
 *
 * Libraries (install via Arduino Library Manager):
 *   SparkFun BNO080 Cortex Based IMU
 *   SparkFun u-blox GNSS Arduino Library
 *
 * Packet format (25 Hz, \r\n terminated):
 *   Without GPS:  $SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAcc*XX
 *   With GPS:     $SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAcc,lat,lon,sog,cog,fix,rtk*XX
 *
 *   lat/lon: 7 decimal places (double) — ~1 cm resolution for smooth 25 Hz motion math
 *   fix:     u-blox fixType  0=none 2=2D 3=3D
 *   rtk:     carrier solution 0=none 1=float(~10-30 cm) 2=fixed(~1-2 cm) — always 0 on M9N
 */

#include <Wire.h>
#include "SparkFun_BNO080_Arduino_Library.h"
// #include <SparkFun_u-blox_GNSS_Arduino_Library.h>   // uncomment when GPS is wired

// ── Platform-specific Bluetooth setup ────────────────────────────────────────
#ifdef ARDUINO_ARCH_ESP32
  #include <BluetoothSerial.h>
  BluetoothSerial SerialBT;
  #define BT_SERIAL  SerialBT
  #define I2C_SDA    21
  #define I2C_SCL    22
  #define GPS_RX_PIN 16
  #define GPS_TX_PIN 17
#else
  // Teensy — HC-05 on Serial1, must be pre-configured to 115200 via AT commands
  #define BT_SERIAL  Serial1
  #define BT_BAUD    115200
  #define I2C_SDA    18
  #define I2C_SCL    19
  #define GPS_RX_PIN 7
  #define GPS_TX_PIN 8
#endif

// ── GPS module selection ──────────────────────────────────────────────────────
// Uncomment ONE. M9N tops out at 25 Hz; F9P at 20 Hz but supports RTK centimeter accuracy.
// F9P accepts RTCM3 correction bytes forwarded from the phone via Bluetooth.
// #define GPS_MODULE_F9P
#define GPS_MODULE_M9N

#ifdef GPS_MODULE_F9P
  #define GPS_NAV_HZ 20
#else
  #define GPS_NAV_HZ 25
#endif

// ── GPS serial port ──────────────────────────────────────────────────────────
#define GPS_SERIAL Serial2
#ifdef GPS_MODULE_F9P
  #define GPS_BAUD 115200  // F9P handles 20 Hz RTK comfortably at 115200
#else
  #define GPS_BAUD 9600    // M9N default
#endif

// ── IMU mounting ─────────────────────────────────────────────────────────────
// Set true when the BNO080 is mounted flat with chip facing UP (Z-axis = up, ENU).
// Flips yaw so clockwise rotation = increasing heading, matching compass convention.
#define IMU_Z_UP true

// ── Objects ──────────────────────────────────────────────────────────────────
BNO080 imu;
// SFE_UBLOX_GNSS gps;   // uncomment when GPS is wired

// ── IMU state ────────────────────────────────────────────────────────────────
float    imu_hdg_deg  = 0.0f;
float    imu_pitch    = 0.0f;
float    imu_roll     = 0.0f;
float    imu_gyroZ    = 0.0f;   // yaw rate °/s, positive = turning right
float    imu_ax       = 0.0f;   // linear accel m/s²
float    imu_ay       = 0.0f;
float    imu_az       = 0.0f;
uint8_t  imu_accuracy = 0;      // 0=unreliable … 3=high

// ── GPS state ─────────────────────────────────────────────────────────────────
double   gps_lat      = 0.0;    // degrees — double for 7-decimal resolution
double   gps_lon      = 0.0;
float    gps_sog_kts  = 0.0f;   // speed over ground, knots
float    gps_cog_deg  = 0.0f;   // course over ground, degrees true
uint8_t  gps_fixType  = 0;      // 0=none 2=2D 3=3D
uint8_t  gps_rtkStatus = 0;    // 0=none 1=float 2=fixed (F9P only, always 0 on M9N)
bool     gps_active   = false;

// ── Forward declarations ──────────────────────────────────────────────────────
bool    pollIMU();
void    transmitPacket();
uint8_t crc8(const char* data, size_t len);
bool    readGPS();

// =============================================================================
void setup() {
  Serial.begin(115200);

#ifdef ARDUINO_ARCH_ESP32
  SerialBT.begin("SailRacing");   // BT device name visible on phone
  Wire.begin(I2C_SDA, I2C_SCL);
#else
  BT_SERIAL.begin(BT_BAUD);
  Wire.begin();
#endif

  Wire.setClock(400000);  // 400 kHz I2C for both platforms

  if (!imu.begin()) {
    Serial.println("BNO080 not found — check SDA/SCL and power.");
    while (true) delay(500);
  }

  // ARVR-stabilised rotation vector: mag-fused heading with slow drift correction
  imu.enableARVRStabilizedRotationVector(40);  // 40 ms = 25 Hz
  imu.enableGyro(40);
  imu.enableLinearAccelerometer(40);

  Serial.println("BNO080 ready.");
  Serial.println("GPS code present but inactive — uncomment readGPS() in loop() when wired.");
}

// =============================================================================
void loop() {
  bool newIMU = pollIMU();

  // bool newGPS = readGPS();   // uncomment after wiring GPS-15712
  bool newGPS = false;

  if (gps_active ? newGPS : newIMU) {
    transmitPacket();
  }

  // Forward any RTCM correction bytes arriving from the phone → GPS module.
  // Only when GPS is active (serial port initialised). Safe to call every loop.
  if (gps_active) {
    while (BT_SERIAL.available()) {
      GPS_SERIAL.write(BT_SERIAL.read());
    }
  }
}

// =============================================================================
// Poll BNO080 FIFO and update IMU state globals.
// =============================================================================
bool pollIMU() {
  if (!imu.dataAvailable()) return false;

  float qw = imu.getQuatReal();
  float qx = imu.getQuatI();
  float qy = imu.getQuatJ();
  float qz = imu.getQuatK();
  imu_accuracy = imu.getQuatAccuracy();

  imu_hdg_deg = atan2f(2.0f * (qw*qz + qx*qy),
                        1.0f - 2.0f * (qy*qy + qz*qz)) * (180.0f / PI);
  if (IMU_Z_UP) imu_hdg_deg = -imu_hdg_deg;
  if (imu_hdg_deg < 0.0f) imu_hdg_deg += 360.0f;

  imu_pitch = asinf (2.0f * (qw*qy - qz*qx))              * (180.0f / PI);
  imu_roll  = atan2f(2.0f * (qw*qx + qy*qz),
                     1.0f - 2.0f * (qx*qx + qy*qy))       * (180.0f / PI);

  imu_gyroZ = imu.getGyroZ();
  imu_ax    = imu.getLinAccelX();
  imu_ay    = imu.getLinAccelY();
  imu_az    = imu.getLinAccelZ();
  return true;
}

// =============================================================================
// Build and transmit one $SAL sentence over Bluetooth + USB mirror.
// =============================================================================
void transmitPacket() {
  char body[160];
  int  len;

  if (gps_active) {
    len = snprintf(body, sizeof(body),
      "SAL,%.1f,%.1f,%.1f,%.2f,%.3f,%.3f,%.3f,%u,"
      "%.7f,%.7f,%.2f,%.1f,%u,%u",
      imu_hdg_deg, imu_pitch, imu_roll, imu_gyroZ,
      imu_ax, imu_ay, imu_az, imu_accuracy,
      gps_lat, gps_lon, gps_sog_kts, gps_cog_deg, gps_fixType, gps_rtkStatus);
  } else {
    len = snprintf(body, sizeof(body),
      "SAL,%.1f,%.1f,%.1f,%.2f,%.3f,%.3f,%.3f,%u",
      imu_hdg_deg, imu_pitch, imu_roll, imu_gyroZ,
      imu_ax, imu_ay, imu_az, imu_accuracy);
  }

  char packet[180];
  snprintf(packet, sizeof(packet), "$%s*%02X\r\n", body, crc8(body, len));

  BT_SERIAL.print(packet);
  Serial.print(packet);   // USB mirror for testing
}

// =============================================================================
// CRC-8 (XOR of all bytes).
// =============================================================================
uint8_t crc8(const char* data, size_t len) {
  uint8_t crc = 0;
  for (size_t i = 0; i < len; i++) crc ^= (uint8_t)data[i];
  return crc;
}

// =============================================================================
// GPS-15712 helper — wired but not called yet.
//
// To activate:
//   1. Wire GPS TX → RX pin, GPS RX → TX pin (see top of file for pin numbers)
//        GPS VCC → 3.3 V, GND → GND
//   2. Uncomment the #include and SFE_UBLOX_GNSS gps above.
//   3. Uncomment readGPS() in loop().
// =============================================================================
bool readGPS() {
  // static bool gpsInit = false;
  // if (!gpsInit) {
  //
  //   // Both modules use UART (Serial2). F9P runs at 115200; M9N at 9600.
  //   // Do NOT use the Qwiic connector for the F9P — I2C is too slow for 20 Hz RTK.
  //   #ifdef ARDUINO_ARCH_ESP32
  //     GPS_SERIAL.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  //   #else
  //     GPS_SERIAL.begin(GPS_BAUD);
  //   #endif
  //
  //   #ifdef GPS_MODULE_F9P
  //   // F9P ships at 38400 by default. Tell the library what baud we want so it
  //   // can auto-detect and then switch the module to 115200.
  //   if (!gps.begin(GPS_SERIAL, GPS_BAUD)) {
  //     Serial.println("ZED-F9P not found — check UART1 TX/RX wiring and 3.3 V power.");
  //     return false;
  //   }
  //   gps.setSerialRate(GPS_BAUD);   // lock module to 115200
  //   #else
  //   if (!gps.begin(GPS_SERIAL)) {
  //     Serial.println("NEO-M9N not found — check TX/RX wiring and 3.3 V power.");
  //     return false;
  //   }
  //   #endif
  //
  //   gps.setUART1Output(COM_TYPE_UBX);
  //   gps.setNavigationFrequency(GPS_NAV_HZ);   // 20 Hz (F9P) or 25 Hz (M9N)
  //   gps.setAutoPVT(true);
  //   gpsInit    = true;
  //   gps_active = true;
  //   Serial.println("GPS ready.");
  // }
  //
  // if (!gps.getPVT()) return false;
  //
  // gps_fixType   = gps.getFixType();
  // gps_rtkStatus = gps.getCarrierSolutionType();  // 0=none 1=float 2=fixed (F9P only)
  // gps_lat     = gps.getLatitude()  / 1e7;   // int32 1e-7 deg → double degrees
  // gps_lon     = gps.getLongitude() / 1e7;
  //
  // float sog_mmps = (float)gps.getGroundSpeed();
  // gps_sog_kts    = sog_mmps * 0.00194384f;
  // gps_cog_deg    = gps.getHeading() / 1e5f;
  // return true;

  return false;
}
