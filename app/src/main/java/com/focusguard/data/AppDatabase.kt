package com.focusguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UsageWindow::class, UsageSession::class, Task::class, TaskOccurrence::class, Tag::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun windowDao(): UsageWindowDao
    abstract fun sessionDao(): UsageSessionDao
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao

    companion object {
        /** v8: tags coloridas nos links salvos. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `color` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE task_occurrences ADD COLUMN tagId INTEGER")
            }
        }

        /** v7: links compartilhados como tarefas pontuais. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_occurrences ADD COLUMN url TEXT")
                db.execSQL("ALTER TABLE task_occurrences ADD COLUMN estimateMinutes INTEGER")
            }
        }

        /** v6: afazeres cotidianos e pontuais. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `kind` INTEGER NOT NULL, `windowId` INTEGER, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_occurrences` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER, `title` TEXT NOT NULL, `kind` INTEGER NOT NULL, `windowId` INTEGER, " +
                        "`day` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_occurrences_taskId_day` ON `task_occurrences` (`taskId`, `day`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_occurrences_day` ON `task_occurrences` (`day`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_occurrences_completedAt` ON `task_occurrences` (`completedAt`)")
            }
        }

        /** v5: sessões divididas por troca de janela. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_sessions ADD COLUMN continuation INTEGER NOT NULL DEFAULT 0")
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
    }
}
