// Temporary sketch — upload this to configure HC-05, then re-upload sailboatgps.ino
// Bridges USB Serial Monitor ↔ HC-05 on Serial1
// AT command mode baud is always 38400 regardless of the module's data baud setting


/**
the hc-05 bluetooth module must be reconfigured to talk at a higher rate to get the most out of the GPS

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
Line ending: Both NL & CR << this is the drop down next to the message box in the serial monitor
Baud rate: 115200 (this is the USB side, to talk to Teensy)
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

*/

void setup() {
  Serial.begin(115200);   // USB to PC
  Serial1.begin(38400);   // HC-05 in AT command mode always uses 38400
}

void loop() {
  if (Serial.available())  Serial1.write(Serial.read());
  if (Serial1.available()) Serial.write(Serial1.read());
}
