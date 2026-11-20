-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class io.github.stickerbug.dsapichat.**$$serializer { *; }
-keepclassmembers class io.github.stickerbug.dsapichat.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.stickerbug.dsapichat.** {
    kotlinx.serialization.KSerializer serializer(...);
}
