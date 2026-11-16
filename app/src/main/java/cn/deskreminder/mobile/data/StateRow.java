package cn.deskreminder.mobile.data;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
@Entity(tableName="state")
public class StateRow {
    @PrimaryKey public int id=1;
    @NonNull public String payload="";
    public StateRow() {}
}
