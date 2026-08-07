# SplitSmith R8 & ProGuard Release Rules

# Preserve line numbers and attributes for clean stacktraces & reflection
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# 1. Kotlin Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepnames class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer {
    public static ** INSTANCE;
}
-keepclassmembers class * {
    *** companion(...);
}

# 2. SplitSmith Data Models & DTOs (Preserve fields & default constructors for Firestore toObject)
-keep class com.splitsmith.app.data.** { *; }
-keepclassmembers class com.splitsmith.app.data.** {
    public <init>();
    <fields>;
    <methods>;
}

# 3. Firebase Auth, Firestore & Google Services
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# 4. Cloudinary Android SDK
-keep class com.cloudinary.** { *; }
-keepclassmembers class com.cloudinary.** { *; }

# 5. Google MLKit Text Recognition
-keep class com.google.mlkit.** { *; }
-keepclassmembers class com.google.mlkit.** { *; }

# 6. Jetpack Compose & Material 3
-keep class androidx.compose.** { *; }

# 7. Optional Third-Party SDK Integrations (Glide, Picasso, Ktor)
-dontwarn com.bumptech.glide.**
-dontwarn com.squareup.picasso.**
-dontwarn io.ktor.client.plugins.**
-dontwarn io.ktor.**
-dontwarn com.google.firebase.**
