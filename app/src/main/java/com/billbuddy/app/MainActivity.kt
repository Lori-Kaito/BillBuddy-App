package com.billbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.billbuddy.app.databinding.ActivityMainBinding
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupUI()
        observeData()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)

        // Set click listeners for cards
        binding.cardAddPersonalExpense.setOnClickListener {
            startActivity(Intent(this, AddPersonalExpenseActivity::class.java))
        }

        binding.cardViewPersonalHistory.setOnClickListener {
            startActivity(Intent(this, PersonalHistoryActivity::class.java))
        }

        binding.cardManageGroups.setOnClickListener {
            startActivity(Intent(this, GroupsActivity::class.java))
        }

        binding.cardAddGroupExpense.setOnClickListener {
            startActivity(Intent(this, AddGroupExpenseActivity::class.java))
        }

        binding.cardSpendingSummary.setOnClickListener {
            startActivity(Intent(this, SpendingSummaryActivity::class.java))
        }

        binding.cardCategories.setOnClickListener {
            startActivity(Intent(this, CategoriesActivity::class.java))
        }
    }

    private fun observeData() {
        // Observe personal spending total
        viewModel.personalTotal.observe(this) { total ->
            val formattedAmount = formatCurrency(total ?: 0.0)
            binding.tvPersonalTotal.text = formattedAmount
        }

        // Observes group spending
        viewModel.groupBalance.observe(this) { spending ->
            val formattedAmount = formatCurrency(spending ?: 0.0)
            binding.tvGroupBalance.text = formattedAmount
        }
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
        return format.format(amount)
    }
}