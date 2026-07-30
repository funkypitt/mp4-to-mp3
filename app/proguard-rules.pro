# The JNI layer resolves these by fully-qualified name at runtime.
-keep class app.mp4tomp3.core.Lame { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
