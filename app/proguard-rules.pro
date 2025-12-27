# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep accessibility service
-keep class com.linerecorder.app.service.LineCallDetectorService { *; }
-keep class com.linerecorder.app.service.RecordingService { *; }

# Keep model classes
-keep class com.linerecorder.app.model.** { *; }
