# Ad Astra

*Ad astra per aspera: to the stars through difficulties.*

Ad Astra is a personal Android app for staying consistent without burning out. It started as a paper notebook with a daily one-line journal, a habit grid and a hand-drawn sleep chart. The app keeps that system and adds a weekly review.

The rule is simple: **never a zero day.** You don't have to do everything. One habit, or a planned rest day, keeps the streak alive. Each day is compared with the day before it, never with a perfect day.

## Features

| Tab | What it does |
| --- | --- |
| **Today** | The evening check-in. Tick habits, mark a rest day, log last night's sleep and write one line about the day. Shows your streak of days without a zero, how today compares with yesterday, and one of your own lines. Only today can be edited. |
| **Grid** | A month at a glance, laid out like the notebook. Days run down the rows, habits run across the columns (with vertical labels), and sleep hours sit in the last column. Above it is the month's sleep curve. Tap a day to read it as a page, then swipe to turn to the next or previous day. Past days are read-only unless you hold the lock on a day page, which unlocks that one day for editing. |
| **Week** | The weekly review. Shows days without a zero, a 7-day row of squares, check-ins, average sleep and rest days. Then "What went well" comes first, then "What to work on", then each habit's 7 days compared with last week. You also set one focus for next week, which appears on Today. |
| **Words** | Your mantras and saved journal lines. They appear on Today and in the evening reminder. |
| **Setup** | Your name, the reminder time, and your habits: add, rename, reorder or hide them. Hidden habits keep their history. |

**Evening reminder.** If nothing is logged by the set time (9:30 PM by default), you get a notification with one of your own lines. It doesn't nag, and it doesn't show red marks.

**Like a notebook.** Text is set in the Kalam handwriting font, and switching tabs or days turns a page.

**Fair stats.** Days before you started using the app don't count as zeros. Today only counts once you log something. A day runs from 4 AM to 4 AM, so a check-in after midnight still counts for the day you just finished.

## Privacy

All data lives in one JSON file in the app's private storage on the phone (`files/adastra.json`). There's no account, no network access and no analytics. This repository contains only the app's code, not anyone's tracking data.

## Tech

- Kotlin 2.0, Jetpack Compose (Material 3), single activity
- kotlinx.serialization for the JSON store, with a `StateFlow` repository
- `AlarmManager` for the evening reminder, and a boot receiver that reschedules it after a restart
- Min SDK 26 (Android 8.0), target SDK 35
- Android Gradle Plugin 8.7, Gradle 8.11 (wrapper included), JDK 17

## Project layout

```
app/src/main/java/com/adastra/app/
  AdAstraApp.kt          Application: creates the repository and notification channel
  MainActivity.kt        Bottom navigation and the five tabs
  data/Models.kt         Habit, DayEntry, Word, Settings, AppData and the defaults
  data/Repository.kt     Loads and saves the JSON file; every edit goes through here
  data/Stats.kt          Streaks, weekly numbers and the weekly review text
  notify/Reminder.kt     Evening reminder scheduling, alarm and boot receivers
  ui/Theme.kt            Colors, type and shared pieces (Section, CheckSquare, sideways text)
  ui/PageTurn.kt         The page-turn animation
  ui/DayPagesScreen.kt   Read-only pages for past days
  ui/*Screen.kt          One file per tab
```

## Building

### With Android Studio

Open the folder, let it sync, then run the `app` configuration on a device or emulator.

### From the command line (Windows)

`build-apk.ps1` expects the toolchain under `L:\Android` (JDK 17 in `jdk17`, Android SDK in `sdk`, with platform 35 and build-tools 35). Edit the paths at the top of the script if yours are somewhere else.

```bat
build-apk.cmd
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

`local.properties` (which holds `sdk.dir`) isn't committed, so create it on a new machine:

```properties
sdk.dir=C\:\\path\\to\\Android\\sdk
```

### Installing on a phone

Turn on USB debugging, connect the phone, then run:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Updating the app keeps your data, but uninstalling it deletes the data.
