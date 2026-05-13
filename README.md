
SenseSwap:
GitHub: https://github.com/ChaldS-mods/SenseSwap
Author: ChaldS


Building from source
Requirements

Java 21 — [download](https://adoptium.net/temurin/releases/?version=21)

Windows

Download Gradle 8.6 → binary-only zip
Unpack to C:\gradle-8.6
Open terminal in the project folder and run:

C:\gradle-8.6\bin\gradle.bat build
Linux / Mac

Install Gradle 8.6:

bashwget https://services.gradle.org/distributions/gradle-8.6-bin.zip
unzip gradle-8.6-bin.zip

Build:

bash./gradle-8.6/bin/gradle build
If you already have Gradle 8.6 in PATH
bashgradle build
Output: build/libs/senseswap-3.0.0.jar

SenseSwap is a Fabric mod for Minecraft 1.21.1 that turns any multiplayer session into a chaotic and hilarious social challenge. Three players are assigned secret roles — Blind, Deaf, and Mute — each with a unique handicap that forces the team to communicate and cooperate in creative ways.

How it works
An admin runs /ss start and the mod automatically assigns one of three roles to each player:

🔴 Blind — your screen is covered in darkness. You can see almost nothing.
🔵 Deaf — all voice chat audio is completely blocked. You cannot hear your teammates.
🟢 Mute — your microphone is silenced. You cannot speak at all.

The catch? You still need to work together to survive or complete objectives. Figure out how to communicate when one of you can't see, one can't hear, and one can't talk.

Features

Automatic random role assignment with /ss start
Full Simple Voice Chat integration — Mute blocks your mic, Deaf blocks incoming audio at the engine level
Roles are server-authoritative — clients cannot spoof or change their own role
Roles persist across reconnects — rejoin and your role is automatically restored
Roles saved per-world in senseswap_roles.json
In-game HUD overlay showing your current role
Role popup screen shown when you receive a role
Full settings GUI (press K) — adjust HUD position, scale, fog intensity, and more
ModMenu support — configure the mod from the mods list
Config saved to config/senseswap.json


Languages

🇬🇧 English
🇷🇺 Русский (Russian)
🇰🇿 Қазақша (Kazakh)


Requirements

Fabric Loader ≥ 0.15.0
Fabric API
Simple Voice Chat

Optional: ModMenu

Commands (requires OP level 2)
CommandDescription/ss startStart the game and assign roles randomly/ss stopStop the game and clear all roles/ss setrole <player> <BLIND|DEAF|MUTE>Manually assign a role/ss clearrole <player>Remove a player's role/ss statusShow current role assignments/ss listAlias for status/ss reloadReload roles from world save

Made by ChaldS
