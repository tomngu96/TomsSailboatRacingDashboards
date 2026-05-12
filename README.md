# TomsSailboatRacingDashboards

A sailing instrumentation system to give sailors fast, accurate, race‑useful data such as historical speed and heading on a budget. The full system consists of:

1. **A mobile app** (Android) that displays sailing metrics. 
2. **An external hardware sensor module** built around a Teensy 4.0, u‑blox M9N GPS, and BNO080 IMU for high‑rate, fairly accurate data.

The app can run **stand‑alone** using the phone’s internal sensors, but is limited by the phone’s slow refresh rate and accuracy which can be fairly bad (there may be smoothing applied over position noise). To get a semi-accurate read from your phone you must mount it to the boat or keep it stationary on the boat. The full system shines when paired with the external hardware module as you can secure that to the boat and walk around with or mount your phone which is gathers and forms the data for display. You can use a cheaper GPS module and probably get all parts for under $100 assuming you already own an android phone.

### Primary dashboard
<img src="Screenshot_20260511_013039_SailRacing.jpg" width="350">

### Sample of possible graphs
this was taken from me running around the house so the data is very erratic. 

<img src="Screenshot_20260511_012744_SailRacing.jpg" width="350">


### Disclaimer
You should read the racing rules for whatever race you plan on racing in before using instruments, as they are sometimes banned.

Also there's a lot of schools of thoughts on "sailing by the instruments"... I say just go out and sail but at a certain point you might be curious if heeling the boat over a few more degrees gives you a bit more speed, or if footing a bit more to get more speed gives you better VMG. This app ideally helps shine a light there so you can drive improvements through something measurable. Who knows if this will actually be useful or will have too much noise. I'm happy to take feature requests/feedback. 

---

## Component 1: The Mobile App

The app contains:
- Current speed (knots)
- Heading
- Course over ground (COG)
- Historical speed graph
- Manual or auto count down timer with narration
- Lift/header visualization (WIP)
- Data logging

---

## Component 2: External Hardware Module

### Hardware Used
- **Teensy 4.0** (600 MHz MCU)
- **Bluetooth Classic (HC‑05)** for Android streaming
- **u‑blox M9N GPS** (SparkFun GPS‑15712 or equivalent). 
  - 32db High Gain Cirocomm 5cm Active GPS Antenna Ceramic Antenna
- **IMU** (BNO080)
- **5V power supply** (battery or USB breakout board)
- solder in breadboard - for permanently mounting
- tupperware to protect electronics
- Optional: 3d printed enclosure, this is custom designed but is useful to orient + protect the board inside the protective case if the case is secured to the boat and we want to physically true up the heading based on the IMU. TODO is upload the stl
- power bank
- Optional: **sunlight‑readable display** for standalone mode

### Why This Hardware?
- **sparkfun M9N GPS** 
  - Ease of setup, you can get GPS modules way cheaper but I wanted something fairly plug and play with rich data. an m8n is sufficient 
  - 25 Hz update rate  
  - Excellent multipath rejection  
  - Fast reacquisition  
  - High‑resolution speed output 
- u.fl antenna - You can generally pick one of either an onboard antenna, u.fl, or external SMA puck style antenna (this is in order from smallest to biggest). The antenna I chose is a good mix of size and performance and should fit inside

- **IMU (BNO080)**  
  - Tilt‑compensated heading  
  - Fast response during maneuvers 
  - GPS-only heading is unreliable at low speeds because heading is computed from small changes in position. The GPS speed is accurate because the receiver uses Doppler carrier frequency shift to measure velocity directly. Doppler gives smooth, high‑rate speed data, but heading still requires either significant movement or an IMU.

- **Teensy 4.0**  
  - Extremely fast UBX parsing  
  - Plenty of headroom for sensor fusion if we wanted to, we're a bit light on using the full power of the teensy
  - Low latency  
  - Really I only chose this because I had one available - an ESP32 actually has a bluetooth module and may be cheaper/fast enough.

- **HC‑05 bluetooth module**  
  - Has bluetooth classic (as opposed to LE), this helps with the continuous stream of packets and i think in practical applications is good enough for our scenario
  - ~100–200 kbps sustained
  - 5–10 ms latency*
  - the teensy doesnt have bluetooth on board natively, dont buy one if youve an ESP32 or similar as the ESP32 has bluetooth and wifi natively


- **Android phone**
  - It is very hard to find a cheap display that can be visible outdoors in sunlight. Most people own a phone with a good display so let's leverage that. Also it's easier to build an app and have the ability to interact with it via your touchscreen compared to a screen bound to the teensy. 
  - There is a much higher chance I would get an app on the play store than the app store. I dont own an iphone and you need to pay a recurring fee to get something on the apple app store. 

---

## Data Transmission

### External Module → Phone
Communication uses **Bluetooth Classic (HC‑05)** for low latency and high throughput.

### Packet Format (example)

Without GPS:  
```
"$SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAcc*CRC\r\n"  
(“Without GPS:  $SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAccXX”)*
```

With GPS:  
```
"$SAL,hdg,pitch,roll,gyroZ,ax,ay,az,imuAcc,lat,lon,sog,cog,fix*CRC\r\n"  
(“With GPS:  $SAL,...,lat,lon,sog,cog,fixXX”)*
```

| Field | Meaning |
| --- | --- |
| ``hdg`` | Magnetic heading (deg true, 0–360) |
| ``pitch`` | Pitch angle (deg) |
| ``roll`` | Roll angle (deg) |
| ``gyroZ`` | Yaw rate (°/s) |
| ``ax`` | Linear accel X (m/s²) |
| ``ay`` | Linear accel Y (m/s²) |
| ``az`` | Linear accel Z (m/s²) |
| ``imuAcc`` | IMU accuracy (0–3) |

GPS‑augmented packet fields
`"SAL, ... ,%.6f,%.6f,%.2f,%.1f,%u"`
| Field | Meaning |
| --- | --- |
| ``lat`` | Latitude (deg, 6 decimals) |
| ``lon`` | Longitude (deg, 6 decimals) |
| ``sog_kts`` | Speed over ground (knots) |
| ``cog`` | Course over ground (deg true) |
| ``fixType`` | 0=none, 2=2D, 3=3D |

The packet rate is driven by the IMU:

```
“40 ms = 25 Hz”
(“enableARVRStabilizedRotationVector(40);  // 40 ms = 25 Hz”)
```

When GPS is active, GPS fixes trigger transmission instead.

---
### Wiring
You can look at the GpsAndImuDataEmitterOverBluetooth which has the most recent wiring. It's all text, a TODO is to diagram this into the readme. 