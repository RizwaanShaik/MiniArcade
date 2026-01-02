# Add project specific ProGuard rules here.

# Keep line numbers for debugging crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==================== Firebase ====================
# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Firebase Auth
-keepattributes Signature
-keepattributes *Annotation*

# Firebase Realtime Database
-keepclassmembers class com.rizwaan.cousinarcade.data.models.** {
    *;
}

# ==================== Gson ====================
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes for Gson serialization
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ==================== Data Models ====================
# Keep all data models (Firebase needs these)
-keep class com.rizwaan.cousinarcade.data.models.Player { *; }
-keep class com.rizwaan.cousinarcade.data.models.GameScore { *; }
-keep class com.rizwaan.cousinarcade.data.models.GameType { *; }

# ==================== Room Database ====================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ==================== Coroutines ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==================== AndroidX ====================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# ==================== Material Components ====================
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ==================== Lottie ====================
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ==================== Enums ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==================== Parcelable ====================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ==================== R8 Full Mode ====================
-allowaccessmodification
-repackageclasses
