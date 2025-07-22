package com.billbuddy.app

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import java.util.Date

@Database(
    entities = [
        Expense::class,
        Category::class,
        ExpenseGroup::class,
        GroupMember::class,
        ExpenseSplit::class
    ],
    version = 2, // Due to schema changes we incremented the version
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BillBuddyDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun groupDao(): GroupDao
    abstract fun groupMemberDao(): GroupMemberDao
    abstract fun expenseSplitDao(): ExpenseSplitDao

    companion object {
        @Volatile
        private var INSTANCE: BillBuddyDatabase? = null

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create indices for better performance
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_groupId` ON `expenses` (`groupId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_groupId` ON `group_members` (`groupId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_memberId` ON `expense_splits` (`memberId`)")
            }
        }

        fun getDatabase(context: Context): BillBuddyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BillBuddyDatabase::class.java,
                    "billbuddy_database"
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2) // Add migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Type Converters
class Converters {
    @androidx.room.TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @androidx.room.TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

// Database Callback to populate default categories
class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)

        // Insert default categories
        val categories = listOf(
            "INSERT INTO categories (name, icon, color, isDefault) VALUES ('Food & Dining', 'restaurant', '#FF6B6B', 1)",
            "INSERT INTO categories (name, icon, color, isDefault) VALUES ('Groceries', 'shopping_cart', '#4ECDC4', 1)",
            "INSERT INTO categories (name, icon, color, isDefault) VALUES ('Transportation', 'directions_car', '#45B7D1', 1)",
            "INSERT INTO categories (name, icon, color, isDefault) VALUES ('Utilities', 'home', '#96CEB4', 1)",
            "INSERT INTO categories (name, icon, color, isDefault) VALUES ('Entertainment', 'movie', '#DDA0DD', 1)",
            "INSERT INTO categories (name, icon, color, isDefault) VALUES ('Healthcare', 'local_hospital', '#FFB6C1', 1)",
            "INSERT INTO categories (name, icon, color, isDefault) VALUES ('Shopping', 'shopping_bag', '#F4A460', 1)",
            "INSERT INTO categories (name, icon, color, isDefault) VALUES ('Others', 'category', '#D3D3D3', 1)"
        )

        categories.forEach { db.execSQL(it) }
    }
}