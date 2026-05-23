# Pixel Music 🎵

<p align="center">
  <img src="assets/icon.png" alt="App Icon" width="128"/>
</p>

<p align="center">
  <strong>A beautiful, feature-rich music player for Android</strong><br>
  Built with Jetpack Compose and Material Design 3
</p>

<p align="center">
  <img src="assets/screenshot1.jpg" alt="Screenshot 1" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot2.jpg" alt="Screenshot 2" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot3.jpg" alt="Screenshot 3" width="200" style="border-radius:26px;"/>
  <img src="assets/screenshot4.jpg" alt="Screenshot 4" width="200" style="border-radius:26px;"/>
</p>

<p align="center">
    <img src="https://img.shields.io/badge/Android-10%2B-green?style=for-the-badge&logo=android" alt="Android 11+">
    <img src="https://img.shields.io/badge/Kotlin-100%25-purple?style=for-the-badge&logo=kotlin" alt="Kotlin">
</p>

---

## ‼️ DISCLAIMER
- No fork of this project will recieve support, if you use a fork, ask the forker to support you.

---

## ✨ Features

### 🎨 Modern UI/UX
- **Material You** - Dynamic color theming that adapts to your wallpaper
- **Smooth Animations** - Fluid transitions and micro-interactions
- **Customizable UI** - Adjustable corner radius and navigation bar settings
- **Dark/Light Theme** - Automatic or manual theme switching
- **Album Art Colors** - Dynamic color extraction from album artwork

### 🎵 Powerful Playback
- **Media3 ExoPlayer** - Industry-leading audio engine with FFmpeg support
- **Background Playback** - Full media session integration
- **Queue Management** - Drag-and-drop reordering
- **Shuffle & Repeat** - All playback modes supported
- **Gapless Playback** - Seamless transitions between tracks
- **Custom Transitions** - Configure crossfades between songs

### 📚 Library Management
- **Multi-format Support** - MP3, FLAC, AAC, OGG, WAV, and more
- **Browse By** - Songs, Albums, Artists, Genres, Folders
- **Smart Artist Parsing** - Configurable delimiters for multi-artist tracks
- **Album Artist Grouping** - Proper album organization
- **Folder Filtering** - Choose which directories to scan

### 🔍 Discovery & Organization
- **Full-text Search** - Search across your entire library
- **Daily Mix** - AI-powered personalized playlist based on listening habits
- **Playlists** - Create and manage custom playlists
- **Statistics** - Track your listening history and habits

### 🎤 Lyrics
- **Synchronized Lyrics** - LRC format via LRCLIB API
- **Lyrics Editing** - Modify or add lyrics to your tracks
- **Scrolling Display** - Follow along as you listen

### 🖼️ Artist Artwork
- **Deezer Integration** - Automatic artist images from Deezer API
- **Smart Caching** - Memory (LRU) + database caching for offline access
- **Fallback Icons** - Beautiful placeholders when images unavailable

### 📲 Connectivity
- **Chromecast** - Stream to your TV or smart speakers
- **Android Auto** - Full Android Auto support for in-car playback (Soon)
- **Widgets** - Home screen control with Glance widgets

### ⚙️ Advanced Features
- **Tag Editor** - Edit metadata with TagLib (MP3, FLAC, M4A support)
- **AI Playlists** - Generate playlists with AI (Supports Gemini, Deepseek, OpenAI, etc.)

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 100% |
| **UI Framework** | Jetpack Compose |
| **Design System** | Material Design 3 |
| **Audio Engine** | Media3 ExoPlayer + FFmpeg |
| **Architecture** | MVVM with StateFlow/SharedFlow |
| **DI** | Hilt |
| **Database** | Room |
| **Networking** | Retrofit + OkHttp |
| **Image Loading** | Coil |
| **Async** | Kotlin Coroutines & Flow |
| **Background Tasks** | WorkManager |
| **Metadata** | TagLib |
| **Widgets** | Glance |

---

## 📱 Requirements

- **Android 11** (API 30) or higher
- **6GB RAM** recommended for smooth performance

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug | 2024.2.1 or newer
- Android SDK 29+
- JDK 11+

### Setup

1. Open the project in Android Studio  
2. Wait for Gradle sync to complete  
3. Build the project  
4. Run on a physical device or emulator  

---

## 📂 Project Structure

```text
app/src/main/java/com/unshoo/pixelmusic/
├── data/
│   ├── database/       # Room entities, DAOs, migrations
│   ├── model/          # Domain models (Song, Album, Artist, etc.)
│   ├── network/        # API services (LRCLIB, Deezer)
│   ├── preferences/    # DataStore preferences
│   ├── repository/     # Data repositories
│   ├── service/        # MusicService, HTTP server
│   └── worker/         # WorkManager sync workers
├── di/                 # Hilt dependency injection modules
├── presentation/
│   ├── components/     # Reusable Compose components
│   ├── navigation/     # Navigation graph
│   ├── screens/        # Screen composables
│   └── viewmodel/      # ViewModels
├── ui/
│   ├── glancewidget/   # Home screen widgets
│   └── theme/          # Colors, typography, theming
└── utils/              # Extensions and utilities