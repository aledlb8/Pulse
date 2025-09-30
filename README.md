<h1 align="center">Pulse</h1>
<p align="center">Modular Paper server core for Minecraft 1.21+, powered by Kotlin.</p>
<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Build" src="https://img.shields.io/badge/Gradle-Shadow-green?logo=gradle&logoColor=white" />
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" />
</p>

## Overview
Pulse unifies ranks, permissions, economy, chat, shops, tags, and placeholders into one lightweight core for Paper servers. Every subsystem is modular, asynchronous, and backed by Exposed + HikariCP storage.

## Key Features
- Unified manager layer for core gameplay systems
- Automatic PlaceholderAPI expansion and Vault hooks when available
- YAML-driven configuration with versioned resource processing

## Quick Start
1. Download the latest Pulse jar and drop it in `plugins/`.
2. Start your server to generate `plugins/Pulse/` defaults.
3. Tweak the YAML configs and reload with `/pulse reload`.

## Build Locally
```bash
# Windows
gradlew.bat shadowJar

# macOS / Linux
./gradlew shadowJar
```
The shaded jar is created at `build/libs/Pulse-1.0-all.jar`.