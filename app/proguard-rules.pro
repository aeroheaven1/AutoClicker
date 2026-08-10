# Shizuku
-keep class rikka.shizuku.** { *; }

# Shizuku 用户服务: 必须保留构造器供服务器反射实例化
-keep class com.autoclicker.app.service.ShellUserService { *; }
-keepclassmembers class com.autoclicker.app.service.ShellUserService {
    public <init>(...);
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.autoclicker.app.data.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
