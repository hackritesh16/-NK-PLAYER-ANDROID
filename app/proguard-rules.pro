# Keep Android components instantiated by the framework.
-keep public class com.nkplayer.app.NKPlayerApplication { *; }
-keep public class com.nkplayer.app.PlaybackService { *; }
-keep public class com.nkplayer.app.MainActivity { *; }
# Media3 uses service metadata and player extensions at runtime.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
