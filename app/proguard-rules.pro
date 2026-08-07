# SplitSmith R8 & ProGuard Release Rules

# 1. Kotlin Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepnames class kotlinx.serialization.** { *; }

# 2. SplitSmith Data Models & DTOs
-keep class com.splitsmith.app.data.** { *; }
-keepclassmembers class com.splitsmith.app.data.** { *; }

# 3. Firebase & Cloud Firestore Reflection
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# 4. Cloudinary Android SDK
-keep class com.cloudinary.** { *; }
-keepclassmembers class com.cloudinary.** { *; }

# 5. Google MLKit Text Recognition
-keep class com.google.mlkit.** { *; }

# 6. Jetpack Compose & Material 3
-keep class androidx.compose.** { *; }

# 7. Optional Third-Party SDK Integrations (Glide, Picasso, Ktor)
-dontwarn com.bumptech.glide.**
-dontwarn com.squareup.picasso.**
-dontwarn io.ktor.client.plugins.**
-dontwarn io.ktor.**

