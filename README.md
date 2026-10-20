# Sales Record Android App

A Java Android app for recording daily sales, inventory, collections, stock changes, and reports.

## Project status

This repository is a modern Gradle/Android Studio wrapper around the existing Sales Record source code.

### Main features already present in the source

- Daily sales entry
- Cash and credit transactions
- Partial payments and receivables
- Inventory tracking
- Stock ending/withdrawal records
- Daily/weekly sales summaries
- CSV/PDF export
- Local SQLite storage
- Backup/restore support
- Built-in update-check flow
- Dark/light UI support

## Build

Open the project in Android Studio and let Gradle sync. The project uses Android Gradle Plugin 9.4.0, Gradle 9.6.0, and JDK 17.

Build a debug APK from Android Studio, or with Gradle 9.6.0 installed:

```bash
gradle assembleDebug
```

The debug APK will be generated under `app/build/outputs/apk/debug/`. A GitHub Actions workflow is included to build the debug APK automatically on pushes and pull requests.

## GitHub

Do not commit generated `bin/`, `gen/`, Gradle build outputs, or `local.properties`. These are ignored by `.gitignore`.

The app currently contains a public Google Drive update-information URL in `MainActivity.java`. It does not contain a password or access token, but review/change it before publishing the repository if you do not want that update endpoint public.
