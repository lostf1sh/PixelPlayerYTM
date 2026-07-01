# PixelPlayerYTM

A from-scratch **YouTube Music** client for Android — written for personal use.
It talks to YouTube's private InnerTube API directly (no NewPipe, no server), plays
via ExoPlayer/Media3, and supports signing in with a Google account for your
personal library.

> Personal project. Not affiliated with, endorsed by, or connected to YouTube or Google.

## Features

- **Anonymous browsing & playback** — search, home feed, Explore, moods/genres,
  album/artist/playlist pages, and full track playback with no account.
- **Stream resolution in-app** — resolves `signatureCipher` and the `n`
  throttling parameter by running the functions from YouTube's `base.js` in an
  embedded Duktape JS engine. Client fallback order:
  `ANDROID_MUSIC → WEB_REMIX → TVHTML5`.
- **Radio & endless queue** — start radio from any song/artist; the queue keeps
  extending as you listen.
- **Google sign-in** — YouTube TV device-code OAuth (visit `google.com/device`,
  enter a code). Unlocks your playlists, liked songs, albums, and artists, plus
  higher-quality audio.
- **Now playing** — mini player + full sheet with queue, like, shuffle, repeat.

## Architecture

```
data/innertube/     InnerTube client, request/response models, endpoint parsers
data/stream/        base.js extraction + Duktape cipher, StreamResolver
data/youtube/auth/  TV OAuth, token/cookie store, auth interceptor
data/repository/    YouTubeRepository — parsed domain pages
domain/model/       Track/Album/Artist/Playlist + page models
playback/           ResolvingDataSource, MediaSessionService, PlayerController
ui/                 Compose screens (Home/Explore/Library/Search/detail/player)
```

**Auth note:** when an OAuth Bearer token is present, the interceptor strips the
`?key=` API-key query param — sending both makes Google authenticate via the key
and ignore the token, dropping the request to anonymous (empty library).

## Build

Requires JDK 21 and the Android SDK (compileSdk 37).

```sh
./gradlew :app:assembleDebug
```

The APK is at `app/build/outputs/apk/debug/`.

## Maintenance

InnerTube client versions (`data/innertube/model/YouTubeClient.kt`) and the
`base.js` extraction regexes (`data/stream/PlayerBaseJsExtractor.kt`) drift over
time. If requests start returning 400s or playback breaks with 403s, refresh
those first.
