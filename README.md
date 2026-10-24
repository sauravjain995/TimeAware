# TimeAware Android

TimeAware is a local-only Android prototype for daily app-usage awareness. It includes a dark dashboard, today's usage summary, a most-used-app list, permission status, and a floating overlay that attempts to show the current foreground app and its accumulated time today.

## Build online without Android Studio

The repository includes `.github/workflows/build-apk.yml`. Upload the project files (not just the ZIP) to GitHub, then open **Actions → Build TimeAware APK → Run workflow**. On success, download the `TimeAware-debug-APK` artifact and extract the APK.

## Install and grant permissions

1. Install the debug APK on your Android phone.
2. Open TimeAware.
3. Tap **Grant** next to Usage access and enable TimeAware in Android's Usage access settings.
4. Return to TimeAware.
5. Tap **Grant** next to Display over other apps and allow it.
6. Return to the app and tap **Enable floating timer**.
7. Switch to another app. The pill should appear near the top. Return to TimeAware and tap **Stop floating timer** to stop it.

## Important limitations

- This is an early-stage, unsigned debug build, not a production release.
- Android vendors may restrict background services; behavior can vary by phone model and battery settings.
- Android's usage APIs report aggregated foreground time and may update with some delay. This is not a precision stopwatch and may not exactly match Digital Wellbeing.
- The floating service uses Android UsageEvents to infer the foreground app. Some system screens, launchers, split-screen, and OEM behavior may not be tracked perfectly.
- The project has not been device-tested in this environment. A successful cloud build only confirms compilation; verify permissions and overlay behavior on the target phone.
- No account or server is required; usage data is read locally.
