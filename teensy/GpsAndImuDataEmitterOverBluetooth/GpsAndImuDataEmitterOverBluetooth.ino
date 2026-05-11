/*
 * sailboatgps.ino — Teensy sailboat sensor hub
 *
 * Hardware:
 *   BNO080/085  I2C  4 pins to teensy: SDA=18, SCL=19 (Teensy 4.x default), VCC=3v, GND=G
 *   GPS-15712   (tomorrow) Serial2  TX=8, RX=7  — code present, not called
 *   HC-05       UART Serial1  3 pins to teensy: TX=1, RX=0, GND=GND... power must be 5v which teensy doesnt supply so use usb VBUS
 *                    this must be reconfigured to talk at a higher rate to get the most out of the GPS

The HC-05 has two modes: data mode (normal Bluetooth) and AT command mode (configuration). The KEY pin trick is how you force it into AT command mode. Here's exactly what to do.

What you're looking at on the HC-05 board
The HC-05 breakout board has:

A small button on the board (this is the KEY pin brought out to a button — you don't need to find the pin, just press the button)
An LED that tells you which mode it's in:
Fast blink (~2× per second) = normal data mode
Slow blink (~1× every 2 seconds) = AT command mode ✓
Step 1 — Upload a passthrough sketch to your Teensy
You need the Teensy to relay characters between your PC and the HC-05. 

Upload hc05_passthrough.ino to your Teensy first to reprogram.

Step 2 — Enter AT command mode
Unplug the HC-05 from power (disconnect the 5V wire or USB, whichever powers it). The Teensy can stay plugged in.
Press and hold the small button on the HC-05 board with your finger (or a pencil).
While still holding the button, reconnect power to the HC-05.
Release the button after about 1 second.
Look at the LED — it should now be blinking slowly (one long blink every 2 seconds). That means AT command mode is active.
If it's still blinking fast, the button wasn't held at the right moment — disconnect power and try again from step 1.

Step 3 — Open Arduino Serial Monitor
In Arduino IDE go to Tools → Serial Monitor (or Ctrl+Shift+M).
At the bottom of the Serial Monitor window, set:
Baud rate: 115200 (this is the USB side, to talk to Teensy)
Line ending: Both NL & CR
Step 4 — Verify the connection
Type exactly this and press Enter:

AT
The HC-05 should reply:

OK
If you see nothing or garbage, double-check your TX/RX wiring (pin 0 and pin 1 on Teensy) and that the LED is blinking slowly.

Step 5 — Set the new baud rate
Type this and press Enter:

AT+UART=115200,0,0
It should reply:

OK
The three numbers mean: baud rate, stop bits (0=1 stop bit), parity (0=none).

Step 6 — Verify it saved (optional but recommended)
AT+UART?
Should reply:

+UART:115200,0,0
OK
Step 7 — Return HC-05 to normal mode
Disconnect and reconnect power to the HC-05 without holding the button. The LED should go back to fast blinking. It will now use 115200 baud for all future data connections.

 *
 * Libraries (install via Arduino Library Manager):
 *   SparkFun BNO080 Cortex Based IMU
 *   SparkFun u-blox GNSS Arduino Library  (needed tomorrow — install now)
 *
 * Bluetooth packet format (10 Hz, \r\n terminated):
 *   $SAL,<hdg>,<pitch>,<roll>,<gyroZ>,<ax>,<ay>,<az>,<imuAcc>*<crc>\r\n
 *   GPS fields appended when active:
 *   $SAL,...,<lat>,<lon>,<sog_kts>,<cog>,<fixType>*<crc>\r\n
 *
 * HDG  = magnetic heading, degrees true  (0–360)
 * SOG  = speed over ground, knots
 * COG  = course over ground, degrees true
 */

#include <Wire.h>
#include "SparkFun_BNO080_Arduino_Library.h"
// #include <SparkFun_u-blox_GNSS_Arduino_Library.h>   // uncomment tomorrow

// ── Bluetooth ────────────────────────────────────────────────────────────────
#define BT_SERIAL  Serial1
// HC-05 must be reconfigured to 115200 via AT commands (see below).
// Factory default is 9600 — at that rate 25 Hz packets overflow the UART buffer.
#define BT_BAUD    115200

// ── GPS serial port ──────────────────────────────────────────────────────────
#define GPS_SERIAL Serial2
#define GPS_BAUD   9600    // u-blox default

// ── IMU mounting ─────────────────────────────────────────────────────────────
// If the BNO080 sits flat with the chip facing UP (Z-axis pointing up / ENU),
// the raw yaw is counter-clockwise-positive, which is the opposite of compass
// convention.  Set true to flip it so clockwise rotation = increasing heading.
#define IMU_Z_UP true

// ── Timing ───────────────────────────────────────────────────────────────────
// Transmit rate is driven by the BNO080 report interval (40 ms = 25 Hz).
// No separate millis() timer — IMU data arrival triggers the packet.

// ── Objects ──────────────────────────────────────────────────────────────────
BNO080 imu;
// SFE_UBLOX_GNSS gps;   // uncomment tomorrow

// ── IMU state ────────────────────────────────────────────────────────────────
float    imu_hdg_deg  = 0.0f;   // heading, degrees true (0–360)
float    imu_pitch    = 0.0f;
float    imu_roll     = 0.0f;
float    imu_gyroZ    = 0.0f;   // yaw rate °/s
float    imu_ax       = 0.0f;   // linear accel m/s²
float    imu_ay       = 0.0f;
float    imu_az       = 0.0f;
uint8_t  imu_accuracy = 0;      // 0=unreliable … 3=high

// ── GPS state (populated by readGPS tomorrow) ─────────────────────────────────
float    gps_lat      = 0.0f;   // degrees
float    gps_lon      = 0.0f;
float    gps_sog_kts  = 0.0f;   // speed over ground, knots
float    gps_cog_deg  = 0.0f;   // course over ground, degrees true
uint8_t  gps_fixType  = 0;      // 0=none 2=2D 3=3D
bool     gps_active   = false;  // set true in readGPS once initialised

// ── Forward declarations ──────────────────────────────────────────────────────
bool     pollIMU();   // returns true when a fresh sample was read
void     transmitPacket();
uint8_t  crc8(const char* data, size_t len);
bool     readGPS();   // returns true when a new GPS fix is ready — NOT called yet

// =============================================================================
void setup() {
  Serial.begin(115200);   // USB debug console
  BT_SERIAL.begin(BT_BAUD);

  Wire.begin();
  Wire.setClock(400000);  // 400 kHz I2C

  // ── BNO080 init ────────────────────────────────────────────────────────────
  if (!imu.begin()) {
    Serial.println("BNO080 not found — check SDA/SCL and power.");
    while (true) delay(500);
  }

  // ARVR-stabilised rotation vector: mag-fused heading, slow drift correction
  // (better than plain rotation vector for a moving boat)
  imu.enableARVRStabilizedRotationVector(40);  // 40 ms = 25 Hz
  imu.enableGyro(40);
  imu.enableLinearAccelerometer(40);

  Serial.println("BNO080 ready.");
  Serial.println("Waiting for GPS tomorrow — GPS code present but inactive.");
}

// =============================================================================
void loop() {
  bool newIMU = pollIMU();          // always runs, keeps heading globals fresh

  // bool newGPS = readGPS();       // ← uncomment tomorrow after wiring GPS-15712
  bool newGPS = false;

  // GPS drives the transmit rate when active; IMU fills in while GPS isn't wired.
  if (gps_active ? newGPS : newIMU) {
    transmitPacket();
  }
}

// =============================================================================
// Poll BNO080 FIFO and update IMU state globals.
// =============================================================================
bool pollIMU() {
  if (!imu.dataAvailable()) return false;

  // ── Rotation vector → heading ─────────────────────────────────────────────
  // BNO080 returns a unit quaternion in sensor frame.
  // Yaw (Z-axis rotation) equals magnetic heading when the board is level.
  float qw = imu.getQuatReal();
  float qx = imu.getQuatI();
  float qy = imu.getQuatJ();
  float qz = imu.getQuatK();
  imu_accuracy = imu.getQuatAccuracy();

  // Quaternion → yaw.  Negate when Z points up (ENU mounting) so that
  // clockwise rotation = increasing heading, matching compass convention.
  imu_hdg_deg = atan2f(2.0f * (qw*qz + qx*qy),
                        1.0f - 2.0f * (qy*qy + qz*qz)) * (180.0f / PI);
  if (IMU_Z_UP) imu_hdg_deg = -imu_hdg_deg;
  if (imu_hdg_deg < 0.0f) imu_hdg_deg += 360.0f;

  imu_pitch = asinf (2.0f * (qw*qy - qz*qx))              * (180.0f / PI);
  imu_roll  = atan2f(2.0f * (qw*qx + qy*qz),
                     1.0f - 2.0f * (qx*qx + qy*qy))       * (180.0f / PI);

  // ── Gyro & linear accel ───────────────────────────────────────────────────
  imu_gyroZ = imu.getGyroZ();           // °/s — positive = turning right
  imu_ax    = imu.getLinAccelX();       // m/s²
  imu_ay    = imu.getLinAccelY();
  imu_az    = imu.getLinAccelZ();
  return true;
}

// =============================================================================
// Build and transmit one $SAL sentence over Bluetooth + USB debug.
//
// Without GPS:  $SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAcc*XX
// With GPS:     $SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAcc,lat,lon,sog,cog,fix*XX
// =============================================================================
void transmitPacket() {
  char body[160];
  int  len;

  if (gps_active) {
    len = snprintf(body, sizeof(body),
      "SAL,%.1f,%.1f,%.1f,%.2f,%.3f,%.3f,%.3f,%u,"
      "%.6f,%.6f,%.2f,%.1f,%u",
      imu_hdg_deg, imu_pitch, imu_roll, imu_gyroZ,
      imu_ax, imu_ay, imu_az, imu_accuracy,
      gps_lat, gps_lon, gps_sog_kts, gps_cog_deg, gps_fixType);
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
// CRC-8 (XOR of all bytes) — simple integrity check for the phone app.
// =============================================================================
uint8_t crc8(const char* data, size_t len) {
  uint8_t crc = 0;
  for (size_t i = 0; i < len; i++) crc ^= (uint8_t)data[i];
  return crc;
}

// =============================================================================
// GPS-15712 helper — fully wired, NOT called yet.
//
// To activate tomorrow:
//   1. Wire GPS TX → Teensy pin 7 (Serial2 RX)
//         GPS RX → Teensy pin 8 (Serial2 TX)
//         GPS VCC → 3.3 V, GND → GND
//   2. Uncomment the #include and SFE_UBLOX_GNSS gps above.
//   3. Uncomment readGPS() in loop().
//   4. The packet will automatically include GPS fields once gps_active = true.
// =============================================================================
bool readGPS() {
  // ── One-time init ──────────────────────────────────────────────────────────
  // static bool gpsInit = false;
  // if (!gpsInit) {
  //   GPS_SERIAL.begin(GPS_BAUD);
  //   if (!gps.begin(GPS_SERIAL)) {
  //     Serial.println("GPS not found — check wiring.");
  //     return false;
  //   }
  //   gps.setUART1Output(COM_TYPE_UBX);          // binary UBX protocol
  //   gps.setNavigationFrequency(25);            // 25 Hz — NEO-M9N max rate
  //   gps.setAutoPVT(true);                      // async PVT messages
  //   gpsInit    = true;
  //   gps_active = true;
  //   Serial.println("GPS-15712 ready.");
  // }
  //
  // ── Return true only when the GPS delivers a fresh fix ─────────────────────
  // if (!gps.getPVT()) return false;            // no new data yet this cycle
  //
  // gps_fixType  = gps.getFixType();            // 0=none 2=2D 3=3D
  // gps_lat      = gps.getLatitude()  / 1e7f;  // degrees
  // gps_lon      = gps.getLongitude() / 1e7f;
  //
  // float sog_mmps = (float)gps.getGroundSpeed(); // mm/s
  // gps_sog_kts    = sog_mmps * 0.00194384f;       // → knots
  //
  // gps_cog_deg  = gps.getHeading() / 1e5f;       // degrees true
  // return true;

  return false;
}
