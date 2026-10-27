# kotlinx.serialization keeps its own generated serializers; keep annotations readable.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# NewPipe Extractor (playback fallback). It uses Rhino and protobuf, and reads some classes by name.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class com.grack.nanojson.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.script.**
-dontwarn com.google.protobuf.**
-dontwarn org.jsoup.**

# Wireless ADB and Shizuku installers.
-keep class io.github.muntashirakon.adb.** { *; }
-keep class android.sun.security.** { *; }
-keep class org.conscrypt.** { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn io.github.muntashirakon.**
-dontwarn com.github.MuntashirAkon.**
-dontwarn org.conscrypt.**
-dontwarn android.sun.**
