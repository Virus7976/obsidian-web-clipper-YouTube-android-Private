# The YouTube player bridge is called from JavaScript by name.
-keepclassmembers class app.marginalia.player.PlayerBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class app.marginalia.player.PlayerBridge { *; }

# kotlinx.serialization keeps its own generated serializers; keep annotations readable.
-keepattributes *Annotation*, InnerClasses
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
