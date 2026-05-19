/*
 * GpsAndImuDataEmitterOverBluetooth.ino — Sailboat sensor hub
 *
 * Hardware: ESP32 (30-pin CP2102 DevKit)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   WIRING  (all connections on the LEFT rail, top to bottom)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   BNO080 / BNO085  (SparkFun breakout — I2C)
 *     ESP32 3V3 (left rail) → BNO VCC
 *     ESP32 GND (left rail) → BNO GND
 *     ESP32 D21 / GPIO21    → BNO SDA    I2C data
 *     ESP32 D22 / GPIO22    → BNO SCL    I2C clock
 *     I2C address: 0x4A (default — PS0 and PS1 pulled low on SparkFun breakout)
 *     No other pins required; INT/RESET/WAKE/PS0/PS1 handled by breakout board.
 *
 *   Bluetooth: built-in — no extra hardware, no wiring.
 *
 *   GPS option A — ZED-F9P / NEO-D9S combo board  [GPS_MODULE_F9P]
 *     ESP32 VIN (5 V, left rail) → GPS VCC
 *       ↑ Use VIN, not 3V3 — the GPS board's onboard LDO needs headroom above 3.3 V.
 *         Powering from 3V3 leaves less than 100 mV of dropout margin; the chip can
 *         brown out under load. VIN (USB 5 V) gives a stable input for the LDO.
 *     ESP32 GND (left rail) → GPS GND
 *     F9P  TX1 (F9P's UART1 transmit pin) → ESP32 GPIO16 (labelled RX2 — ESP32's UART2 RX)
 *     ESP32 GPIO17 (labelled TX2 — ESP32's UART2 TX) → F9P RX1 (F9P's UART1 receive pin)
 *       NOTE: "TX1/RX1" = the F9P's UART port 1.  "RX2/TX2" = the ESP32's second UART (UART2).
 *             The port numbers refer to different devices — they are not related.
 *     Leave D9S SMA port unconnected — D9S is not used (NTRIP corrections via phone instead).
 *     IMPORTANT: F9P UART1 must be pre-configured to 115200 baud before use.
 *       Run the f9p_set_baud sketch once if not already done.
 *
 *   GPS option B — NEO-M9N  (SparkFun GPS-15712, UART)  [GPS_MODULE_M9N]
 *     ESP32 VIN (5 V, left rail) → GPS VCC
 *     ESP32 GND (left rail)      → GPS GND
 *     GPS TX  → ESP32 GPIO16 (labelled RX2)
 *     ESP32 GPIO17 (labelled TX2) → GPS RX
 *     Baud: 38400 (SparkFun board default — no pre-configuration needed)
 *
 *   Left rail quick reference (top to bottom):
 *     VIN  → GPS VCC   (5 V)
 *     3V3  → BNO VCC
 *     GND  → BNO GND, GPS GND
 *     D21  → BNO SDA
 *     D22  → BNO SCL
 *     RX2  → GPS TX    (GPIO16)
 *     TX2  → GPS RX    (GPIO17)
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
#include <BluetoothSerial.h>
#include "SparkFun_BNO080_Arduino_Library.h"
#include <SparkFun_u-blox_GNSS_Arduino_Library.h>

// ── Bluetooth ─────────────────────────────────────────────────────────────────
BluetoothSerial SerialBT;
#define BT_SERIAL SerialBT

// ── Pin definitions ───────────────────────────────────────────────────────────
#define I2C_SDA    21
#define I2C_SCL    22
#define GPS_RX_PIN 16   // ESP32 UART2 RX — receives data from GPS TX
#define GPS_TX_PIN 17   // ESP32 UART2 TX — sends RTCM3 corrections to GPS RX

// ── GPS module selection ──────────────────────────────────────────────────────
// Uncomment ONE of the two lines below.
// M9N: 25 Hz, standard GNSS accuracy (~1 m). No RTK.
// F9P: 20 Hz, centimetre RTK accuracy when receiving RTCM3 corrections via NTRIP.
//   F9P UART1 must be pre-configured to 115200 in u-center before use
//   (use the f9p_set_baud sketch if needed).
#define GPS_MODULE_F9P
// #define GPS_MODULE_M9N

#ifdef GPS_MODULE_F9P
  #define GPS_NAV_HZ 20
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
BNO080        imu;
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

  SerialBT.begin("TomsSailRacingf9p");   // BT device name visible on phone
  Wire.begin(I2C_SDA, I2C_SCL);
  Wire.setClock(400000);              // 400 kHz I2C — BNO080 supports up to 400 kHz

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
//
// Init strategy:
//   • Tries GPS_BAUD first, then 38400 (u-blox factory default) as fallback.
//     If found at 38400 the module is automatically reconfigured to GPS_BAUD and
//     saved to flash so it persists across power cycles.
//   • On failure, retries every 5 s — never gives up permanently, so a module that
//     is slow to boot or was temporarily disconnected will recover automatically.
//   • A 100 ms settle delay is inserted before each begin() attempt; the u-blox
//     library requires the caller to open the Serial port first (it does not call
//     Serial2.begin() internally).
// =============================================================================
bool readGPS() {
  static bool     gpsInit    = false;
  static uint32_t retryAfter = 0;   // millis() after which to attempt init again

  if (!gpsInit) {
    if ((uint32_t)millis() < retryAfter) return false;

    // Try GPS_BAUD first; fall back to 38400 (u-blox factory default).
    // This handles the common case where f9p_set_baud was not run, or
    // was run but the baud rate did not persist to flash.
    const uint32_t tryBauds[] = { GPS_BAUD, 38400 };
    bool found = false;
    uint32_t foundAt = 0;

    for (uint32_t baud : tryBauds) {
      GPS_SERIAL.begin(baud, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
      delay(100);   // let the UART and GPS module settle before polling

      if (gps.begin(GPS_SERIAL)) {
        foundAt = baud;
        found   = true;
        break;
      }
    }

    if (!found) {
      Serial.println("GPS not found at any baud — retrying in 5 s.");
      Serial.println("  Check: TX→RX2 / TX2→RX wiring, VIN power (5 V), baud rate.");
#ifdef GPS_MODULE_F9P
      Serial.println("  F9P: if never configured, run f9p_set_baud sketch once.");
#endif
      retryAfter = (uint32_t)millis() + 5000;
      return false;
    }

    Serial.printf("GPS found at %lu baud.\r\n", foundAt);

    // If found at the wrong baud, bump it up to GPS_BAUD and save to flash.
    if (foundAt != GPS_BAUD) {
      Serial.printf("Reconfiguring GPS to %d baud and saving...\r\n", GPS_BAUD);
      gps.setSerialRate(GPS_BAUD, COM_PORT_UART1);
      // Module switches immediately — reopen UART at new rate.
      delay(100);
      GPS_SERIAL.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
      delay(100);
      gps.saveConfiguration();
      Serial.println("GPS baud updated.");
    }

    // Output UBX only — disables NMEA output to reduce UART1 bandwidth.
    gps.setUART1Output(COM_TYPE_UBX);

    // Note: F9P accepts RTCM3 input on UART1 by default — no explicit
    // configuration needed. Correction bytes forwarded from the phone via
    // Bluetooth will be accepted automatically.

    gps.setNavigationFrequency(GPS_NAV_HZ);
    gps.setAutoPVT(true);   // module pushes NAV-PVT automatically; getPVT() is non-blocking

    gpsInit    = true;
    gps_active = true;
    Serial.println("GPS ready.");
  }

  if (!gps.getPVT()) return false;

  gps_fixType   = gps.getFixType();
  gps_rtkStatus = gps.getCarrierSolutionType();   // 0/1/2 on F9P; always 0 on M9N
  gps_lat       = gps.getLatitude()  / 1e7;       // u-blox returns integer 1e-7 degrees
  gps_lon       = gps.getLongitude() / 1e7;

  float sog_mmps = (float)gps.getGroundSpeed();   // library returns mm/s
  gps_sog_kts    = sog_mmps * 0.00194384f;        // mm/s → knots  (1 kn = 514.444 mm/s)
  gps_cog_deg    = gps.getHeading() / 1e5f;       // library returns integer 1e-5 degrees
  return true;
}
