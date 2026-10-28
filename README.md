# Workouts

An Android app for following a Streek training plan day by day, with the
session videos playing **offline from your own downloaded copies**: no Vimeo,
no logging in, no signal needed.

Open it and it shows today: the run with its prescription broken into parts,
the strength workout, and the foam rolling. Each session has a tick box and,
where there's a video, a play button. Swipe for other days.

| Tab | What's there |
| --- | --- |
| **Today** | Today's sessions, swipe left/right through the plan. Tick sessions off; a video that plays to the end ticks itself. Link to the week's coach's notes. |
| **Plan** | Every week of the plan with dates, distances and what's done. Tap a day to open it. Each week's notes (Info / Technique / Nutrition / Gear) and the plan's glossary are here too. |
| **Videos** | All 23 session videos, playable directly, and where the app found each one. |
| **Settings** | Plan, race date, name, race distance, and the morning reminder. |

Also: a **morning notification** with the day's workout (skipped on rest days),
and a **home screen widget** showing today with its ticks.

## Install

Every push builds the app on GitHub Actions and publishes it as a release.

1. On the phone, open **github.com/nwbort/workouts-app/releases** (signed in,
   as the repo is private).
2. Tap the newest `workouts-1.0.N.apk` and install it. Android asks once to
   allow installs from your browser.

Each build has a higher version number and is signed with the same key, so a
new APK installs over the old one and keeps your ticks and settings. Builds
from branches other than `main` are marked *pre-release* but install the same
way. The APK is also attached to each run on the Actions tab.

## First run

It opens on the **UTA100 plan as "Kepler Challenge"** with the race on
**Saturday 5 December 2026**, the same as the Running calendar. Change any
of that under Settings. All 57 extracted Streek plans are in the app.

Then connect your videos:

1. **Videos → Choose folder**, and pick the folder the videos are in (for
   example `Movies/Streek`). Android remembers the choice, so this is a
   one-off, and the app needs no storage permission.
2. The app matches files to sessions by name: the streek downloader names
   them after the session title (`strength-workout-9.mp4`), and matching
   ignores case, spaces and punctuation, so `Strength Workout 9.mp4` works
   too. Subfolders are searched as well.
3. Anything it can't match: **⋮ → Link a file…** on that video.

`Movies` is a safer home than `Download`, which storage cleaners treat as
disposable.

## How it works

- **The plans are built in**, copied from the
  [streek](https://github.com/nwbort/streek) repo's extracted data by
  `tools/import_streek.py`. That script reuses streek's own code for day titles
  ("Run 10km + Strength") and video file names, so the app, the calendar
  export and the downloaded files all agree.
- **Dates are worked out the same way as the calendar tool.** A plan is
  anchored by its race day and counted back from it. A plan without a race
  runs forward from its start date.
- **Videos** are read through Android's Storage Access Framework (a persisted
  folder grant) and played with Media3 ExoPlayer in a full-screen player that
  keeps going when you rotate the phone. If you stop partway through, it picks
  up where you left off for the next 12 hours.
- **Ticks and settings** are stored on the phone (DataStore), keyed by the
  session's place in the plan, so changing the race date keeps them.

Kotlin, Jetpack Compose, Material 3 (Material You colours on Android 12+),
Glance for the widget, with a live preview in the widget picker. Needs
Android 16 or newer, so any Pixel still getting updates (Pixel 6 onwards); it
targets Android 17.

## Updating the plan data

If the plans in the streek repo are re-extracted, with streek checked out
next to this repo:

```bash
python3 tools/import_streek.py            # or --streek path/to/streek
```

This rewrites `app/src/main/assets/`. Commit the result and push; CI builds the
new APK.

## Building

GitHub Actions (`.github/workflows/build.yml`) runs on every push. It runs the
tests and builds the release APK. Then it installs that APK on an Android 16
emulator, opens it, and checks the app is still running 20 seconds later
(screenshot and logcat in the `launch` artifact). Only then does it publish
the release, keeping the ten newest.

Locally, with JDK 21 and the Android SDK:

```bash
./gradlew testDebugUnitTest assembleRelease
```

The tests check the plan data against the real calendar (28 October 2026 is
week 11, "Run 10km + Strength"). They also drive every screen under
Robolectric and save a screenshot of each to `app/build/screenshots/`; CI
uploads those as the `screenshots` artifact.

The plans and videos are paid Streek content. Keep this repo private.
