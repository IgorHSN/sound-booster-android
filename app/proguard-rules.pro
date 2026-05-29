# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve audio effect classes
-keep class android.media.audiofx.** { *; }

# Preserve our app classes
-keep class com.soundbooster.app.** { *; }
