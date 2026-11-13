package com.qq.knitushka.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One validated source response. Keeping the raw HTML allows offline reparsing. */
@Entity(tableName = "source_cache")
public class CacheEntry {
    @PrimaryKey @NonNull public String key = "";
    @NonNull public String html = "";
    public long checkedAt;

    public CacheEntry(@NonNull String key, @NonNull String html, long checkedAt) {
        this.key = key;
        this.html = html;
        this.checkedAt = checkedAt;
    }
}
