# kotlinx.serialization ships keep rules for @Serializable classes; keep the
# plan-data models whole anyway.
-keep class com.nwbort.workouts.data.** { *; }

# Room creates each database's generated _Impl class reflectively through its
# no-argument constructor, which R8 full mode strips unless told otherwise.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Glance builds widget tap callbacks (actionRunCallback) reflectively through
# their no-argument constructor. Its own keep rule names the classes but not
# the constructor, so R8 full mode strips it and taps silently do nothing.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
