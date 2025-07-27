package com.billbuddy.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.*
import androidx.lifecycle.Observer

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BillBuddyDatabase.getDatabase(application)
    private val expenseDao = database.expenseDao()
    private val groupDao = database.groupDao()

    val personalTotal: LiveData<Double?> = expenseDao.getTotalPersonalExpenses()

    val groupBalance: LiveData<Double?> = expenseDao.getTotalGroupSpending()

    private val _hasGroups = MutableLiveData<Boolean>()
    val hasGroups: LiveData<Boolean> = _hasGroups

    init {
        checkGroups()
    }

    private fun checkGroups() {
        groupDao.getAllGroups().observeForever { groups ->
            _hasGroups.value = groups.isNotEmpty()
        }
    }
}

class AddExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BillBuddyDatabase.getDatabase(application)
    private val expenseDao = database.expenseDao()
    private val categoryDao = database.categoryDao()

    val categories: LiveData<List<Category>> = categoryDao.getAllCategories()

    fun saveExpense(expense: Expense) {
        viewModelScope.launch {
            expenseDao.insert(expense)
        }
    }
}

// Groups ViewModel
class GroupsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BillBuddyDatabase.getDatabase(application)
    private val groupDao = database.groupDao()
    private val groupMemberDao = database.groupMemberDao()
    private val expenseDao = database.expenseDao()
    private val categoryDao = database.categoryDao()

    private val _groupsWithCalculations = MutableLiveData<List<GroupWithDetails>>()
    val groupsWithCalculations: LiveData<List<GroupWithDetails>> = _groupsWithCalculations

    val categories = categoryDao.getAllCategories()

    private val groupsObserver = Observer<List<ExpenseGroup>> { groupList ->
        viewModelScope.launch {
            val updatedGroups = groupList.map { group ->
                val memberCount = groupDao.getMemberCount(group.id)
                val totalExpenses = groupDao.getTotalExpenses(group.id)

                GroupWithDetails(
                    id = group.id,
                    name = group.name,
                    description = group.description,
                    createdAt = group.createdAt,
                    updatedAt = group.updatedAt,
                    memberCount = memberCount,
                    totalExpenses = totalExpenses
                )
            }
            _groupsWithCalculations.postValue(updatedGroups)
        }
    }

    init {
        // Observe groups and calculate details
        groupDao.getGroupsWithDetails().observeForever(groupsObserver)
    }

    fun updateGroup(group: ExpenseGroup) {
        viewModelScope.launch {
            groupDao.update(group)
        }
    }

    fun deleteGroup(group: ExpenseGroup) {
        viewModelScope.launch {
            // Delete all group members first
            groupMemberDao.deleteAllGroupMembers(group.id)

            // Delete all expenses for this group
            deleteGroupExpenses(group.id)

            // Delete the group
            groupDao.delete(group)
        }
    }

    private suspend fun deleteGroupExpenses(groupId: Long) {
        // Get all expenses for this group
        val expenses = expenseDao.getGroupExpensesSync(groupId)

        // Delete each expense
        expenses.forEach { expense ->
            expenseDao.delete(expense)
        }
    }

    override fun onCleared() {
        super.onCleared()
        groupDao.getGroupsWithDetails().removeObserver(groupsObserver)
    }
}

// Group Detail ViewModel
class GroupDetailViewModel(
    application: Application,
    private val groupId: Long
) : AndroidViewModel(application) {

    private val database = BillBuddyDatabase.getDatabase(application)
    private val groupDao = database.groupDao()
    private val groupMemberDao = database.groupMemberDao()
    private val expenseDao = database.expenseDao()
    private val expenseSplitDao = database.expenseSplitDao()

    val groupMembers = groupMemberDao.getGroupMembers(groupId)
    val groupExpenses = expenseDao.getGroupExpenses(groupId)
    val memberBalances = expenseSplitDao.getGroupMemberBalances(groupId)

    private val _group = MutableLiveData<ExpenseGroup>()
    val group: LiveData<ExpenseGroup> = _group

    init {
        loadGroup()
    }

    private fun loadGroup() {
        viewModelScope.launch {
            _group.value = groupDao.getGroupById(groupId)
        }
    }

    fun markMemberAsSettled(memberId: Long) {
        viewModelScope.launch {
            expenseSplitDao.markAllMemberSplitsAsPaid(memberId, true, Date())
        }
    }

    fun markMemberAsUnsettled(memberId: Long) {
        viewModelScope.launch {
            expenseSplitDao.markAllMemberSplitsAsPaid(memberId, false, null)
        }
    }

    fun updateMember(member: GroupMember) {
        viewModelScope.launch {
            groupMemberDao.update(member)
        }
    }

    fun removeMember(member: GroupMember) {
        viewModelScope.launch {
            groupMemberDao.delete(member)
        }
    }

    fun addMemberAndRecalculateExpenses(newMember: GroupMember) {
        viewModelScope.launch {
            try {
                // 1. Add the new member first
                val newMemberId = groupMemberDao.insert(newMember)

                // 2. Get all current group expenses (synchronously)
                val groupExpenses = expenseDao.getGroupExpensesSync(groupId)

                // 3. Get all current members including the new one (synchronously)
                val allMembers = groupMemberDao.getGroupMembersSync(groupId)
                val memberCount = allMembers.size

                println("DEBUG: Found ${groupExpenses.size} expenses to recalculate for ${memberCount} members")

                // 4. Recalculate each expense
                groupExpenses.forEach { expense ->
                    println("DEBUG: Recalculating expense: ${expense.title} - ₱${expense.amount}")
                    recalculateExpenseSplits(expense, memberCount, newMemberId)
                }

                println("DEBUG: Recalculation completed")

            } catch (e: Exception) {
                println("ERROR: Failed to add member and recalculate: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun recalculateExpenseSplits(expense: Expense, newMemberCount: Int, newMemberId: Long) {
        try {
            // Get existing splits for this expense (synchronously)
            val existingSplits = expenseSplitDao.getExpenseSplitsSync(expense.id)

            // Calculate new split amount
            val newSplitAmount = expense.amount / newMemberCount

            println("DEBUG: Expense ${expense.title}: ₱${expense.amount} ÷ ${newMemberCount} = ₱${newSplitAmount} per person")

            // Update existing splits with new amount
            existingSplits.forEach { split ->
                val updatedSplit = split.copy(
                    amount = newSplitAmount
                    // Keep the same isPaid status
                )
                expenseSplitDao.update(updatedSplit)
                println("DEBUG: Updated split for member ${split.memberId}: ₱${newSplitAmount}")
            }

            // Add new split for the new member
            val newSplit = ExpenseSplit(
                expenseId = expense.id,
                memberId = newMemberId,
                amount = newSplitAmount,
                isPaid = false // New member hasn't paid yet
            )
            expenseSplitDao.insert(newSplit)
            println("DEBUG: Added new split for member ${newMemberId}: ₱${newSplitAmount}")

        } catch (e: Exception) {
            println("ERROR: Failed to recalculate splits for expense ${expense.title}: ${e.message}")
            e.printStackTrace()
        }
    }
}

// Personal History ViewModel
class PersonalHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BillBuddyDatabase.getDatabase(application)
    private val expenseDao = database.expenseDao()

    val personalExpenses = expenseDao.getPersonalExpensesWithDetails()

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            expenseDao.delete(expense)
        }
    }
}

// Spending Summary ViewModel
class SpendingSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BillBuddyDatabase.getDatabase(application)
    private val expenseDao = database.expenseDao()

    private val _selectedPeriod = MutableLiveData<Period>(Period.WEEK)
    val selectedPeriod: LiveData<Period> = _selectedPeriod

    private val _selectedYear = MutableLiveData<String>()
    val selectedYear: LiveData<String> = _selectedYear

    private val _availableYears = MutableLiveData<List<String>>()
    val availableYears: LiveData<List<String>> = _availableYears

    private val _categoryExpenses = MutableLiveData<List<CategoryExpenseSum>>()
    val categoryExpenses: LiveData<List<CategoryExpenseSum>> = _categoryExpenses

    private val _totalExpense = MutableLiveData<Double>()
    val totalExpense: LiveData<Double> = _totalExpense

    private val _personalExpenses = MutableLiveData<List<ExpenseWithDetails>>()
    val personalExpenses: LiveData<List<ExpenseWithDetails>> = _personalExpenses

    init {
        // Set current year as default
        val currentYear = Calendar.getInstance().get(Calendar.YEAR).toString()
        _selectedYear.value = currentYear

        loadAvailableYears()
        loadSummary(Period.WEEK, currentYear)
    }



    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun loadAvailableYears() {
        viewModelScope.launch {
            val years = expenseDao.getAvailableYears().filterNotNull()
            _availableYears.postValue(years)

            // If current year is not in available years select the most recent year
            val currentYear = _selectedYear.value
            if (years.isNotEmpty() && !years.contains(currentYear)) {
                val firstYear = years.first().toString()
                _selectedYear.postValue(firstYear)
                loadSummary(_selectedPeriod.value ?: Period.WEEK, firstYear)
            }
        }
    }

    fun selectPeriod(period: Period) {
        _selectedPeriod.value = period
        loadSummary(period, _selectedYear.value ?: getCurrentYear())
    }

    fun selectYear(year: String) {
        _selectedYear.value = year
        loadSummary(_selectedPeriod.value ?: Period.WEEK, year)
    }

    private fun loadSummary(period: Period, year: String) {
        val calendar = Calendar.getInstance()

        // Set calendar to the selected year
        calendar.set(Calendar.YEAR, year.toInt())

        val endDate = when (period) {
            Period.WEEK -> {
                // For weekly, use current week in selected year
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                if (year.toInt() == currentYear) {
                    // Current year - use actual current week
                    Calendar.getInstance().time
                } else {
                    // Past year - use last week of year
                    calendar.set(Calendar.WEEK_OF_YEAR, calendar.getActualMaximum(Calendar.WEEK_OF_YEAR))
                    calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    calendar.time
                }
            }
            Period.MONTH -> {
                // For monthly, use current month in selected year
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                if (year.toInt() == currentYear) {
                    // Current year - use actual current month
                    Calendar.getInstance().time
                } else {
                    // Past year - use December
                    calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                    calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                    calendar.time
                }
            }
            Period.YEAR -> {
                // For yearly, use end of selected year
                calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                calendar.set(Calendar.DAY_OF_MONTH, 31)
                calendar.time
            }
        }

        val startDate = when (period) {
            Period.WEEK -> {
                val cal = Calendar.getInstance()
                cal.time = endDate
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.time
            }
            Period.MONTH -> {
                val cal = Calendar.getInstance()
                cal.time = endDate
                cal.add(Calendar.MONTH, -1)
                cal.time
            }
            Period.YEAR -> {
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.time
            }
        }

        // Load data with year filtering
        expenseDao.getPersonalExpensesByCategoryByYearAndPeriod(year, startDate, endDate)
            .observeForever { expenses ->
                _categoryExpenses.value = expenses
                _totalExpense.value = expenses.sumOf { it.total }
            }

        expenseDao.getPersonalExpensesByYearAndPeriod(year, startDate, endDate)
            .observeForever { total ->
                _totalExpense.value = total ?: 0.0
            }

        expenseDao.getPersonalExpensesWithDetailsByYearAndPeriod(year, startDate, endDate)
            .observeForever { expenseList ->
                _personalExpenses.value = expenseList
            }
    }

    private fun getCurrentYear(): String {
        return Calendar.getInstance().get(Calendar.YEAR).toString()
    }

    enum class Period {
        WEEK, MONTH, YEAR
    }
}

// Categories ViewModel
class CategoriesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BillBuddyDatabase.getDatabase(application)
    private val categoryDao = database.categoryDao()
    private val expenseDao = database.expenseDao()

    val categories = categoryDao.getAllCategories()

    // Categories with expense statistics
    private val _categoriesWithStats = MutableLiveData<List<CategoryWithStats>>()
    val categoriesWithStats: LiveData<List<CategoryWithStats>> = _categoriesWithStats

    // Expenses for selected category
    private val _categoryExpenses = MutableLiveData<List<ExpenseWithDetails>>()
    val categoryExpenses: LiveData<List<ExpenseWithDetails>> = _categoryExpenses

    fun loadCategoriesWithStats() {
        viewModelScope.launch {
            val allCategories = categoryDao.getAllCategoriesSync()
            val categoriesWithStats = allCategories.map { category ->
                val expenses = expenseDao.getPersonalExpensesByCategorySync(category.id)
                CategoryWithStats(
                    id = category.id,
                    name = category.name,
                    icon = category.icon,
                    color = category.color,
                    isDefault = category.isDefault,
                    expenseCount = expenses.size,
                    totalAmount = expenses.sumOf { it.amount }
                )
            }
            _categoriesWithStats.postValue(categoriesWithStats)
        }
    }

    fun loadExpensesForCategory(categoryId: Long) {
        expenseDao.getPersonalExpensesByCategoryWithDetails(categoryId)
            .observeForever { expenses ->
                _categoryExpenses.value = expenses
            }
    }

    fun addCategory(name: String, icon: String?, color: String?) {
        viewModelScope.launch {
            val category = Category(
                name = name,
                icon = icon,
                color = color,
                isDefault = false
            )
            categoryDao.insert(category)
            loadCategoriesWithStats() // Refresh the list
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            categoryDao.update(category)
            loadCategoriesWithStats()
        }
    }

    fun deleteCategory(category: Category) {
        if (!category.isDefault) {
            viewModelScope.launch {
                categoryDao.delete(category)
                loadCategoriesWithStats()
            }
        }
    }
}

data class CategoryWithStats(
    val id: Long,
    val name: String,
    val icon: String?,
    val color: String?,
    val isDefault: Boolean,
    val expenseCount: Int,
    val totalAmount: Double
)

class AddGroupExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BillBuddyDatabase.getDatabase(application)
    private val groupDao = database.groupDao()
    private val groupMemberDao = database.groupMemberDao()
    private val expenseDao = database.expenseDao()
    private val expenseSplitDao = database.expenseSplitDao()
    private val categoryDao = database.categoryDao()

    val categories: LiveData<List<Category>> = categoryDao.getAllCategories()

    private val _groupCreated = MutableLiveData<Long>()
    val groupCreated: LiveData<Long> = _groupCreated

    private val _groupMembers = MutableLiveData<List<GroupMember>>()
    val groupMembers: LiveData<List<GroupMember>> = _groupMembers

    fun createGroupWithMembers(groupName: String, description: String?, memberNames: List<String>) {
        viewModelScope.launch {
            // Create the group first
            val group = ExpenseGroup(
                name = groupName,
                description = description
            )

            val groupId = groupDao.insert(group)

            // Add members to the group
            memberNames.forEach { memberName ->
                val member = GroupMember(
                    groupId = groupId,
                    name = memberName
                )
                groupMemberDao.insert(member)
            }

            // Notify that group is created
            _groupCreated.value = groupId
        }
    }

    fun loadGroupMembers(groupId: Long) {
        viewModelScope.launch {
            // Load members and update the LiveData
            groupMemberDao.getGroupMembers(groupId).observeForever { members ->
                _groupMembers.value = members
            }
        }
    }

    fun addGroupExpense(
        groupId: Long,
        title: String,
        amount: Double,
        categoryId: Long,
        notes: String?,
        receiptPath: String?,
        splitAmong: List<Long>
    ) {
        viewModelScope.launch {
            // Create the expense
            val expense = Expense(
                title = title,
                amount = amount,
                categoryId = categoryId,
                date = Date(),
                notes = notes,
                receiptImagePath = receiptPath,
                isPersonal = false,
                groupId = groupId,
                paidById = null
            )

            val expenseId = expenseDao.insert(expense)

            // Create splits for each member
            val splitAmount = amount / splitAmong.size
            val splits = splitAmong.map { memberId ->
                ExpenseSplit(
                    expenseId = expenseId,
                    memberId = memberId,
                    amount = splitAmount,
                    isPaid = false
                )
            }

            expenseSplitDao.insertAll(splits)
        }
    }
}