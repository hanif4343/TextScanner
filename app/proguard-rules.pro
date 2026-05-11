# Add project specific ProGuard rules here.
-keep class com.hanif.textscanner.** { *; }
-keep class com.google.mlkit.** { *; }
-keepclassmembers class * {
    @com.google.firebase.encoders.annotations.ExtraProperty <fields>;
}
