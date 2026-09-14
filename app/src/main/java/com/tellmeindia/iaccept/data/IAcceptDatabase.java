package com.tellmeindia.iaccept.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {UserProfile.class}, version = 12)
public abstract class IAcceptDatabase extends RoomDatabase {
    public abstract IAcceptDao dao();

    private static volatile IAcceptDatabase INSTANCE;

    public static IAcceptDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (IAcceptDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            IAcceptDatabase.class, "iaccept_db")
                            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
