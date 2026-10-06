# Room / Kotlin metadata
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Gson generic signatures for export/import models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.chepkall.gymlog.data.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken