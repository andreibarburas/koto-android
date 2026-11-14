# OkHttp / okio reference some optional classes (Conscrypt, BouncyCastle, etc.)
# that aren't on the classpath -- suppress warnings for those, they're fine to miss.
-dontwarn okhttp3.**
-dontwarn okio.**

# org.json is used directly for all sidecar JSON -- keep it intact.
-keep class org.json.** { *; }

# The iCalendar parser works on plain strings and data classes with no reflection, but the
# VTodo model is round-tripped through Room entities -- keep both intact.
-keep class com.brbrs.koto.data.** { *; }
-keep class com.brbrs.koto.ical.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# XmlPullParser is used for all CalDAV multistatus parsing.
-keep class org.xmlpull.** { *; }
-dontwarn org.xmlpull.**

# Hilt -- keep generated components and annotated entry points.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}
-keepattributes *Annotation*

# Coroutines -- keep names (not full bodies) so stack traces and debug metadata stay readable.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-dontwarn kotlinx.coroutines.debug.**
