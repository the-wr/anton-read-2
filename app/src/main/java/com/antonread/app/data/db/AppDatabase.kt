package com.antonread.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.antonread.app.data.model.ItemState
import com.antonread.app.data.model.ItemType

class Converters {
    @TypeConverter fun fromItemState(v: ItemState): String = v.name
    @TypeConverter fun toItemState(v: String): ItemState = ItemState.valueOf(v)
    @TypeConverter fun fromItemType(v: ItemType): String = v.name
    @TypeConverter fun toItemType(v: String): ItemType = ItemType.valueOf(v)
    @TypeConverter fun fromNullableLong(v: Long?): Long = v ?: -1L
    @TypeConverter fun toNullableLong(v: Long): Long? = if (v == -1L) null else v
}

@Database(entities = [ItemEntity::class, SessionEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "antonread.db"
            ).build().also { INSTANCE = it }
        }
    }
}
