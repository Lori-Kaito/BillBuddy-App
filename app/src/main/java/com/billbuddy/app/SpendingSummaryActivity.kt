package com.billbuddy.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbuddy.app.databinding.ActivitySpendingSummaryBinding
import com.billbuddy.app.databinding.ItemExpenseSummaryBinding
import com.google.android.material.chip.Chip
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class SpendingSummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpendingSummaryBinding
    private lateinit var viewModel: SpendingSummaryViewModel
    private lateinit var expensesAdapter: ExpenseSummaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpendingSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[SpendingSummaryViewModel::class.java]

        setupUI()
        observeData()
        setupBackPressedCallback()
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Setup expenses RecyclerView
        expensesAdapter = ExpenseSummaryAdapter()
        binding.rvPurchases.layoutManager = LinearLayoutManager(this)
        binding.rvPurchases.adapter = expensesAdapter

        // Period selection
        binding.chipWeek.setOnClickListener {
            selectPeriod(SpendingSummaryViewModel.Period.WEEK)
        }

        binding.chipMonth.setOnClickListener {
            selectPeriod(SpendingSummaryViewModel.Period.MONTH)
        }

        binding.chipYear.setOnClickListener {
            selectPeriod(SpendingSummaryViewModel.Period.YEAR)
        }
    }

    private fun selectPeriod(period: SpendingSummaryViewModel.Period) {
        viewModel.selectPeriod(period)
        updatePeriodSelection(period)
    }

    private fun selectYear(year: String) {
        viewModel.selectYear(year)
        updateYearSelection(year)
    }

    private fun observeData() {
        // Observe available years
        viewModel.availableYears.observe(this) { years ->
            setupYearChips(years)
        }

        // Observe selected year
        viewModel.selectedYear.observe(this) { year ->
            updateYearSelection(year)
        }

        // Observe selected period
        viewModel.selectedPeriod.observe(this) { period ->
            updatePeriodSelection(period)
        }

        // Observe total expense
        viewModel.totalExpense.observe(this) { total ->
            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
            binding.tvTotalAmount.text = format.format(total)
        }

        // Observe personal expenses for the selected period
        viewModel.personalExpenses.observe(this) { expenses ->
            expensesAdapter.submitList(expenses)

            // Show or hide empty state
            if (expenses.isEmpty()) {
                binding.rvPurchases.visibility = View.GONE
                binding.tvEmptyPurchases.visibility = View.VISIBLE
            } else {
                binding.rvPurchases.visibility = View.VISIBLE
                binding.tvEmptyPurchases.visibility = View.GONE
            }
        }

        viewModel.categoryExpenses.observe(this) { categoryExpenses ->
            // Handle category expenses as needed
        }
    }

    private fun setupYearChips(years: List<String>) {
        binding.chipGroupYears.removeAllViews()

        years.forEach { year ->
            val chip = Chip(this)
            chip.text = year
            chip.isCheckable = true

            val primaryColor = androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary)
            val transparentColor = android.graphics.Color.TRANSPARENT
            val whiteColor = android.graphics.Color.WHITE

            chip.setChipBackgroundColorResource(android.R.color.transparent)
            chip.chipStrokeWidth = 2f

            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )

            val backgroundColors = intArrayOf(primaryColor, transparentColor)
            val textColors = intArrayOf(whiteColor, primaryColor)
            val strokeColors = intArrayOf(primaryColor, primaryColor)

            chip.chipBackgroundColor = android.content.res.ColorStateList(states, backgroundColors)
            chip.setTextColor(android.content.res.ColorStateList(states, textColors))
            chip.setChipStrokeColor(android.content.res.ColorStateList(states, strokeColors))

            chip.setOnClickListener {
                selectYear(year)
            }
            binding.chipGroupYears.addView(chip)
        }
    }

    private fun updateYearSelection(selectedYear: String) {
        for (i in 0 until binding.chipGroupYears.childCount) {
            val chip = binding.chipGroupYears.getChildAt(i) as Chip
            chip.isChecked = chip.text == selectedYear
        }
    }

    private fun updatePeriodSelection(period: SpendingSummaryViewModel.Period) {
        // Reset all chips
        binding.chipWeek.isChecked = false
        binding.chipMonth.isChecked = false
        binding.chipYear.isChecked = false

        // Set selected chip
        when (period) {
            SpendingSummaryViewModel.Period.WEEK -> binding.chipWeek.isChecked = true
            SpendingSummaryViewModel.Period.MONTH -> binding.chipMonth.isChecked = true
            SpendingSummaryViewModel.Period.YEAR -> binding.chipYear.isChecked = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

// Expenses adapter for purchases list
class ExpenseSummaryAdapter : RecyclerView.Adapter<ExpenseSummaryAdapter.ExpenseViewHolder>() {

    private var expenses = listOf<ExpenseWithDetails>()

    fun submitList(newExpenses: List<ExpenseWithDetails>) {
        expenses = newExpenses
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
        holder.bind(expenses[position])
    }

    override fun getItemCount() = expenses.size

    inner class ExpenseViewHolder(
        private val binding: ItemExpenseSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: ExpenseWithDetails) {
            binding.tvExpenseTitle.text = expense.title

            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
            binding.tvExpenseAmount.text = format.format(expense.amount)

            binding.tvExpenseCategory.text = expense.categoryName

            val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
            binding.tvExpenseDate.text = dateFormat.format(expense.date)

            // Show receipt indicator if available
            if (!expense.receiptImagePath.isNullOrBlank()) {
                binding.ivReceiptIcon.visibility = View.VISIBLE
            } else {
                binding.ivReceiptIcon.visibility = View.GONE
            }
        }
    }
}