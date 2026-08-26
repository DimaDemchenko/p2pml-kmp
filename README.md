# P2P Media Loader KMP

[![Android](https://img.shields.io/badge/Android-minSdk%2024-3DDC84?logo=android&logoColor=white)](#requirements)
[![iOS](https://img.shields.io/badge/iOS-15.0%2B-000000?logo=apple&logoColor=white)](#requirements)
[![p2p-media-loader](https://img.shields.io/badge/p2p--media--loader-v4.0.0-orange)](https://github.com/Novage/p2p-media-loader)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Release](https://img.shields.io/github/v/release/DimaDemchenko/p2pml-kmp?label=release&color=blue)](https://github.com/DimaDemchenko/p2pml-kmp/releases/latest)
[![JitPack](https://jitpack.io/v/DimaDemchenko/p2pml-kmp.svg)](https://jitpack.io/#DimaDemchenko/p2pml-kmp)
[![SPM](https://img.shields.io/badge/SPM-compatible-brightgreen.svg?logo=swift)](#installation)
[![CI](https://img.shields.io/github/actions/workflow/status/DimaDemchenko/p2pml-kmp/pr.yml?branch=main&label=CI)](https://github.com/DimaDemchenko/p2pml-kmp/actions)

**Every viewer becomes part of your delivery network.** p2pml-kmp adds peer-to-peer segment
delivery to the players you already use — ExoPlayer on Android, AVPlayer on iOS. Viewers
watching the same HLS stream exchange segments over WebRTC, offloading your CDN exactly when
demand peaks, and anything the swarm can't provide loads over plain HTTP — playback never
depends on P2P.

Powered by [Novage p2p-media-loader](https://github.com/Novage/p2p-media-loader)
(bundled engine: core 4.0.0), brought to Kotlin Multiplatform.

## Features

- **Native players, unmodified** — hand ExoPlayer or AVPlayer one proxied URL; no custom
  player stack, no forked media pipeline
- **Automatic HTTP fallback** — P2P accelerates delivery but never gates it; if the loader
  fails entirely, play the origin URL directly
- **Live and VOD HLS**, including Low-Latency HLS handling
- **Battle-tested engine** — the same p2p-media-loader core that powers Novage's web players
- **First-class Swift** via [SKIE](https://skie.touchlab.co/): suspend functions become
  `async`, flows become `AsyncSequence`
- **Java-friendly** — a `CompletableFuture`/listener facade over the same core

## How it works

```text
ExoPlayer / AVPlayer
        │ plays the proxied manifest URL
        ▼
loopback proxy (Ktor) ── rewrites playlists, serves segments
        │                        │
        ▼                        ▼
headless WebView          HTTP origin
(p2p-media-loader          (fallback)
 engine, WebRTC swarm)
```

The loader runs a loopback HTTP proxy and a headless WebView hosting the p2p-media-loader
JS engine. You hand the player a proxied manifest URL; the proxy rewrites the HLS playlist so
segment requests flow through it, serves segments from the P2P swarm (WebRTC) when peers have
them, and falls back to plain HTTP when they don't.

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
  build cleanly without it.
- **iOS**: 15.0 or newer. AVPlayer is supported out of the box.

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

**iOS — Swift Package Manager:**

```swift
.package(url: "https://github.com/DimaDemchenko/p2pml-kmp.git", from: "0.1.0")
```

Resolve a tag, never a branch. The package is a binary target pointing at an XCFramework
attached to each GitHub release, and only tags carry a checksum matching that release —
`branch: "main"` will not resolve.

## Platform setup

The player fetches rewritten playlists and segments from the loader's loopback server over
cleartext HTTP, and both platforms restrict cleartext by default — each needs one small,
loopback-scoped exception. Nothing is opened to the outside world.

**Android** — the library ships no manifest of its own, so the app must declare both of these
or playback never starts on Android 9+:

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

**iOS** — add an App Transport Security exception for the loopback address to the app's
`Info.plist` (this is exactly what the demo ships — scoped to `127.0.0.1`, not
`NSAllowsArbitraryLoads`):

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSExceptionDomains</key>
    <dict>
        <key>127.0.0.1</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
            <key>NSIncludesSubdomains</key>
            <true/>
        </dict>
    </dict>
</dict>
```

## Quick start

> [!IMPORTANT]
> P2P (WebRTC) usually cannot reach peers from an **Android emulator**, whose virtual network
> sits behind NAT — streams still play over HTTP fallback, but you will see no P2P traffic.
> Verify peer connectivity on a physical device. The iOS Simulator runs directly on the host
> Mac's network and is not affected.

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

**Java** — `P2PMediaLoaderJava` wraps the same core behind `CompletableFuture`s and listener
subscriptions:

```java
P2PMediaLoaderJava loader = new P2PMediaLoaderJava(new P2PMediaLoader(context));
loader.initialize(exoPlayer).thenRun(() -> {
    String url = loader.createPlaybackUrl("https://example.com/master.m3u8");
    // hand url to ExoPlayer on the main thread
});
```

Event listeners return `AutoCloseable` subscriptions and run on a background thread — switch
to the main thread before touching UI.

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

## Demos

Two complete players ship in this repo — the fastest way to see the loader in action and the
reference for every integration pattern above:

- [`androidDemo`](androidDemo) — Compose + ExoPlayer. Open the project in Android Studio and
  run it on a physical device.
- [`iosDemo`](iosDemo) — SwiftUI + AVPlayer. Open `iosDemo/iosDemo.xcodeproj` in Xcode; a
  build phase invokes Gradle to build the framework, so it just runs.

Both demos show event collection, the background P2P-disable pattern, and HTTP fallback.

## Custom engine page

By default the engine page and core bundle are served from bundled assets. A custom page can be
hosted instead via `customEngineUrl`; it must implement this library version's bridge contract
(readiness via `onWebViewLoaded`, `initP2P` acknowledged with `onCoreInitialized` /
`onCoreInitFailed`). Build it from `p2pml/src/assets` to stay in sync.

## Status

Pre-1.0. APIs may change without deprecation cycles between minor versions.

## License

Licensed under the [Apache License 2.0](LICENSE). The bundled
[Novage p2p-media-loader](https://github.com/Novage/p2p-media-loader) engine is also
licensed under Apache-2.0.
