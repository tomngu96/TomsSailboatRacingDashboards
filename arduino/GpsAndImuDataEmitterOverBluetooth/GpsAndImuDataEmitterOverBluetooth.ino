/*
 * GpsAndImuDataEmitterOverBluetooth.ino — Sailboat sensor hub
 *
 * Supports two hardware configurations selected automatically at compile time:
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   TEENSY 4.x  +  HC-05
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   BNO080 / BNO085  (SparkFun breakout — I2C)
 *     Teensy 3V3  → BNO VCC           3.3 V supply (breakout has no onboard reg)
 *     Teensy GND  → BNO GND
 *     Teensy 18   → BNO SDA           I2C data
 *     Teensy 19   → BNO SCL           I2C clock
 *     I2C address: 0x4A (default — PS0 and PS1 pulled low on SparkFun breakout)
 *     No other pins required; INT/RESET/WAKE/PS0/PS1 handled by breakout board.
 *
 *   HC-05  (Bluetooth module, Serial1)
 *     USB VBUS (5 V) → HC-05 VCC      5 V required — Teensy 3V3 cannot supply enough current
 *     Teensy GND     → HC-05 GND
 *     Teensy TX1 (pin 1) → HC-05 RX   Teensy transmits → HC-05 receives
 *     HC-05 TX       → Teensy RX1 (pin 0)   HC-05 transmits → Teensy receives
 *     NOTE: HC-05 UART is 3.3 V logic despite 5 V supply — no level shifter needed
 *           for Teensy 4.x (3.3 V I/O). Do NOT connect HC-05 TX to a 5 V-only MCU.
 *     Must be pre-configured to 115200 baud via AT commands (see AT-command section below).
 *
 *   GPS option A — ZED-F9P / NEO-D9S combo board  [GPS_MODULE_F9P]
 *     Use the UART1 (SMA-side) pins on the board — do NOT use the Qwiic connector.
 *     Qwiic (I2C) is too slow for 20 Hz RTK data; UART gives the required throughput.
 *     GPS-22560 (SparkFun F9P+D9S combo): only wire the F9P UART1 pins.
 *     Leave the D9S SMA antenna port unconnected — D9S is not used (NTRIP via phone instead).
 *
 *     Teensy 3V3  → F9P VCC           F9P+D9S combo draws ~100 mA; Teensy 3V3 (250 mA max) is fine.
 *     Teensy GND  → F9P GND
 *     F9P  TX1 (F9P's UART1 transmit pin) → Teensy pin 7  (Teensy's Serial2 RX)
 *     Teensy pin 8 (Teensy's Serial2 TX)  → F9P  RX1 (F9P's UART1 receive pin)
 *       NOTE: "TX1/RX1" = the F9P's UART port 1.  "Serial2/pin 7-8" = Teensy's second UART.
 *             The port numbers are independent — "1" and "2" refer to different devices.
 *
 *     IMPORTANT — baud rate pre-configuration required:
 *       The F9P ships at 38400. This sketch connects at 115200 (GPS_BAUD below).
 *       The SparkFun GNSS library does NOT auto-detect baud.
 *       Before first use, open u-center, connect to the F9P, and set UART1 baud to 115200.
 *       Save to flash (CFG-CFG → Save). After that, this sketch connects correctly.
 *
 *   GPS option B — NEO-M9N  (SparkFun GPS-15712, UART)  [GPS_MODULE_M9N]
 *     Teensy 3V3  → GPS VCC
 *     Teensy GND  → GPS GND
 *     GPS TX      → Teensy RX2 (pin 7)
 *     Teensy TX2 (pin 8) → GPS RX
 *     Baud: 38400 (SparkFun board default — no pre-configuration needed)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   ESP32  (30-pin CP2102 DevKit — all connections on the LEFT rail)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   BNO080 / BNO085  (SparkFun breakout — I2C)
 *     ESP32 3V3 (left rail) → BNO VCC
 *     ESP32 GND (left rail) → BNO GND
 *     ESP32 D21 / GPIO21    → BNO SDA
 *     ESP32 D22 / GPIO22    → BNO SCL
 *     I2C address: 0x4A (default — PS0 and PS1 pulled low on SparkFun breakout)
 *
 *   Bluetooth: built-in — no extra hardware, no wiring.
 *
 *   GPS option A — ZED-F9P / NEO-D9S combo board  [GPS_MODULE_F9P]
 *     ESP32 VIN (5 V, left rail) → GPS VCC
 *       ↑ Use VIN, not 3V3 — the GPS board's onboard LDO needs headroom above 3.3 V.
 *         Powering from 3V3 leaves less than 100 mV of dropout margin; the chip can
 *         brown out under load. VIN (USB 5 V) gives a stable input for the LDO.
 *     ESP32 GND (left rail) → GPS GND
 *     F9P  TX1 (F9P's UART1 transmit pin) → ESP32 GPIO16 (labelled RX2 on DevKit — ESP32's UART2 RX)
 *     ESP32 GPIO17 (labelled TX2 — ESP32's UART2 TX) → F9P RX1 (F9P's UART1 receive pin)
 *       NOTE: "TX1/RX1" = the F9P's UART port 1.  "RX2/TX2" = the ESP32's second UART (UART2).
 *     Leave D9S SMA port unconnected (NTRIP used instead of L-Band corrections).
 *     IMPORTANT: pre-configure F9P UART1 to 115200 in u-center before first use (see above).
 *
 *   GPS option B — NEO-M9N  (SparkFun GPS-15712, UART)  [GPS_MODULE_M9N]
 *     ESP32 VIN (5 V) → GPS VCC          (same headroom reasoning as above)
 *     ESP32 GND       → GPS GND
 *     GPS TX  → ESP32 RX2 / GPIO16
 *     ESP32 TX2 / GPIO17 → GPS RX
 *     Baud: 38400 (no pre-configuration needed)
 *
 *   30-pin CP2102 left rail — quick reference (top to bottom):
 *     VIN  → GPS VCC   (5 V)
 *     3V3  → BNO VCC
 *     GND  → BNO GND, GPS GND
 *     D21  → BNO SDA
 *     D22  → BNO SCL
 *     RX2  → GPS TX    (GPIO16)
 *     TX2  → GPS RX    (GPIO17)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   HC-05 AT-command setup  (Teensy only — one-time baud configuration)
 * ═══════════════════════════════════════════════════════════════════════════════
 *   1. Flash hc05_passthrough.ino to Teensy.
 *   2. Hold HC-05 KEY/EN button, reconnect power, release after ~1 s.
 *      LED should slow-blink (~1× per 2 s) — confirms AT mode.
 *   3. Open Serial Monitor at 115200, line ending = Both NL & CR.
 *   4. Send:  AT                  → expect: OK
 *   5. Send:  AT+UART=115200,0,0  → expect: OK
 *   6. Power-cycle HC-05 without holding KEY to return to data mode.
 *   7. Re-flash this sketch to Teensy.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   Libraries (install via Arduino Library Manager)
 * ═══════════════════════════════════════════════════════════════════════════════
 *   SparkFun BNO080 Cortex Based IMU
 *   SparkFun u-blox GNSS Arduino Library
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   Packet format  (25 Hz, \r\n terminated)
 * ═══════════════════════════════════════════════════════════════════════════════
 *   Without GPS:  $SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAcc*XX
 *   With GPS:     $SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAcc,lat,lon,sog,cog,fix,rtk*XX
 *
 *   lat/lon : 7 decimal places (double) — ~1 cm resolution for smooth 25 Hz motion math
 *   fix     : u-blox fixType  0=none  2=2D  3=3D
 *   rtk     : carrier solution  0=none  1=float (~10–30 cm)  2=fixed (~1–2 cm)
 *             always 0 on M9N
 *   XX      : CRC-8 (XOR of all bytes between $ and *, exclusive)
 */

#include <Wire.h>
#include "SparkFun_BNO080_Arduino_Library.h"
#include <SparkFun_u-blox_GNSS_Arduino_Library.h>

// ── Platform-specific Bluetooth setup ────────────────────────────────────────
#ifdef ARDUINO_ARCH_ESP32
  #include <BluetoothSerial.h>
  BluetoothSerial SerialBT;
  #define BT_SERIAL  SerialBT
  #define I2C_SDA    21
  #define I2C_SCL    22
  #define GPS_RX_PIN 16   // ESP32 RX2 — receives GPS TX
  #define GPS_TX_PIN 17   // ESP32 TX2 — sends RTCM3 corrections to GPS RX
#else
  // Teensy — HC-05 on Serial1 (RX=pin 0, TX=pin 1), pre-configured to 115200
  #define BT_SERIAL  Serial1
  #define BT_BAUD    115200
  #define I2C_SDA    18
  #define I2C_SCL    19
  #define GPS_RX_PIN 7    // Teensy RX2 — receives GPS TX
  #define GPS_TX_PIN 8    // Teensy TX2 — sends RTCM3 corrections to GPS RX
#endif

// ── GPS module selection ──────────────────────────────────────────────────────
// Uncomment ONE of the two lines below.
// M9N: 25 Hz, standard GNSS accuracy (~1 m). No RTK.
// F9P: 20 Hz, centimetre RTK accuracy when receiving RTCM3 corrections via NTRIP.
// #define GPS_MODULE_F9P
#define GPS_MODULE_M9N

#ifdef GPS_MODULE_F9P
  #define GPS_NAV_HZ 20
  // F9P must be pre-configured to 115200 in u-center before use.
  // The SparkFun GNSS library does NOT auto-detect baud rate.
  #define GPS_BAUD   115200
#else
  #define GPS_NAV_HZ 25
  #define GPS_BAUD   38400   // SparkFun NEO-M9N factory default
#endif

// ── GPS serial port ───────────────────────────────────────────────────────────
#define GPS_SERIAL Serial2

// ── IMU mounting ──────────────────────────────────────────────────────────────
// Set true when the BNO080 is mounted flat with chip facing UP (Z-axis = up, ENU).
// Flips yaw so clockwise rotation = increasing heading, matching compass convention.
#define IMU_Z_UP true

// ── Objects ───────────────────────────────────────────────────────────────────
BNO080       imu;
SFE_UBLOX_GNSS gps;

// ── IMU state ─────────────────────────────────────────────────────────────────
float    imu_hdg_deg  = 0.0f;
float    imu_pitch    = 0.0f;
float    imu_roll     = 0.0f;
float    imu_gyroZ    = 0.0f;   // yaw rate °/s — positive = turning right (clockwise)
float    imu_ax       = 0.0f;   // linear acceleration m/s²
float    imu_ay       = 0.0f;
float    imu_az       = 0.0f;
uint8_t  imu_accuracy = 0;      // 0=unreliable … 3=high

// ── GPS state ─────────────────────────────────────────────────────────────────
double   gps_lat       = 0.0;   // degrees — double preserves 7-decimal-place resolution
double   gps_lon       = 0.0;
float    gps_sog_kts   = 0.0f;  // speed over ground, knots
float    gps_cog_deg   = 0.0f;  // course over ground, degrees true
uint8_t  gps_fixType   = 0;     // 0=none  2=2D  3=3D
uint8_t  gps_rtkStatus = 0;     // 0=none  1=float  2=fixed  (F9P only, always 0 on M9N)
bool     gps_active    = false;

// ── Forward declarations ──────────────────────────────────────────────────────
bool    pollIMU();
void    transmitPacket();
uint8_t crc8(const char* data, size_t len);
bool    readGPS();

// =============================================================================
void setup() {
  Serial.begin(115200);

#ifdef ARDUINO_ARCH_ESP32
  SerialBT.begin("SailRacingf9p");   // BT device name visible on phone
  Wire.begin(I2C_SDA, I2C_SCL);
#else
  BT_SERIAL.begin(BT_BAUD);
  Wire.begin();
#endif

  Wire.setClock(400000);  // 400 kHz I2C — BNO080 supports up to 400 kHz

  if (!imu.begin()) {
    Serial.println("BNO080 not found — check SDA/SCL wiring, power (3V3+GND), and I2C address (0x4A).");
    while (true) delay(500);
  }

  // ARVR-stabilised rotation vector: magnetometer-fused heading with gyro drift correction.
  // 40 ms period = 25 Hz (matches GPS_NAV_HZ for M9N; F9P runs at 20 Hz but IMU still at 25).
  imu.enableARVRStabilizedRotationVector(40);
  imu.enableGyro(40);
  imu.enableLinearAccelerometer(40);

  Serial.println("BNO080 ready.");
}

// =============================================================================
void loop() {
  bool newIMU = pollIMU();
  readGPS();

  if (newIMU) {
    transmitPacket();
  }

  // Forward RTCM3 correction bytes arriving from phone via Bluetooth → GPS module.
  // Only active once GPS serial port is initialised. Safe to call every loop.
  if (gps_active) {
    while (BT_SERIAL.available()) {
      GPS_SERIAL.write(BT_SERIAL.read());
    }
  }
}

// =============================================================================
// Poll BNO080 FIFO and update IMU state globals.
// Returns true if a new sample was available.
// =============================================================================
bool pollIMU() {
  if (!imu.dataAvailable()) return false;

  float qw = imu.getQuatReal();
  float qx = imu.getQuatI();
  float qy = imu.getQuatJ();
  float qz = imu.getQuatK();
  imu_accuracy = imu.getQuatAccuracy();

  // Extract yaw (heading) from quaternion using standard ZYX Euler decomposition.
  // This formula gives yaw relative to the BNO080's magnetic north reference.
  imu_hdg_deg = atan2f(2.0f * (qw*qz + qx*qy),
                        1.0f - 2.0f * (qy*qy + qz*qz)) * (180.0f / PI);
  // BNO080 in ENU (Z-up) frame reports CCW-positive yaw.
  // Negate to get CW-positive (compass convention: clockwise = increasing degrees).
  if (IMU_Z_UP) imu_hdg_deg = -imu_hdg_deg;
  if (imu_hdg_deg < 0.0f) imu_hdg_deg += 360.0f;

  // Pitch: positive = nose up
  imu_pitch = asinf(2.0f * (qw*qy - qz*qx)) * (180.0f / PI);

  // Roll: positive = starboard heel (right side down)
  imu_roll  = atan2f(2.0f * (qw*qx + qy*qz),
                     1.0f - 2.0f * (qx*qx + qy*qy)) * (180.0f / PI);

  imu_gyroZ = imu.getGyroZ();         // rad/s from sensor — converted to °/s in Android app
  imu_ax    = imu.getLinAccelX();     // m/s²
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

  if (gps_active && gps_fixType >= 2) {
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
  Serial.print(packet);   // USB mirror for testing with Serial Monitor
}

// =============================================================================
// CRC-8: XOR of all bytes between $ and * (exclusive), matching NMEA convention.
// =============================================================================
uint8_t crc8(const char* data, size_t len) {
  uint8_t crc = 0;
  for (size_t i = 0; i < len; i++) crc ^= (uint8_t)data[i];
  return crc;
}

// =============================================================================
// GPS helper — initialises Serial2 on first successful call, then polls PVT data.
// Uses a failure flag so a missing/miswired GPS does not spam Serial2.begin()
// every loop iteration.
// =============================================================================
bool readGPS() {
  static bool gpsInit   = false;
  static bool gpsFailed = false;
  if (gpsFailed) return false;

  if (!gpsInit) {
#ifdef ARDUINO_ARCH_ESP32
    GPS_SERIAL.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
#else
    GPS_SERIAL.begin(GPS_BAUD);
#endif

    if (!gps.begin(GPS_SERIAL)) {
      Serial.println("GPS module not found — check TX/RX wiring, power, and baud rate.");
#ifdef GPS_MODULE_F9P
      Serial.println("  F9P: ensure UART1 is pre-configured to 115200 baud in u-center.");
#endif
      gpsFailed = true;   // stop retrying — avoid repeated Serial2.begin() calls
      return false;
    }

    // Output UBX only — disables NMEA output to reduce bandwidth on UART1.
    gps.setUART1Output(COM_TYPE_UBX);

#ifdef GPS_MODULE_F9P
    // Explicitly enable RTCM3 on UART1 input so correction bytes forwarded from
    // the phone (via Bluetooth) are accepted by the F9P and used for RTK.
    // (Default F9P firmware also accepts RTCM3 input, but better to set it explicitly.)
    gps.setUART1Input(COM_TYPE_UBX | COM_TYPE_RTCM3);
#endif

    gps.setNavigationFrequency(GPS_NAV_HZ);
    gps.setAutoPVT(true);

    gpsInit    = true;
    gps_active = true;
    Serial.println("GPS ready.");
  }

  if (!gps.getPVT()) return false;

  gps_fixType   = gps.getFixType();
  gps_rtkStatus = gps.getCarrierSolutionType();   // 0/1/2 on F9P; always 0 on M9N
  gps_lat       = gps.getLatitude()  / 1e7;       // u-blox returns 1e-7 degrees
  gps_lon       = gps.getLongitude() / 1e7;

  float sog_mmps = (float)gps.getGroundSpeed();   // library returns mm/s
  gps_sog_kts    = sog_mmps * 0.00194384f;        // mm/s → knots (1 kn = 514.444 mm/s)
  gps_cog_deg    = gps.getHeading() / 1e5f;       // library returns 1e-5 degrees
  return true;
}
