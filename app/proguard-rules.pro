# Ignore missing logger classes for Ktor
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn org.bson.**
-dontwarn io.github.jan.supabase.**

# Keep Kotlin Serialization Models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep,allowobfuscation,allowshrinking @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    *** Companion;
    *** $serializer;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep App Data Models
-keep class com.tellmeindia.iaccept.data.** { *; }

# Keep Native JNI Methods
-keep class com.tellmeindia.iaccept.logic.NativeRideEngine {
    native <methods>;
}

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Coroutines
-keepclassmembers class * extends kotlinx.coroutines.internal.MainDispatcherFactory {
    public <init>();
}
-keepclassmembers class * extends kotlinx.coroutines.CoroutineExceptionHandler {
    public <init>();
}
