# p2pml-kmp

Kotlin Multiplatform SDK that adds peer-to-peer segment delivery to native HLS playback on
Android and iOS, powered by [Novage p2p-media-loader](https://github.com/Novage/p2p-media-loader)
(bundled engine: core 3.0.1).

## How it works

The loader runs a loopback HTTP proxy (Ktor) and a headless WebView hosting the p2p-media-loader
JS engine. You hand the player a proxied manifest URL; the proxy rewrites the HLS playlist so
segment requests flow through it, serves segments from the P2P swarm (WebRTC) when peers have
them, and falls back to plain HTTP when they don't. If the loader ever fails, play the origin
URL directly — playback never depends on P2P.

- HLS only (multivariant and media playlists, live and VOD). DASH is not supported.
- One active stream per loader instance; use one instance per concurrent stream.
- Instances are single-use: initialize → play → release → discard.

## Requirements

- **Android**: minSdk 24. Calling `initialize(exoPlayer)` requires your app to depend on
  `androidx.media3:media3-exoplayer` (1.10.1 or newer) — the library compiles against it but
  does not ship it. Apps that use a custom `PlaybackProvider` do not need media3 at all and
  build cleanly without it.
- **iOS**: AVPlayer is supported out of the box (the demo targets iOS 15.3+). Swift interop is
  generated with [SKIE](https://skie.touchlab.co/): suspend functions become `async`, flows
  become `AsyncSequence`.

## Quick start

**Android (Kotlin):**

```kotlin
val loader = P2PMediaLoader(context)
loader.initialize(exoPlayer) // suspend; throws P2PMediaLoaderException on failure
val url = loader.createPlaybackUrl("https://example.com/master.m3u8")
exoPlayer.setMediaItem(MediaItem.fromUri(url))

// on teardown
loader.release()
```

**iOS (Swift):**

```swift
let loader = P2PMediaLoader()
try await loader.initialize(player: avPlayer)
let url = try loader.createPlaybackUrl(manifestUrl: "https://example.com/master.m3u8")
avPlayer.replaceCurrentItem(with: AVPlayerItem(url: URL(string: url)!))

// release() is non-suspending and safe to call from deinit
```

Java apps can use `P2PMediaLoaderJava`, a `CompletableFuture`/listener facade over the same core.

### Lifecycle notes

- `initialize` is terminal on failure **and on cancellation**: the instance ends up
  FAILED/RELEASED and cannot be reused — create a new loader to retry.
- Observe `loader.state`; on FAILED the local proxy is gone — fall back to the origin URL.
- Backgrounded apps throttle WebView JavaScript, which stalls the engine. Disable P2P while
  in the background (both demos show the pattern):

  ```kotlin
  loader.applyDynamicConfig(DynamicCoreConfig().apply { isP2PDisabled = true })
  ```

## Configuration

`CoreConfig` (at construction) and `DynamicCoreConfig` (at runtime via `applyDynamicConfig`)
mirror the engine's configuration. Properties left unset are omitted from the payload and the
engine applies its own defaults; see the class KDoc for the semantics.

## Events

`loader.p2pEvents` exposes engine events as hot `SharedFlow`s — segment lifecycle, peer and
tracker activity, and per-chunk transfer stats. Collecting a flow subscribes the engine to that
event; a flow nobody collects never emits.

## Custom engine page

By default the engine page and core bundle are served from bundled assets. A custom page can be
hosted instead via `customEngineUrl`; it must implement this library version's bridge contract
(readiness via `onWebViewLoaded`, `initP2P` acknowledged with `onCoreInitialized` /
`onCoreInitFailed`). Build it from `p2pml/src/assets` to stay in sync.

## Project layout

- `p2pml` — the KMP library (`commonMain` / `androidMain` / `iosMain`)
- `androidDemo`, `iosDemo` — demo players

## Status

Pre-release. APIs may change without deprecation cycles until the first published version.
