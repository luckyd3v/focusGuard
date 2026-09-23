package com.focusguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UsageWindow::class, UsageSession::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun windowDao(): UsageWindowDao
    abstract fun sessionDao(): UsageSessionDao

    companion object {
        /** v4: estimativa fixa opcional nas janelas sob demanda. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_windows ADD COLUMN fixedEstimateMinutes INTEGER")
            }
        }

        /** v3: estimativa de duração das janelas sob demanda. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_windows ADD COLUMN estimateMinutes INTEGER")
            }
        }

        /** v2: janelas sob demanda. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_windows ADD COLUMN onDemand INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE usage_windows ADD COLUMN activatedAt INTEGER")
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
