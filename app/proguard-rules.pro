# Keep Room database implementations (accessed via reflection, e.g. WorkManager's WorkDatabase_Impl).
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
