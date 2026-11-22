# PenOS 5.5 R8 / ProGuard Rules (MT6582 1GHz Optimized Release)

-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Keep Android components declared in AndroidManifest.xml
-keep public class com.example.PenOsApplication { *; }
-keep public class com.example.MainActivity { *; }
-keep public class com.example.system.PenKeepAliveService { *; }
-keep public class com.example.system.PenAntiLockAccessibilityService { *; }
-keep public class com.example.system.PenBootReceiver { *; }

# Keep Room Database, Entities & DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.local.** { *; }

# Keep Moshi & Retrofit DTOs / Interfaces (JinrishiciApi & VocabSyncManager)
-keep class com.example.data.remote.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep OkHttp / Conscrypt / Kotlin reflection warnings clean
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn retrofit2.**

# Keep Preferences & State Data Models
-keep class com.example.data.prefs.** { *; }
-keep class com.example.ui.theme.FontSlotSpec { *; }
-keep class com.example.ui.theme.GranularTypographyProfile { *; }
