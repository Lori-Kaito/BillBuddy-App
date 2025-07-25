package com.billbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbuddy.app.databinding.ActivityCategoriesBinding
import com.billbuddy.app.databinding.ItemCategoryBinding
import com.billbuddy.app.databinding.ItemExpenseSummaryBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class CategoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriesBinding
    private lateinit var viewModel: CategoriesViewModel
    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var expensesAdapter: CategoryExpensesAdapter

    private var isShowingExpenses = false
    private var currentCategoryName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CategoriesViewModel::class.java]

        setupUI()
        observeData()
        setupBackPressedCallback()
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isShowingExpenses) {
                    // Go back to categories list
                    showCategoriesList()
                } else {
                    // Exit activity
                    finish()
                }
            }
        })
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Setup categories adapter
        categoriesAdapter = CategoriesAdapter { category ->
            showCategoryExpenses(category)
        }

        // Setup expenses adapter with click listener
        expensesAdapter = CategoryExpensesAdapter { expense ->
            openReceiptViewer(expense)
        }

        binding.rvCategories.layoutManager = LinearLayoutManager(this)
        binding.rvCategories.adapter = categoriesAdapter

        // Initially show categories
        showCategoriesList()
    }

    private fun observeData() {
        // Check if categoriesWithStats exists, fallback to regular categories
        try {
            viewModel.categoriesWithStats.observe(this) { categoriesWithStats ->
                if (categoriesWithStats != null) {
                    categoriesAdapter.submitList(categoriesWithStats)
                }
            }
        } catch (e: Exception) {
            // If categoriesWithStats doesn't exist, create it from regular categories
            viewModel.categories.observe(this) { categories ->
                val categoriesWithStats = categories.map { category ->
                    CategoryWithStats(
                        id = category.id,
                        name = category.name,
                        icon = category.icon,
                        color = category.color,
                        isDefault = category.isDefault,
                        expenseCount = 0,
                        totalAmount = 0.0
                    )
                }
                categoriesAdapter.submitList(categoriesWithStats)
            }
        }

        viewModel.categoryExpenses.observe(this) { expenses ->
            if (isShowingExpenses) {
                expensesAdapter.submitList(expenses ?: emptyList())

                if (expenses.isNullOrEmpty()) {
                    binding.tvEmptyExpenses.visibility = View.VISIBLE
                    binding.tvEmptyExpenses.text = "No expenses in $currentCategoryName category"
                } else {
                    binding.tvEmptyExpenses.visibility = View.GONE
                }
            }
        }
    }

    private fun showCategoriesList() {
        binding.rvCategories.animate()
            .translationX(binding.rvCategories.width.toFloat())
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                isShowingExpenses = false
                binding.rvCategories.adapter = categoriesAdapter
                supportActionBar?.title = "Categories"
                binding.tvEmptyExpenses.visibility = View.GONE

                try {
                    viewModel.loadCategoriesWithStats()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                binding.rvCategories.translationX = -binding.rvCategories.width.toFloat()
                binding.rvCategories.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }


    private fun showCategoryExpenses(category: CategoryWithStats) {
        try {
            binding.rvCategories.animate()
                .translationX(-binding.rvCategories.width.toFloat())
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    // Switch content
                    isShowingExpenses = true
                    currentCategoryName = category.name
                    binding.rvCategories.adapter = expensesAdapter
                    supportActionBar?.title = category.name
                    viewModel.loadExpensesForCategory(category.id)


                    binding.rvCategories.translationX = binding.rvCategories.width.toFloat()
                    binding.rvCategories.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Open receipt viewer for selected expense
    private fun openReceiptViewer(expense: ExpenseWithDetails) {
        try {
            // Validate expense data before creating intent
            val title = expense.title ?: "Unknown Expense"
            val amount = expense.amount
            val date = expense.date?.time ?: System.currentTimeMillis()
            val category = expense.categoryName ?: "Unknown Category"
            val receiptPath = expense.receiptImagePath

            val intent = Intent(this, ReceiptViewerActivity::class.java).apply {
                putExtra(ReceiptViewerActivity.EXTRA_EXPENSE_TITLE, title)
                putExtra(ReceiptViewerActivity.EXTRA_EXPENSE_AMOUNT, amount)
                putExtra(ReceiptViewerActivity.EXTRA_EXPENSE_DATE, date)
                putExtra(ReceiptViewerActivity.EXTRA_EXPENSE_CATEGORY, category)
                putExtra(ReceiptViewerActivity.EXTRA_RECEIPT_PATH, receiptPath)
            }

            // Check if ReceiptViewerActivity exists before starting
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Show expense details in a simple dialog
                showExpenseDetailsDialog(expense)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Show expense details in a simple dialog
            showExpenseDetailsDialog(expense)
        }
    }

    // Expense details dialog if ReceiptViewerActivity fails
    private fun showExpenseDetailsDialog(expense: ExpenseWithDetails) {
        try {
            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
            val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)

            val title = expense.title ?: "Unknown Expense"
            val amount = format.format(expense.amount)
            val date = dateFormat.format(expense.date ?: Date())
            val category = expense.categoryName ?: "Unknown Category"

            val message = """
                Amount: $amount
                Date: $date
                Category: $category
                ${if (!expense.receiptImagePath.isNullOrBlank()) "\n✓ Has receipt image" else "\n✗ No receipt image"}
            """.trimIndent()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            // Ultimate fallback
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Expense Details")
                .setMessage("Unable to load expense details")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isShowingExpenses) {
                    showCategoriesList()
                } else {
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// Categories Adapter with colored icons
class CategoriesAdapter(
    private val onCategoryClick: (CategoryWithStats) -> Unit
) : RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder>() {

    private var categories = listOf<CategoryWithStats>()

    fun submitList(newCategories: List<CategoryWithStats>?) {
        categories = newCategories ?: emptyList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        if (position < categories.size) {
            holder.bind(categories[position])
        }
    }

    override fun getItemCount() = categories.size

    inner class CategoryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position < categories.size) {
                    try {
                        // Add click animation
                        animateClick {
                            onCategoryClick(categories[position])
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        private fun animateClick(onAnimationEnd: () -> Unit) {
            // Icon bounce animation
            binding.ivCategoryIcon.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(150)
                .withEndAction {
                    binding.ivCategoryIcon.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
                .start()

            // Card scale animation
            binding.root.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(150)
                .withEndAction {
                    binding.root.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .withEndAction {
                            onAnimationEnd()
                        }
                        .start()
                }
                .start()
        }

        fun bind(category: CategoryWithStats) {
            try {
                binding.tvCategoryName.text = category.name ?: "Unknown Category"

                val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
                if (category.expenseCount > 0) {
                    binding.tvCategoryStats.text = "${category.expenseCount} expenses • ${format.format(category.totalAmount)}"
                    binding.tvCategoryStats.visibility = View.VISIBLE
                } else {
                    binding.tvCategoryStats.text = "No expenses"
                    binding.tvCategoryStats.visibility = View.VISIBLE
                }

                // Set icon and color for category
                val iconRes = getCategoryIconResource(category.name ?: "")
                val iconColor = getCategoryColor(category.name ?: "")

                binding.ivCategoryIcon.setImageResource(iconRes)
                binding.ivCategoryIcon.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)

            } catch (e: Exception) {
                e.printStackTrace()
                binding.tvCategoryName.text = "Error loading category"
                binding.tvCategoryStats.text = ""
                binding.ivCategoryIcon.setImageResource(R.drawable.ic_category)
            }
        }

        private fun getCategoryIconResource(categoryName: String): Int {
            return when (categoryName.lowercase().trim()) {
                "food & dining", "food", "dining" -> R.drawable.ic_restaurant
                "groceries", "grocery" -> R.drawable.ic_shopping_cart
                "transportation", "transport" -> R.drawable.ic_directions_car
                "entertainment" -> R.drawable.ic_movie
                "healthcare", "health" -> R.drawable.ic_local_hospital
                "shopping" -> R.drawable.ic_shopping_bag
                "utilities", "utility" -> R.drawable.ic_home
                "others", "other" -> R.drawable.ic_category
                else -> {
                    println("DEBUG: Unknown category: '$categoryName'")
                    R.drawable.ic_category
                }
            }
        }

        private fun getCategoryColor(categoryName: String): Int {
            val context = binding.root.context
            return when (categoryName.lowercase().trim()) {
                "food & dining", "food", "dining" -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
                "groceries", "grocery" -> ContextCompat.getColor(context, android.R.color.holo_green_dark)
                "transportation", "transport" -> ContextCompat.getColor(context, android.R.color.holo_blue_dark)
                "entertainment" -> ContextCompat.getColor(context, android.R.color.holo_purple)
                "healthcare", "health" -> ContextCompat.getColor(context, android.R.color.holo_red_light)
                "shopping" -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
                "utilities", "utility" -> ContextCompat.getColor(context, android.R.color.darker_gray)
                "others", "other" -> ContextCompat.getColor(context, android.R.color.black)
                else -> ContextCompat.getColor(context, android.R.color.black)
            }
        }
    }
}

// Category Expenses Adapter
class CategoryExpensesAdapter(
    private val onExpenseClick: (ExpenseWithDetails) -> Unit = {}
) : RecyclerView.Adapter<CategoryExpensesAdapter.ExpenseViewHolder>() {

    private var expenses = listOf<ExpenseWithDetails>()

    fun submitList(newExpenses: List<ExpenseWithDetails>?) {
        expenses = newExpenses ?: emptyList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        if (position < expenses.size) {
            holder.bind(expenses[position])
        }
    }

    override fun getItemCount() = expenses.size

    inner class ExpenseViewHolder(
        private val binding: ItemExpenseSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position < expenses.size) {
                    try {
                        onExpenseClick(expenses[position])
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        fun bind(expense: ExpenseWithDetails) {
            try {
                binding.tvExpenseTitle.text = expense.title ?: "Unknown Expense"

                val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
                binding.tvExpenseAmount.text = format.format(expense.amount)

                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                binding.tvExpenseCategory.text = dateFormat.format(expense.date)
                binding.tvExpenseDate.visibility = View.GONE

                if (!expense.receiptImagePath.isNullOrBlank()) {
                    binding.ivReceiptIcon.visibility = View.VISIBLE
                } else {
                    binding.ivReceiptIcon.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.tvExpenseTitle.text = "Error loading expense"
                binding.tvExpenseAmount.text = "₱0.00"
                binding.tvExpenseCategory.text = ""
            }
        }
    }
}