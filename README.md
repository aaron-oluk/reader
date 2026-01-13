# PDF Reader - Android Application

A simple and elegant PDF reader and library manager for Android, built with Java.

## Features

- **PDF Library**: Browse all PDF files from your Downloads and Documents folders
- **PDF Reader**: Read PDFs with smooth scrolling and page navigation
- **File Picker**: Add PDFs from anywhere on your device
- **Page Counter**: Track your reading progress with page numbers
- **Auto-refresh**: Library automatically refreshes when returning to the app
- **Material Design**: Clean and modern UI with Material Design components

## Technical Details

- **Language**: Java
- **Min SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 33 (Android 13)
- **PDF Library**: [AndroidPdfViewer](https://github.com/barteksc/AndroidPdfViewer) by Barteksc

## Project Structure

```
app/
├── src/main/
│   ├── java/com/pdfreader/app/
│   │   ├── MainActivity.java          # Main library activity
│   │   ├── PdfReaderActivity.java     # PDF reading activity
│   │   ├── PdfBook.java               # PDF book model
│   │   └── PdfBookAdapter.java        # RecyclerView adapter
│   ├── res/
│   │   ├── layout/                    # XML layouts
│   │   ├── values/                    # Strings, colors, themes
│   │   └── menu/                      # Menu resources
│   └── AndroidManifest.xml
├── build.gradle                       # App-level Gradle config
└── proguard-rules.pro
```

## Building the Project

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on an emulator or physical device

## Permissions

The app requires storage permissions to access PDF files:
- `READ_EXTERNAL_STORAGE`
- `WRITE_EXTERNAL_STORAGE`
- For Android 11+: `MANAGE_EXTERNAL_STORAGE`

## Usage

1. **Browse Library**: View all PDFs in your Downloads and Documents folders
2. **Add PDF**: Tap the + button to pick a PDF from anywhere
3. **Read PDF**: Tap any PDF in the library to open it
4. **Navigate**: Use swipe gestures or the scroll handle to navigate pages
5. **Refresh**: Use the refresh icon to update the library

## Dependencies

- AndroidX AppCompat
- Material Components
- RecyclerView
- CardView
- ConstraintLayout
- AndroidPdfViewer (Barteksc)

## License

This is a sample project for educational purposes.
