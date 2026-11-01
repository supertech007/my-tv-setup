# My TV

A glass-style home screen (launcher) for Android TV, built for the Xiaomi Mi TV Stick 4K (Android TV 11) and
similar sticks. Kotlin and Jetpack Compose.

## What it does

- **Home:** clock and weather, a For you panel, Swipe, Music, Jarvis (AI assistants), YouTube and an app dock.
- **For you:** films and series from your streaming apps (Prime Video, JioHotstar, SonyLIV, ZEE5, Apple TV),
  filtered by audio language, sorted by IMDb or newest, with genre filters and an optional Personalise switch
  that learns from what you accept, reject and play. **Open** shows it full screen.
- **Swipe:** right accepts, left rejects, OK plays.
- **Music:** JioSaavn featured playlists in the languages you choose.
- **Direct play:** JioHotstar and Prime Video titles start playing straight away where the app allows it.
- **Dock:** hold OK on an app to move it or take it off the dock.
- **Settings:** name, city, wallpaper, accent colour, cards, dock, languages and music app.

## Data sources

- **JustWatch:** the film and series catalogue, audio languages, IMDb scores and posters.
- **TMDB** (optional, free key): original language and keywords.
  This product uses the TMDB API but is not endorsed or certified by TMDB.
- **JioSaavn:** featured playlists and cover art.
- **Open-Meteo:** weather and city search.

JustWatch and JioSaavn are read through the internal endpoints their own websites use. They are not official
public APIs, have no guarantees and may change or stop working at any time; if titles or playlists stop
loading, those endpoints have probably changed. This is a personal, non-commercial project and is not
affiliated with any of these services or with the streaming apps it opens.

## Build and install

1. Install JDK 17 and the Android SDK, and turn on network debugging (adb) on the TV.
2. Optional: copy `secrets.properties.example` to `secrets.properties` and fill in a TMDB key, and a Google
   "TVs and limited input devices" OAuth client for YouTube accounts.
3. Install on the TV:
   ```bash
   TV=192.168.1.20:5555 scripts/install.sh
   ```
4. On the TV press Home and choose **My TV → Always**.

## Switching back

On the TV: Settings → Apps → Default apps → Home app, and pick your previous launcher. Nothing else on the TV is
changed or removed.

## Tests

```bash
./gradlew testDebugUnitTest
```

## Licence

MIT, see [LICENSE](LICENSE). Wallpapers and the Outfit font have their own licences, listed in
[docs/credits.md](docs/credits.md).
