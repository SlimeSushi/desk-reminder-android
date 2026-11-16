package cn.deskreminder.mobile.data;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.annotation.Nullable;
@Dao
public interface StateDao {
    @Nullable @Query("SELECT * FROM state WHERE id=1 LIMIT 1") StateRow load();
    @Insert(onConflict=OnConflictStrategy.REPLACE) void save(StateRow row);
}
