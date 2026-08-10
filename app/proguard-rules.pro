# Shizuku
-keep class rikka.shizuku.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.autoclicker.app.data.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
