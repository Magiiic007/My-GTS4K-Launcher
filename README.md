# Magiic TV

**A personal Arabic-first home-screen launcher for Google TV Streamer 4K, with prayer times, adhan and iqama, Quran radio, Hijri occasions and reminders.**

[![Build APK](https://github.com/Magiiic007/My-GTS4K-Launcher/actions/workflows/build.yml/badge.svg)](https://github.com/Magiiic007/My-GTS4K-Launcher/actions/workflows/build.yml)
[![Latest build](https://img.shields.io/badge/download-latest%20APK-2ea44f)](https://github.com/Magiiic007/My-GTS4K-Launcher/releases/download/latest/my-launcher.apk)

Magiic TV replaces the stock Google TV home screen with a remote-friendly dashboard built around the Muslim daily
routine: the next prayer and a live countdown, the Gregorian and Hijri dates, your favourite apps, background Quran
radio and gentle reminders. It is written in plain Android Views (Java) with no third-party UI framework, and it is
built to be light on the TV's limited hardware.

> **Status:** a personal project, developed and tested on a single Google TV Streamer 4K. It is shared as-is, with no
> warranty. See [Known limitations](#known-limitations).

---

## Contents

- [Features](#features)
- [Three looks](#three-looks)
- [Installation](#installation)
- [First-time setup](#first-time-setup)
- [Using it with the remote](#using-it-with-the-remote)
- [Updating](#updating)
- [Going back to the stock home screen](#going-back-to-the-stock-home-screen)
- [Building from source](#building-from-source)
- [Project layout](#project-layout)
- [Known limitations](#known-limitations)
- [Credits and licences](#credits-and-licences)

---

## Features

### Prayer times, adhan and iqama
- Offline prayer times using each country's official method (Egypt: Egyptian General Authority of Survey; Saudi
  Arabia: Umm al-Qura), with a live countdown to the next prayer.
- Per-prayer manual adjustment from -10 to +10 minutes to match your local mosque.
- **Adhan** appears as a full popup on top of whatever is playing, pauses the content, plays the adhan (a dedicated
  Fajr recording is supported) and then lets the content continue.
- **Iqama** with its own shorter alert and a configurable delay after each adhan (0 disables it).
- Friday support: first adhan reminder and a "Jumu'ah" label.
- The iqama counter runs only between the adhan and the iqama. Outside that window it cycles through short adhkar.
- Each prayer row shows the minutes remaining until the iqama.

### Dates and occasions
- Gregorian and Hijri (Umm al-Qura) dates with a manual correction of -3 to +3 days.
- Hijri occasions: Ramadan, Eid al-Fitr, Day of Arafah, Eid al-Adha, Hijri New Year, the first ten days of Dhul
  Hijja, Tasu'a, Ashura and the last ten nights of Ramadan.
- A countdown line under the prayer times alternates with "next prayer": Ramadan starts counting 30 days ahead, other
  occasions 10 days ahead ("N days until ...", "Tomorrow: ...", "Today: ...").
- During Ramadan the line shows the imsak and iftar times.
- An "Upcoming occasions" list shows each occasion with its Gregorian date and the days remaining.

### Reminders
Grouped by category, with all times and days edited inside each reminder (never crammed into the list):
- **Fasting**: Monday and Thursday, and the White Days (13th, 14th, 15th of the Hijri month), shown on the day or the
  day before.
- **Adhkar**: morning and evening.
- **Surat Al-Kahf** on Fridays.
- **Hijri occasions**: an alert at 22:00 the evening before, so you can prepare (for example, to fast).
- **Custom reminders** with your own title, text, days, time and display duration.

### Quran radio
- Live Quran radio stations and reciters, listed in separate groups.
- Keeps playing in the background while you use other apps; pauses automatically for the adhan and iqama and
  resumes afterwards.
- Resumes by itself after a reboot, an app update or a force stop.

### Home screen
- Launcher grid of all installed TV apps with reordering, hiding, uninstalling and an app-info shortcut (long-press
  OK on an app).
- Favourites row in the Mihrab look (long-press an app in *All apps* to add or remove it).
- Weather for Egyptian and Saudi cities via [Open-Meteo](https://open-meteo.com/) (no account needed).
- **Automatic city** from the network's IP location (the Streamer has no GPS, so accuracy is to the nearest city),
  or choose a country and city by hand.
- Network, Bluetooth and radio status icons.
- A daily Quran verse in the Mihrab look (complete verses, one per day).
- Shortcuts to the device settings, the screensaver settings and the system home.
- Arabic (right-to-left) and English, following the device language.

---

## Three looks

Switch between them from **Settings → Look of the home screen**.

| Look | Description |
| --- | --- |
| **Royal** | Green and gold, with ornamental Islamic frames. |
| **Kids** | Soft pink and friendly. |
| **Mihrab** | Navy and gold mihrab arch, six prayer cards, favourite apps and a daily verse. |

All popups and menus follow the selected look.

---

## Installation

You need a Google TV Streamer 4K (other Android TV / Google TV devices running Android 8.0 or later may also work,
but are untested).

**Download link (always the newest build):**

```
https://github.com/Magiiic007/My-GTS4K-Launcher/releases/download/latest/my-launcher.apk
```

### Option 1: with the Downloader app (no computer needed)
1. Install **Downloader** from the Google Play Store on the TV.
2. In the TV's settings, allow Downloader to **install unknown apps**.
3. Open Downloader, enter the link above and choose **Install**.

### Option 2: from your phone or computer
1. Download `my-launcher.apk` from the
   [Releases page](https://github.com/Magiiic007/My-GTS4K-Launcher/releases/tag/latest).
2. Send it to the TV with any file-transfer app (for example Send Files to TV) and open it there.

---

## First-time setup

1. **Open Magiic TV** from the apps list and grant the permissions it asks for. "Display over other apps" is needed
   for the adhan and reminder popups.
2. **Set your location:** open **Settings → Adhan & iqama settings** and use the *City and country* row to choose
   *Automatic* or pick your country and city by hand.
3. **Choose the adhan settings:** iqama delays per prayer, adjustment minutes, the first Friday adhan.
4. **Pick a look** under **Settings → Look of the home screen**.
5. *(Optional)* **Make it the home screen:** Settings → *Device settings* → *Home screen* → *Turn it on: open Accessibility settings* → **Magiic TV** →
   switch on and accept the notice. After that, the Home button opens Magiic TV.

Everything is saved on the device. No account is required and no data leaves the TV except the weather request and the
optional IP-based city lookup.

---

## Using it with the remote

- **D-pad** moves between items, **OK** opens, **Back** closes a menu or popup.
- The remote is active in the Settings menu and the apps row.
- **Long-press OK** on an app for move, hide, uninstall and info.
- The side menu contains: notices, adhan and iqama, radio, look, reminders, device settings, launcher update and
  permissions.

---

## Updating

From inside the app: **Settings → Update the launcher**. The app compares its version with the latest GitHub release and
downloads and installs the newer APK if there is one.

Every push to `main` is built automatically by GitHub Actions and published as the release named `latest`.

---

## Going back to the stock home screen

- From **Settings → Device settings → Home screen**: *Stop it being the home screen*.
- If the app will not open: **Settings → Accessibility → Magiic TV → Off**, or **Settings → Apps → Magiic TV →
  Uninstall**.

---

## Building from source

Requirements: JDK 17 and the Android SDK (the project uses Android Gradle Plugin 8.5.2, `minSdk 26`).

```bash
git clone https://github.com/Magiiic007/My-GTS4K-Launcher.git
cd My-GTS4K-Launcher
chmod +x gradlew
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

The workflow in [`.github/workflows/build.yml`](.github/workflows/build.yml) does the same on every push and
publishes the APK. Bump `versionCode` and `versionName` in `app/build.gradle` for every release, otherwise the in-app
updater will report that no newer version exists.

---

## Project layout

```
app/src/main/java/app/mylauncher/gtv/
  MainActivity.java       home screen, settings menu, dialogs
  PrayerBoard.java        Royal / Kids prayer board and counters
  MihrabHome.java         Mihrab look
  PrayerCalc.java         prayer time engine
  Reminders*.java         reminder model, scheduling and dialogs
  RadioService.java       background Quran radio
  Menu.java               themed dialog replacement for AlertDialog
  Hijri.java, Cities.java, AutoCity.java
app/src/main/res/         layouts, drawables, strings (ar / en), raw audio
```

---

## Known limitations

- Developed and tested on one device (Google TV Streamer 4K); behaviour on other TVs is untested.
- The Streamer has no GPS, so "automatic city" relies on IP geolocation and can be wrong if your internet provider's
  address is far from you. Pick the city manually if so.
- Cities available for weather and prayer times are Egypt and Saudi Arabia only.
- Hijri dates follow the Umm al-Qura calendar and may differ by a day from local moon sighting; use the Hijri
  adjustment to correct this.
- The screensaver shortcut cannot open Google Photos directly on every device; the app then tells you the manual path.
- The app is distributed as a debug-signed APK for personal use and is not on any app store.

---

## Credits and licences

- Prayer time calculation: [adhan-java](https://github.com/batoulapps/adhan-java) (MIT).
- Weather: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0).
- Layout idea for the Mihrab look: [Afnon Android TV Launcher](https://github.com/egyjs/afnon-android-tv-launcher)
  (MIT). No code or fonts were copied.
- Quran text: Tanzil Uthmani text via [risan/quran-json](https://github.com/risan/quran-json).
- Radio streams were taken from the original Afnon project; the stations are not affiliated with this app.
- Audio recordings (adhan, iqama, reminder) were added by the project owner for personal use and their rights are
  **not** verified. See [`THIRD_PARTY.md`](THIRD_PARTY.md) for sources and the takedown note.

## Licence

Copyright (c) 2026 Mahmoud Arafa. All rights reserved. See [LICENSE](LICENSE) for what is allowed, and
[THIRD_PARTY.md](THIRD_PARTY.md) for the third-party material and its licences.
