package com.billbuddy.app

import androidx.room.*
import androidx.lifecycle.LiveData
import java.util.Date

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE isPersonal = 1 ORDER BY date DESC")
    fun getAllPersonalExpenses(): LiveData<List<Expense>>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY date DESC")
    fun getGroupExpenses(groupId: Long): LiveData<List<Expense>>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId")
    suspend fun getGroupExpensesSync(groupId: Long): List<Expense>

    @Query("SELECT SUM(amount) FROM expenses WHERE isPersonal = 1")
    fun getTotalPersonalExpenses(): LiveData<Double?>

    // Get total group spending across all groups
    @Query("SELECT SUM(amount) FROM expenses WHERE isPersonal = 0")
    fun getTotalGroupSpending(): LiveData<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE isPersonal = 1 AND date BETWEEN :startDate AND :endDate")
    fun getPersonalExpensesBetweenDates(startDate: Date, endDate: Date): LiveData<Double?>

    @Query("""
        SELECT e.*, c.name as categoryName, c.icon as categoryIcon, c.color as categoryColor
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.isPersonal = 1
        ORDER BY e.date DESC
    """)
    fun getPersonalExpensesWithDetails(): LiveData<List<ExpenseWithDetails>>

    @Query("""
        SELECT SUM(amount) as total, categoryId, c.name as categoryName, c.color as categoryColor
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.isPersonal = 1 AND e.date BETWEEN :startDate AND :endDate
        GROUP BY categoryId
        ORDER BY total DESC
    """)
    fun getPersonalExpensesByCategoryBetweenDates(
        startDate: Date,
        endDate: Date
    ): LiveData<List<CategoryExpenseSum>>

    @Query("""
    SELECT e.*, c.name as categoryName, c.icon as categoryIcon, c.color as categoryColor
    FROM expenses e
    LEFT JOIN categories c ON e.categoryId = c.id
    WHERE e.isPersonal = 1 AND e.date BETWEEN :startDate AND :endDate
    ORDER BY e.date DESC
""")
    fun getPersonalExpensesWithDetailsBetweenDates(
        startDate: Date,
        endDate: Date
    ): LiveData<List<ExpenseWithDetails>>

    @Query("SELECT * FROM expenses WHERE isPersonal = 1 AND categoryId = :categoryId")
    suspend fun getPersonalExpensesByCategorySync(categoryId: Long): List<Expense>

    @Query("""
    SELECT e.*, c.name as categoryName, c.icon as categoryIcon, c.color as categoryColor
    FROM expenses e
    LEFT JOIN categories c ON e.categoryId = c.id
    WHERE e.isPersonal = 1 AND e.categoryId = :categoryId
    ORDER BY e.date DESC
""")
    fun getPersonalExpensesByCategoryWithDetails(categoryId: Long): LiveData<List<ExpenseWithDetails>>

    // Get unique years from personal expenses
    @Query("""
        SELECT DISTINCT CAST(strftime('%Y', date/1000, 'unixepoch') AS TEXT) as year 
        FROM expenses 
        WHERE isPersonal = 1 
        AND date IS NOT NULL
        AND strftime('%Y', date/1000, 'unixepoch') IS NOT NULL
        AND strftime('%Y', date/1000, 'unixepoch') != ''
        ORDER BY year DESC
    """)
    suspend fun getAvailableYears(): List<String>

    // Get expenses for specific year and period
    @Query("""
        SELECT e.*, c.name as categoryName, c.icon as categoryIcon, c.color as categoryColor
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.isPersonal = 1 
        AND strftime('%Y', e.date/1000, 'unixepoch') = :year
        AND e.date BETWEEN :startDate AND :endDate
        ORDER BY e.date DESC
    """)
    fun getPersonalExpensesWithDetailsByYearAndPeriod(
        year: String,
        startDate: Date,
        endDate: Date
    ): LiveData<List<ExpenseWithDetails>>

    // Get total expenses for specific year and period
    @Query("""
        SELECT SUM(amount) 
        FROM expenses 
        WHERE isPersonal = 1 
        AND strftime('%Y', date/1000, 'unixepoch') = :year
        AND date BETWEEN :startDate AND :endDate
    """)
    fun getPersonalExpensesByYearAndPeriod(
        year: String,
        startDate: Date,
        endDate: Date
    ): LiveData<Double?>

    // Get category breakdown for specific year and period
    @Query("""
        SELECT SUM(amount) as total, categoryId, c.name as categoryName, c.color as categoryColor
        FROM expenses e
        LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.isPersonal = 1 
        AND strftime('%Y', e.date/1000, 'unixepoch') = :year
        AND e.date BETWEEN :startDate AND :endDate
        GROUP BY categoryId
        ORDER BY total DESC
    """)
    fun getPersonalExpensesByCategoryByYearAndPeriod(
        year: String,
        startDate: Date,
        endDate: Date
    ): LiveData<List<CategoryExpenseSum>>
}

// Data classes for complex queries
data class CategoryExpenseSum(
    val total: Double,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String
)

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories ORDER BY name")
    fun getAllCategories(): LiveData<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories ORDER BY name")
    suspend fun getAllCategoriesSync(): List<Category>

}

@Dao
interface GroupDao {

    @Insert
    suspend fun insert(group: ExpenseGroup): Long

    @Update
    suspend fun update(group: ExpenseGroup)

    @Delete
    suspend fun delete(group: ExpenseGroup)

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllGroups(): LiveData<List<ExpenseGroup>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: Long): ExpenseGroup?

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getGroupsWithDetails(): LiveData<List<ExpenseGroup>>

    // Methods for calculations
    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberCount(groupId: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE groupId = :groupId AND isPersonal = 0")
    suspend fun getTotalExpenses(groupId: Long): Double
}

data class GroupWithDetails(
    val id: Long,
    val name: String,
    val description: String?,
    val createdAt: Date,
    val updatedAt: Date,
    val memberCount: Int,
    val totalExpenses: Double
)

@Dao
interface GroupMemberDao {

    @Insert
    suspend fun insert(member: GroupMember): Long

    @Update
    suspend fun update(member: GroupMember)

    @Delete
    suspend fun delete(member: GroupMember)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY name")
    fun getGroupMembers(groupId: Long): LiveData<List<GroupMember>>

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteAllGroupMembers(groupId: Long)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getGroupMembersSync(groupId: Long): List<GroupMember>
}

@Dao
interface ExpenseSplitDao {

    @Insert
    suspend fun insert(split: ExpenseSplit)

    @Insert
    suspend fun insertAll(splits: List<ExpenseSplit>)

    @Update
    suspend fun update(split: ExpenseSplit)

    @Query("UPDATE expense_splits SET isPaid = :isPaid, paidAt = :paidAt WHERE id = :splitId")
    suspend fun updatePaymentStatus(splitId: Long, isPaid: Boolean, paidAt: Date?)

    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun getExpenseSplitsSync(expenseId: Long): List<ExpenseSplit>

    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    fun getExpenseSplits(expenseId: Long): LiveData<List<ExpenseSplit>>

    @Query("""
        SELECT es.*, gm.name as memberName
        FROM expense_splits es
        LEFT JOIN group_members gm ON es.memberId = gm.id
        WHERE es.expenseId = :expenseId
    """)
    fun getExpenseSplitsWithMemberNames(expenseId: Long): LiveData<List<SplitWithMemberName>>

    @Query("""
        SELECT gm.id as memberId, gm.name as memberName,
        COALESCE(SUM(CASE WHEN es.isPaid = 0 THEN es.amount ELSE 0 END), 0) as owedAmount,
        COALESCE(SUM(CASE WHEN es.isPaid = 1 THEN es.amount ELSE 0 END), 0) as paidAmount
        FROM group_members gm
        LEFT JOIN expense_splits es ON gm.id = es.memberId
        WHERE gm.groupId = :groupId
        GROUP BY gm.id
    """)
    fun getGroupMemberBalances(groupId: Long): LiveData<List<MemberBalance>>

    // Method for marking member splits as paid
    @Query("UPDATE expense_splits SET isPaid = :isPaid, paidAt = :paidAt WHERE memberId = :memberId")
    suspend fun markAllMemberSplitsAsPaid(memberId: Long, isPaid: Boolean, paidAt: Date?)

    @Query("SELECT * FROM expense_splits WHERE memberId = :memberId")
    suspend fun getAllSplitsForMember(memberId: Long): List<ExpenseSplit>

    @Delete
    suspend fun delete(split: ExpenseSplit)
}

data class SplitWithMemberName(
    val id: Long,
    val expenseId: Long,
    val memberId: Long,
    val memberName: String,
    val amount: Double,
    val isPaid: Boolean,
    val paidAt: Date?
)

data class MemberBalance(
    val memberId: Long,
    val memberName: String,
    val owedAmount: Double,
    val paidAmount: Double
)