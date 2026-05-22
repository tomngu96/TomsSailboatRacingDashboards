/*
 * NmeaPassthroughF9P.ino — ESP32 + ZED-F9P NMEA Bluetooth bridge
 *
 * Stripped-down companion to GpsAndImuDataEmitterOverBluetooth.
 * No IMU, no custom packet format.  The F9P is configured to output standard
 * NMEA sentences which are forwarded byte-for-byte to a Bluetooth SPP serial
 * connection.  Any GPS-capable racing app (Sailracer, Sailgrib, iSailor,
 * NV Charts, etc.) can connect and use the data directly.
 *
 * RTCM3 correction bytes sent by the phone (e.g. via an NTRIP client in the
 * racing app) are forwarded back to the F9P so RTK positioning still works.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   WIRING  (identical pin layout to GpsAndImuDataEmitterOverBluetooth)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *   ZED-F9P  (SparkFun GPS-16481 or similar, UART)
 *     ESP32 VIN (5 V, left rail) → F9P VCC
 *       ↑ Use VIN not 3V3 — the onboard LDO needs headroom above 3.3 V.
 *     ESP32 GND (left rail)      → F9P GND
 *     F9P  TX1 → ESP32 GPIO16   (labelled RX2 — ESP32 UART2 RX)
 *     ESP32 GPIO17 (labelled TX2) → F9P RX1
 *
 *   Bluetooth: built-in ESP32 — no extra hardware, no wiring.
 *
 *   GPIO21 / GPIO22 (I2C) are intentionally left free — same board can later
 *   have a BNO085 added without re-wiring.
 *
 *   Left rail quick reference (top to bottom):
 *     VIN  → F9P VCC  (5 V)
 *     3V3  → (unused)
 *     GND  → F9P GND
 *     D21  → (unused — I2C SDA, reserved)
 *     D22  → (unused — I2C SCL, reserved)
 *     RX2  → F9P TX1  (GPIO16)
 *     TX2  → F9P RX1  (GPIO17)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   Pre-requisite
 * ═══════════════════════════════════════════════════════════════════════════════
 *   F9P UART1 must be pre-configured to 115200 baud before first use.
 *   If not already done, run the f9p_set_baud sketch once.
 *   (This sketch falls back to 38400 automatically and reconfigures if needed.)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   Library (install via Arduino Library Manager)
 * ═══════════════════════════════════════════════════════════════════════════════
 *   SparkFun u-blox GNSS Arduino Library  (used only during setup for init)
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   NMEA output  (25 Hz — F9P maximum, forwarded over Bluetooth SPP)
 * ═══════════════════════════════════════════════════════════════════════════════
 *   Enabled  (essential for racing apps):
 *     $GNGGA — position, fix quality, HDOP, altitude
 *               Quality indicator: 1=GPS, 4=RTK fixed, 5=RTK float
 *     $GNRMC — position, SOG, COG, date/time, mode indicator
 *     $GNVTG — COG true/magnetic, SOG knots/km-h
 *
 *   Disabled  (verbose / redundant):
 *     $GNGSV — satellite details: many sentences per epoch, kills bandwidth at 25 Hz
 *     $GNGLL — lat/lon + time: fully redundant with GGA + RMC
 *
 *   Bandwidth at 25 Hz with GGA + RMC + VTG + GSA:
 *     GGA + RMC + VTG  ≈ 190 bytes/epoch × 25 Hz ≈  4 750 bytes/s
 *     + GSA (1–3 per epoch) ≈  80 bytes/epoch × 25 Hz ≈  6 750 bytes/s total
 *     Both well within 115200 baud (11 520 bytes/s).
 *     Do NOT re-enable GSV at 25 Hz — with 3 constellations it can exceed 650 bytes/epoch
 *     and will overflow the UART (~23 000 bytes/s).
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 *   Bluetooth device name
 * ═══════════════════════════════════════════════════════════════════════════════
 *   "gpsf9pnmea"  — pair from phone settings; open from your racing app.
 */

#include <BluetoothSerial.h>
#include <SparkFun_u-blox_GNSS_Arduino_Library.h>

// ── Bluetooth ─────────────────────────────────────────────────────────────────
BluetoothSerial SerialBT;

// ── Pin definitions (same as GpsAndImuDataEmitterOverBluetooth) ───────────────
#define GPS_RX_PIN 16   // ESP32 UART2 RX — receives NMEA from F9P TX1
#define GPS_TX_PIN 17   // ESP32 UART2 TX — sends RTCM3 corrections to F9P RX1

// ── GPS serial port ───────────────────────────────────────────────────────────
#define GPS_SERIAL Serial2

// ── F9P configuration ─────────────────────────────────────────────────────────
#define GPS_BAUD   115200
#define GPS_NAV_HZ 25    // 25 Hz — ZED-F9P maximum navigation rate

// ── SparkFun GNSS object — used only during init ──────────────────────────────
SFE_UBLOX_GNSS gps;

// ── State ─────────────────────────────────────────────────────────────────────
static bool gpsReady = false;

// =============================================================================
void setup() {
  Serial.begin(115200);
  Serial.println("\r\nNmeaPassthroughF9P starting...");

  SerialBT.begin("gpsf9pnmea");
  Serial.println("Bluetooth ready — device name: gpsf9pnmea");

  initGPS();
}

// =============================================================================
void loop() {
  if (!gpsReady) {
    static uint32_t retryAfter = 0;
    if ((uint32_t)millis() >= retryAfter) {
      if (!initGPS()) {
        retryAfter = (uint32_t)millis() + 5000;
      }
    }
    return;
  }

  // ── NMEA passthrough: F9P → Bluetooth ─────────────────────────────────────
  // Forward every byte from the F9P directly to the Bluetooth client.
  // No parsing or buffering — raw forwarding keeps latency minimal.
  while (GPS_SERIAL.available()) {
    SerialBT.write(GPS_SERIAL.read());
  }

  // ── RTCM3 passthrough: Bluetooth → F9P ────────────────────────────────────
  // Forward correction bytes from the phone back to the F9P so RTK works.
  while (SerialBT.available()) {
    GPS_SERIAL.write(SerialBT.read());
  }
}

// =============================================================================
// initGPS()
//
// Detects and configures the F9P.  Tries GPS_BAUD first; falls back to 38400
// (u-blox factory default) and reconfigures if found there.
//
// Configuration applied (with UBX output kept on during setup so ACKs arrive):
//   • Nav rate:    25 Hz
//   • UART1 input: UBX + RTCM3
//   • NMEA on:     GGA, RMC, VTG, GSA
//   • NMEA off:    GSV (bandwidth), GLL (redundant)
//   • UART1 out:   NMEA only (applied last, not saved — module boots UBX+NMEA
//                  so the next run can still init via the library)
//
// Returns true on success.
// =============================================================================
bool initGPS() {
  Serial.println("Searching for F9P...");

  const uint32_t tryBauds[] = { GPS_BAUD, 38400 };
  bool     found   = false;
  uint32_t foundAt = 0;

  for (uint32_t baud : tryBauds) {
    GPS_SERIAL.begin(baud, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
    delay(100);
    if (gps.begin(GPS_SERIAL)) {
      foundAt = baud;
      found   = true;
      break;
    }
  }

  if (!found) {
    Serial.println("F9P not found — retrying in 5 s.");
    Serial.println("  Check: TX1→GPIO16 / GPIO17→RX1 wiring, VIN power (5 V).");
    Serial.println("  If UART1 was never configured, run the f9p_set_baud sketch first.");
    return false;
  }

  Serial.printf("F9P found at %lu baud.\r\n", foundAt);

  // ── Reconfigure baud if found at fallback rate ─────────────────────────────
  if (foundAt != GPS_BAUD) {
    Serial.printf("Reconfiguring F9P to %d baud and saving to flash...\r\n", GPS_BAUD);
    gps.setSerialRate(GPS_BAUD, COM_PORT_UART1);
    delay(100);
    GPS_SERIAL.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
    delay(100);
    gps.saveConfiguration();
    Serial.println("Baud updated.");
  }

  // ── Navigation rate ────────────────────────────────────────────────────────
  gps.setNavigationFrequency(GPS_NAV_HZ);
  Serial.printf("Nav rate: %d Hz.\r\n", GPS_NAV_HZ);

  // ── UART1 input: UBX commands + RTCM3 corrections ─────────────────────────
  gps.setUART1Input(COM_TYPE_UBX | COM_TYPE_RTCM3);

  // ── Enable UBX + NMEA output during config so ACKs are received ───────────
  gps.setUART1Output(COM_TYPE_UBX | COM_TYPE_NMEA);

  // ── Disable verbose / redundant NMEA sentences ────────────────────────────
  // GSV (satellite list) — with 3 constellations this can be 10+ sentences per
  // epoch.  At 25 Hz that alone exceeds 115200 baud.  Keep off unconditionally.
  // GLL is fully redundant with GGA + RMC; nothing needs it.
  gps.disableNMEAMessage(UBX_NMEA_GSV, COM_PORT_UART1);
  gps.disableNMEAMessage(UBX_NMEA_GLL, COM_PORT_UART1);
  Serial.println("NMEA GSV, GLL disabled.");

  // ── Confirm essential sentences are enabled ────────────────────────────────
  gps.enableNMEAMessage(UBX_NMEA_GGA, COM_PORT_UART1);   // position + RTK quality indicator
  gps.enableNMEAMessage(UBX_NMEA_RMC, COM_PORT_UART1);   // SOG, COG, date, time
  gps.enableNMEAMessage(UBX_NMEA_VTG, COM_PORT_UART1);   // COG + SOG (preferred by many apps)
  gps.enableNMEAMessage(UBX_NMEA_GSA, COM_PORT_UART1);   // PDOP/HDOP/VDOP + active sat count
  Serial.println("NMEA GGA, RMC, VTG, GSA enabled.");

  // ── Save to flash (baud, nav rate, input protocols, message config) ────────
  // Output protocol (UBX+NMEA) is deliberately saved here so the module boots
  // with UBX output available for the library to init on the next power-up.
  gps.saveConfiguration();
  Serial.println("Configuration saved to flash.");

  // ── Switch to NMEA-only output for this session ────────────────────────────
  // Applied last so all the above ACKs are received.  NOT saved to flash — see above.
  gps.setUART1Output(COM_TYPE_NMEA);
  Serial.println("UART1 output: NMEA only.");

  gpsReady = true;
  Serial.println("F9P ready.  Forwarding NMEA at 25 Hz over Bluetooth.");
  Serial.println("Pair phone to 'gpsf9pnmea' and connect from your racing app.");
  return true;
}
