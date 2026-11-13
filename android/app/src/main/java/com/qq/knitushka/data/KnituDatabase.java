package com.qq.knitushka.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {CacheEntry.class}, version = 1, exportSchema = false)
public abstract class KnituDatabase extends RoomDatabase {
    public abstract CacheDao cache();
}
