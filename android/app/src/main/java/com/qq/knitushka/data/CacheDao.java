package com.qq.knitushka.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CacheDao {
    @Query("SELECT * FROM source_cache WHERE `key` = :key LIMIT 1")
    CacheEntry get(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(CacheEntry entry);
}
