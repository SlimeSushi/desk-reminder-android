package cn.deskreminder.mobile.data;
import androidx.room.Database;
import androidx.room.RoomDatabase;
@Database(entities={StateRow.class},version=1,exportSchema=false)
public abstract class LocalDatabase extends RoomDatabase { public abstract StateDao state(); }
