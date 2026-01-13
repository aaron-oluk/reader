# Reader App - UI Design Implementation

## Overview
This Android reading app features a modern, clean UI design with a blue and white color scheme, focusing on tracking reading habits, managing books, and providing insights into reading progress.

## Architecture

### Main Components

#### 1. **Bottom Navigation Structure**
- **Home**: Dashboard with current reading status and daily goals
- **Library**: Book collection with filtering and search
- **Scan**: Document scanner for capturing pages and quotes
- **Stats**: Reading insights and analytics
- **Profile**: User settings and preferences

### Data Models

Located in `app/src/main/java/com/pdfreader/app/models/`:

- **Book.java**: Book information including title, author, pages, progress
- **ReadingSession.java**: Active reading session tracking
- **ReadingStats.java**: User reading statistics and goals
- **UserProfile.java**: User information and preferences

### UI Screens

#### Home Screen (`fragment_home.xml`)
Features:
- Personalized greeting based on time of day
- Current reading streak display (days)
- Currently reading book card with:
  - Book cover image
  - Title and author
  - Progress bar (percentage)
  - "Start Reading Session" button
- Daily goals section:
  - Time spent (circular progress, target: 60m)
  - Pages read (circular progress, target: 30p)
- "Up Next" horizontal book carousel

#### Library Screen (`fragment_library.xml`)
Features:
- Tab navigation: All | Reading | To-Read | Finished
- Search bar for filtering books
- Grid layout (2 columns) displaying books with:
  - Book cover
  - Title and author
  - Progress percentage badge
- Floating action button to add new books

#### Reading Session (`activity_reading_session.xml`)
Features:
- Book title and author header
- Large timer display (HH:MM:SS format)
- Current page tracker with target
- "Capture a Thought" note field
- Pause and End Session buttons

#### Insights/Stats Screen (`fragment_insights.xml`)
Features:
- Monthly volume card (pages read)
- Quick stats row:
  - Books finished
  - Reading speed (words per minute)
  - Current streak (days)
- Weekly progress chart
- 2024 reading goal progress bar
- Personal habits section:
  - Reading time preference (e.g., "Evening Reader")
  - Genre preference (e.g., "Fiction Enthusiast")
- Export report button

#### Document Scanner (`fragment_scanner.xml`)
Features:
- Live camera preview
- Scan frame with corner guides
- Mode tabs: Scan Quote | Scan Page | Sign
- Capture button
- Gallery and settings access

## Color Scheme

Defined in `res/values/colors.xml`:

### Primary Colors
- **primary_blue**: `#2B7FED` - Main brand color
- **primary_blue_dark**: `#1E5DB8` - Pressed states
- **primary_blue_light**: `#5FA4F5` - Highlights

### Background Colors
- **background_white**: `#FFFFFF` - Card backgrounds
- **background_light_gray**: `#F5F7FA` - Screen backgrounds

### Text Colors
- **text_primary**: `#1A1A1A` - Main text
- **text_secondary**: `#8E8E93` - Secondary text
- **text_tertiary**: `#C7C7CC` - Disabled/hint text

### Status Colors
- **status_active**: `#2B7FED` - Active reading
- **status_paused**: `#FF9500` - Paused
- **status_completed**: `#34C759` - Completed

## Key Features

### 1. Reading Streak Tracking
Displays consecutive days of reading to encourage daily habits.

### 2. Daily Goals
- Time-based goals (minutes)
- Page-based goals (pages)
- Visual circular progress indicators

### 3. Reading Sessions
- Timer to track reading duration
- Page progress tracking
- Note-taking capability during reading

### 4. Statistics & Insights
- Monthly reading volume
- Reading speed calculation
- Personal reading patterns (time of day, genre preferences)
- Yearly reading goals with progress tracking

### 5. Document Scanning
- Camera-based text capture
- Quote extraction
- Page scanning
- Digital signature support

## Dependencies

Added in `app/build.gradle`:

```gradle
// Navigation components
implementation 'androidx.navigation:navigation-fragment:2.7.0'
implementation 'androidx.navigation:navigation-ui:2.7.0'

// ViewPager2 for tabs
implementation 'androidx.viewpager2:viewpager2:1.0.0'

// CameraX for document scanning
implementation 'androidx.camera:camera-core:1.2.3'
implementation 'androidx.camera:camera-camera2:1.2.3'
implementation 'androidx.camera:camera-lifecycle:1.2.3'
implementation 'androidx.camera:camera-view:1.2.3'

// Charts for statistics
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

// Image loading
implementation 'com.github.bumptech.glide:glide:4.15.1'
```

## File Structure

```
app/src/main/
├── java/com/pdfreader/app/
│   ├── models/
│   │   ├── Book.java
│   │   ├── ReadingSession.java
│   │   ├── ReadingStats.java
│   │   └── UserProfile.java
│   ├── fragments/
│   │   ├── HomeFragment.java
│   │   ├── LibraryFragment.java
│   │   ├── ScannerFragment.java
│   │   ├── InsightsFragment.java
│   │   └── ProfileFragment.java
│   ├── MainActivityNew.java
│   └── ReadingSessionActivity.java
├── res/
│   ├── layout/
│   │   ├── activity_main_new.xml
│   │   ├── fragment_home.xml
│   │   ├── fragment_library.xml
│   │   ├── fragment_scanner.xml
│   │   ├── fragment_insights.xml
│   │   └── activity_reading_session.xml
│   ├── drawable/
│   │   ├── progress_bar_horizontal.xml
│   │   ├── circular_progress.xml
│   │   ├── button_outline.xml
│   │   └── [various icons]
│   ├── menu/
│   │   └── bottom_navigation_menu.xml
│   └── values/
│       └── colors.xml
└── AndroidManifest.xml
```

## Getting Started

### 1. Launch the App
The app will start with `MainActivityNew` showing the Home screen.

### 2. Navigation
Use the bottom navigation bar to switch between sections.

### 3. Start Reading
- Tap "Start Reading Session" from the home screen
- Timer starts automatically
- Track your progress and take notes

### 4. View Statistics
- Navigate to the Stats tab
- See your reading insights and patterns
- Export reports for sharing

### 5. Manage Library
- Navigate to Library tab
- Filter books by status
- Search and add new books

## TODO / Future Enhancements

1. **Book Adapters**: Implement RecyclerView adapters for book grids and carousels
2. **Data Persistence**: Add database (Room) for storing books and sessions
3. **Charts Implementation**: Integrate MPAndroidChart for weekly progress visualization
4. **Camera Implementation**: Complete CameraX integration for document scanning
5. **Profile Screen**: Design and implement user profile management
6. **Notifications**: Add reading reminders and streak notifications
7. **Book Import**: Support for importing EPUB, PDF, and other formats
8. **Cloud Sync**: Optional cloud backup and sync across devices
9. **Social Features**: Reading challenges and friend comparisons
10. **Themes**: Dark mode support

## Design Philosophy

The UI follows modern Material Design principles with:
- **Clean aesthetics**: Minimal visual clutter
- **Card-based layouts**: Clear content separation
- **Consistent spacing**: 20dp padding, 16dp card radius
- **Readable typography**: Clear hierarchy with size and weight
- **Actionable colors**: Blue for primary actions, orange for warnings
- **Progress visualization**: Circular and linear progress indicators
- **Touch-friendly**: Large tap targets (minimum 48dp)

## Notes

- The app uses `MainActivityNew` as the entry point
- Legacy `MainActivity` is kept for compatibility but not used as launcher
- Camera permission required for scanner feature
- JitPack repository added for chart library support
