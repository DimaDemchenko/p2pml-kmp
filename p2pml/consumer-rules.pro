# androidx.media3 is a compileOnly dependency: consumers who use initialize(ExoPlayer)
# already have it, and consumers who supply a custom PlaybackProvider must not be forced
# to pull it in. For the latter, R8 (full mode) would otherwise fail with "missing class"
# warnings on the ExoPlayer references in ExoPlayerPlaybackProvider and the initialize
# overload. Those references are only reachable when the consumer actually passes an
# ExoPlayer, in which case media3 is present.
-dontwarn androidx.media3.**
