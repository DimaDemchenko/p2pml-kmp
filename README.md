# p2pml-kmp

[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-lightgrey)](#requirements)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![p2p-media-loader](https://img.shields.io/badge/p2p--media--loader-v4.0.0-orange)](https://github.com/Novage/p2p-media-loader)
[![JitPack](https://jitpack.io/v/DimaDemchenko/p2pml-kmp.svg)](https://jitpack.io/#DimaDemchenko/p2pml-kmp)
[![SPM](https://img.shields.io/badge/SPM-compatible-brightgreen.svg?logo=swift)](#installation)
[![CI](https://img.shields.io/github/actions/workflow/status/DimaDemchenko/p2pml-kmp/pr.yml?branch=main&label=CI)](https://github.com/DimaDemchenko/p2pml-kmp/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Kotlin Multiplatform SDK that adds peer-to-peer segment delivery to native HLS playback on
Android and iOS, powered by [Novage p2p-media-loader](https://github.com/Novage/p2p-media-loader)
(bundled engine: core 4.0.0).

## How it works

The loader runs a loopback HTTP proxy (Ktor) and a headless WebView hosting the p2p-media-loader
JS engine. You hand the player a proxied manifest URL; the proxy rewrites the HLS playlist so
segment requests flow through it, serves segments from the P2P swarm (WebRTC) when peers have
them, and falls back to plain HTTP when they don't. If the loader ever fails, play the origin
URL directly — playback never depends on P2P.

- HLS only (multivariant and media playlists, live and VOD). DASH is not supported.
- Low-Latency HLS: blocking playlist reloads are relayed to the origin, delta updates are
  disabled (the `CAN-SKIP-*` attributes are stripped from proxied playlists), and partial
  segments load directly from the origin without P2P — only full segments are shared.
- One active stream per loader instance; use one instance per concurrent stream.
- Instances are single-use: initialize → play → release → discard.

## Requirements

- **Android**: minSdk 24. Calling `initialize(exoPlayer)` requires your app to depend on
  `androidx.media3:media3-exoplayer` (1.10.1 or newer) — the library compiles against it but
  does not ship it. Apps that use a custom `PlaybackProvider` do not need media3 at all and
  build cleanly without it. P2P (WebRTC) traffic usually cannot reach other peers from an
  Android emulator, whose virtual network sits behind NAT — verify peer connectivity on a
  physical device.
- **iOS**: AVPlayer is supported out of the box (the demo targets iOS 15.3+). Swift interop is
  generated with [SKIE](https://skie.touchlab.co/): suspend functions become `async`, flows
  become `AsyncSequence`.

## Installation

Android and iOS use different package managers, so the library ships through both. One tag
produces both artifacts with the same version number.

**Android — JitPack:**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
implementation("com.github.DimaDemchenko.p2pml-kmp:p2pml:0.1.0")
```

The group id is `com.github.<owner>.<repo>` — JitPack's convention for multi-module projects,
and not the same as the `com.github.<owner>:<repo>` form its front page suggests. This
coordinate resolves through Gradle module metadata, so an Android app gets the AAR and a
Kotlin Multiplatform project can depend on it from `commonMain`.

**Android — required app configuration:**

The loader serves the rewritten playlist and segments from a loopback HTTP server, so the
player talks to `127.0.0.1` over cleartext. Android 9+ blocks that by default and the library
ships no manifest of its own, so add both of these or playback never starts:

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />

<application android:networkSecurityConfig="@xml/network_security_config" ... >
```

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```

**iOS — Swift Package Manager:**

```swift
.package(url: "https://github.com/DimaDemchenko/p2pml-kmp.git", from: "0.1.0")
```

Resolve a tag, never a branch. The package is a binary target pointing at an XCFramework
attached to each GitHub release, and only tags carry a checksum matching that release —
`branch: "main"` will not resolve.

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
engine applies its own defaults; see the class KDoc for the semantics, and the engine's
[CoreConfig reference](https://novage.github.io/p2p-media-loader/docs/latest/types/p2p-media-loader-core.CoreConfig.html)
for what each one does.

## Events

`loader.p2pEvents` exposes engine events as hot flows — segment lifecycle, peer and tracker
activity, per-chunk transfer stats, and stream registration failures. Collecting a flow
subscribes the engine to that event; a flow nobody collects never emits. Every stream completes
once the loader reaches a terminal state, so a Swift `for await` loop ends after `release()`
instead of suspending forever.

A stream that fails to register stays unknown to the engine: it still plays, but over plain HTTP
with no P2P sharing. `onStreamRegistrationError` is the only signal that this happened — worth
collecting if you report P2P efficiency.

## Custom engine page

By default the engine page and core bundle are served from bundled assets. A custom page can be
hosted instead via `customEngineUrl`; it must implement this library version's bridge contract
(readiness via `onWebViewLoaded`, `initP2P` acknowledged with `onCoreInitialized` /
`onCoreInitFailed`). Build it from `p2pml/src/assets` to stay in sync.

## Project layout

- `p2pml` — the KMP library (`commonMain` / `androidMain` / `iosMain`)
- `androidDemo`, `iosDemo` — demo players

## Status

Pre-1.0. APIs may change without deprecation cycles between minor versions.

## License

Licensed under the [Apache License 2.0](LICENSE). The bundled
[Novage p2p-media-loader](https://github.com/Novage/p2p-media-loader) engine is also
licensed under Apache-2.0.
