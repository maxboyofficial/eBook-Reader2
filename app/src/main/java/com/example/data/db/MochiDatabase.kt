package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class MochiTypeConverters {
    @TypeConverter
    fun fromBookFormat(value: BookFormat): String = value.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun fromReadingStatus(value: ReadingStatus): String = value.name

    @TypeConverter
    fun toReadingStatus(value: String): ReadingStatus = ReadingStatus.valueOf(value)
}

@Database(
    entities = [
        BookEntity::class,
        FolderEntity::class,
        CollectionEntity::class,
        BookCollectionCrossRef::class,
        TagEntity::class,
        BookTagCrossRef::class,
        ThemeEntity::class,
        BookSettingsEntity::class,
        PositionEntity::class,
        AnnotationEntity::class,
        ExportTemplateEntity::class,
        DictionaryEntity::class,
        VocabularyWordEntity::class,
        AnnotationLayerEntity::class,
        MarkupObjectEntity::class,
        PageScaleEntity::class,
        ReadingSessionEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(MochiTypeConverters::class)
abstract class MochiDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun folderDao(): FolderDao
    abstract fun collectionDao(): CollectionDao
    abstract fun tagDao(): TagDao
    abstract fun themeDao(): ThemeDao
    abstract fun positionDao(): PositionDao
    abstract fun bookSettingsDao(): BookSettingsDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun exportTemplateDao(): ExportTemplateDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun annotationStudioDao(): AnnotationStudioDao
    abstract fun readingStatsDao(): ReadingStatsDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `annotation_layers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `visible` INTEGER NOT NULL,
                        `locked` INTEGER NOT NULL,
                        `opacity` REAL NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotation_layers_bookId` ON `annotation_layers` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotation_layers_sortOrder` ON `annotation_layers` (`sortOrder`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `markup_objects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `layerId` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `geometryJson` TEXT NOT NULL,
                        `styleJson` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `parentGroupId` TEXT,
                        `zOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_markup_objects_bookId_pageIndex` ON `markup_objects` (`bookId`, `pageIndex`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_markup_objects_layerId` ON `markup_objects` (`layerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_markup_objects_parentGroupId` ON `markup_objects` (`parentGroupId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `page_scales` (
                        `bookId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `pagePtsPerUnit` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`, `pageIndex`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reading_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `bookTitle` TEXT NOT NULL,
                        `dateIso` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `pagesTurned` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_sessions_bookId_dateIso` ON `reading_sessions` (`bookId`, `dateIso`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_dateIso` ON `reading_sessions` (`dateIso`)")
            }
        }

        fun build(context: Context): MochiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MochiDatabase::class.java,
                "mochi_reader.db"
            ).addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

