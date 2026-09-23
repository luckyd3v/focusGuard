package com.focusguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UsageWindow::class, UsageSession::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun windowDao(): UsageWindowDao
    abstract fun sessionDao(): UsageSessionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focusguard.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Janela de exemplo: Expediente, 09h–19h, seg a sex, 10 min por desbloqueio
                        db.execSQL(
                            "INSERT INTO usage_windows (name, startMinuteOfDay, endMinuteOfDay, limitMinutes, daysMask, enabled) " +
                                "VALUES ('Expediente', 540, 1140, 10, ${UsageWindow.WEEKDAYS}, 1)"
                        )
                    }
                })
                .build()
    }
}
