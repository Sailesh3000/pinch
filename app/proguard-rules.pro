# Pinch release-build keep rules (phase: production hardening)

# Room - keep entity classes and DAOs (reflection-based)
-keep class com.expensetracker.core.database.entity.** { *; }
-keep class com.expensetracker.core.database.dao.** { *; }

# Room generated implementations reference type tokens
-keep class * extends androidx.room.RoomDatabase { *; }

# Hilt - keep generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# MediaPipe GenAI - native JNI and image framework
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { native <methods>; }
-dontwarn com.google.mediapipe.framework.image.**
-dontwarn com.google.mediapipe.tasks.genai.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.expensetracker.**$$serializer { *; }
-keepclassmembers class com.expensetracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLCipher
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Sentry
-keep class io.sentry.** { *; }

# Play Core / Asset Delivery
-keep class com.google.android.play.core.** { *; }
-keepclassmembers class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.assetpacks.**
-dontwarn com.google.android.gms.common.annotation.**

# Gson/OkHttp used transitively by Play Core - keep model annotations
-keepattributes Signature, ExceptionHandler
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.MethodHandles*