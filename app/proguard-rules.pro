# Line numbers make the crash reports readable.
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*
-renamesourcefileattribute SourceFile

# The preference screen builds its entries by name from XML, so those classes
# have to survive shrinking.
-keep class androidx.preference.** { *; }

# Views that are inflated from XML need their two argument constructor.
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Kotlin metadata is read by the tooling and by the serialization runtime.
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# org.json ships with the platform.
-dontwarn org.json.**
