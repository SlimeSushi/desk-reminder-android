-keep class cn.deskreminder.mobile.data.model.** { *; }
-keep class cn.deskreminder.mobile.data.cloud.SyncValue { *; }
-keep class cn.deskreminder.mobile.data.cloud.SyncRecord { *; }
-keep class cn.deskreminder.mobile.data.cloud.SyncTracking { *; }
-keep class cn.deskreminder.mobile.data.cloud.SyncEnvelope { *; }
-keep class cn.deskreminder.mobile.data.cloud.CloudProfile { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-keep class * extends androidx.room.RoomDatabase
-keep class cn.deskreminder.mobile.data.LocalDatabase_Impl { *; }

# Full-snapshot sync and cloud-backup cache (SYNC-20261105-07)
-keep class cn.deskreminder.mobile.data.cloud.FullEnvelope { *; }
-keep class cn.deskreminder.mobile.data.cloud.CloudBackup { *; }
-keep class cn.deskreminder.mobile.data.cloud.HistoryCache { *; }
