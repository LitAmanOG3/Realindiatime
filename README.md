# RealIndiaTime — Paper 1.21.11

A LITMC plugin that maps Minecraft daylight to real-world time using India time (`Asia/Kolkata`). In `REAL` mode it calculates approximate astronomical sunrise/sunset from latitude/longitude; `FIXED` mode lets you choose exact sunrise/sunset times.

## Features
- India timezone by default.
- Configurable worlds.
- REAL sunrise/sunset based on date + coordinates.
- FIXED sunrise/sunset mode.
- Freezes vanilla daylight cycle.
- Continuously corrects drift.
- Blocks sleeping during real night.
- Blocks player and server `/time` commands when enabled.
- `/rit reload`, `/rit sync`, `/rit status`.
- No external API is required at runtime.

## Build
Use Java 21 and Gradle. The server version 1.21.11 is documented by Paper as using Java 21. The dependency is Paper API 1.21.11-R0.1-SNAPSHOT.

## Install
Build the JAR and put it into the Paper server's `plugins/` folder, then restart the server.

## Important
The astronomical calculation is an approximation of sunrise/sunset. For a different city, change `location.latitude` and `location.longitude`. For a fixed schedule, set `mode: FIXED`.
