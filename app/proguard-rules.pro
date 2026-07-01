# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lostf1sh.pixelplayerytm.**$$serializer { *; }
-keepclassmembers class com.lostf1sh.pixelplayerytm.** {
    *** Companion;
}
-keepclasseswithmembers class com.lostf1sh.pixelplayerytm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Duktape (JNI)
-keep class com.squareup.duktape.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
