# kotlinx.serialization: keep generated serializers reachable via reflection
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.xichen.crossfitlog.**$$serializer { *; }
-keepclassmembers class dev.xichen.crossfitlog.** {
    *** Companion;
}
-keepclasseswithmembers class dev.xichen.crossfitlog.** {
    kotlinx.serialization.KSerializer serializer(...);
}
