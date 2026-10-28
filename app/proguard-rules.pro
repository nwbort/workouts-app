# kotlinx.serialization ships keep rules for @Serializable classes; keep the
# plan-data models whole anyway, as a release build can't be smoke-tested here.
-keep class com.nwbort.workouts.data.** { *; }
