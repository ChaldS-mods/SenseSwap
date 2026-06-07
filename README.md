# SenseSwap 4.0.0

> **GitHub:** https://github.com/ChaldS-mods/SenseSwap  
> **Author:** ChaldS

---

## 🔨 Building from source

### Requirements

- **Java 21** — [download](https://adoptium.net/temurin/releases/?version=21)

### Windows

1. Download [Gradle 8.6](https://gradle.org/releases/) → binary-only zip
2. Unpack to `C:\gradle-8.6`
3. Open terminal in the project folder and run:
```bat
C:\gradle-8.6\bin\gradle.bat build
```

### Linux / Mac

1. Install Gradle 8.6:
```bash
wget https://services.gradle.org/distributions/gradle-8.6-bin.zip
unzip gradle-8.6-bin.zip
```
2. Build:
```bash
./gradle-8.6/bin/gradle build
```

Output: `build/libs/senseswap-4.0.0.jar`

---

## 📖 About

**SenseSwap** is a Fabric mod for Minecraft 1.21.1 that turns any multiplayer session into a chaotic and hilarious social challenge. Players are assigned secret roles — **Blind**, **Deaf**, **Mute** (and optionally **Dizzy**) — each with a unique handicap that forces the team to communicate and cooperate in creative ways.

---

## 🎮 How it works

An admin runs `/ss start` and the mod automatically assigns one of three (or four) roles to each player:

- 🔴 **Blind** — your screen is covered in darkness. You can see almost nothing.
- 🔵 **Deaf** — all voice chat audio is completely blocked. You cannot hear your teammates.
- 🟢 **Mute** — your microphone is silenced. You cannot speak at all.
- 🟣 **Dizzy** *(optional, 4th player)* — a pulsing border effect distracts your vision.

The catch? You still need to work together to survive or complete objectives. Figure out how to communicate when one of you can't see, one can't hear, and one can't talk.

---

## ✨ Features

- Automatic random role assignment with `/ss start`
- Full **Simple Voice Chat** integration — Mute blocks your mic, Deaf blocks incoming audio at the engine level
- Roles are **server-authoritative** — clients cannot spoof or change their own role
- Roles **persist across reconnects** — rejoin and your role is automatically restored
- Roles saved **per-world** in `senseswap_roles.json`
- In-game **HUD overlay** showing your current role
- **Role popup** screen shown when you receive a role
- Full **settings GUI** (press **K**) — adjust HUD position, scale, fog intensity, and more
- **ModMenu** support — configure the mod from the mods list
- Config saved to `config/senseswap.json`

### 🆕 New in 4.0.0

- **Score system** — players earn points at the end of each round based on their role (Blind = hardest, highest bonus). Scores persist between sessions in `senseswap_scores.json`
- **`/ss swap`** — instantly shuffle roles between players mid-game (great for mixing things up!)
- **Dizzy role** (optional) — a 4th role for 4-player games with a pulsing screen-border effect. Enable in settings
- **Configurable min players** — set minimum player count to 3 or 4 before `/ss start` works
- **`/ss score`** — view leaderboard | **`/ss score reset`** | **`/ss score add <player> <pts>`**

---

## 🌍 Languages

- 🇬🇧 English
- 🇷🇺 Русский (Russian)
- 🇰🇿 Қазақша (Kazakh)

---

## ⚙️ Requirements

- Fabric Loader ≥ 0.15.0
- [Fabric API](https://modrinth.com/mod/fabric-api) — Required
- [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) — Required
- [ModMenu](https://modrinth.com/mod/modmenu) — Optional

---

## 💻 Commands *(requires OP level 2)*

| Command | Description |
|---|---|
| `/ss start` | Start the game and assign roles randomly |
| `/ss stop` | Stop the game and clear all roles |
| `/ss swap` | **[NEW]** Randomly shuffle roles between current players |
| `/ss setrole <player> <BLIND\|DEAF\|MUTE\|DIZZY>` | Manually assign a role |
| `/ss clearrole <player>` | Remove a player's role |
| `/ss status` | Show current role assignments and phase info |
| `/ss list` | Alias for status |
| `/ss reload` | Reload roles from world save |
| `/ss score` | **[NEW]** Show leaderboard |
| `/ss score reset` | **[NEW]** Reset all scores |
| `/ss score add <player> <pts>` | **[NEW]** Add (or subtract) points manually |

---

## 🏆 Scoring (4.0.0)

Points are awarded automatically at the end of each game round (when phase switches to REST):

| Base reward | Role bonus |
|---|---|
| `scorePerRound` pts (default 10) | +5 Blind, +3 Deaf, +2 Mute, +1 Dizzy |

Scores are saved to `<world>/senseswap_scores.json` and persist across server restarts.

---

*Made by ChaldS*
