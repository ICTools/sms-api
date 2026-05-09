# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.example.smsgateway.**$$serializer { *; }
-keepclassmembers class com.example.smsgateway.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.smsgateway.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes
-keep @kotlinx.serialization.Serializable class com.example.smsgateway.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager
-keep class androidx.work.impl.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep application class and receivers / services referenced from manifest
-keep class com.example.smsgateway.SmsGatewayApp { *; }
-keep class com.example.smsgateway.MainActivity { *; }
-keep class com.example.smsgateway.receiver.** { *; }
-keep class com.example.smsgateway.service.** { *; }

# Keep api DTOs (serialized)
-keep class com.example.smsgateway.data.api.** { *; }
-keep class com.example.smsgateway.data.auth.TokenResponse { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    <init>(...);
}

# Kotlin metadata
-keep class kotlin.Metadata { *; }
