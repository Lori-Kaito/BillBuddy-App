package com.billbuddy.app

import android.graphics.BitmapFactory
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbuddy.app.databinding.ActivityPersonalHistoryBinding
import com.billbuddy.app.databinding.ItemPersonalExpenseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class PersonalHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalHistoryBinding
    private lateinit var viewModel: PersonalHistoryViewModel
    private lateinit var adapter: PersonalExpenseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[PersonalHistoryViewModel::class.java]

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

        adapter = PersonalExpenseAdapter { expense ->
            showExpenseDetails(expense)
        }

        binding.rvPersonalHistory.layoutManager = LinearLayoutManager(this)
        binding.rvPersonalHistory.adapter = adapter
    }

    private fun observeData() {
        viewModel.personalExpenses.observe(this) { expenses ->
            adapter.submitList(expenses)

            if (expenses.isEmpty()) {
                binding.rvPersonalHistory.visibility = View.GONE
                binding.tvEmptyHistory.visibility = View.VISIBLE
            } else {
                binding.rvPersonalHistory.visibility = View.VISIBLE
                binding.tvEmptyHistory.visibility = View.GONE
            }
        }
    }

    private fun showExpenseDetails(expense: ExpenseWithDetails) {
        val dialogView = createExpenseDetailsView(expense)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Expense Details")
            .setView(dialogView)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Delete") { dialog, _ ->
                dialog.dismiss()
                showDeleteConfirmation(expense)
            }
            .create()

        dialog.show()
    }

    private fun createExpenseDetailsView(expense: ExpenseWithDetails): View {
        val context = this
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(24, 16, 24, 16)

        // Title section
        val titleLabel = TextView(context)
        titleLabel.text = "Title"
        titleLabel.textSize = 14f
        titleLabel.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        layout.addView(titleLabel)

        val titleValue = TextView(context)
        titleValue.text = expense.title
        titleValue.textSize = 18f
        titleValue.setTypeface(null, android.graphics.Typeface.BOLD)
        titleValue.setPadding(0, 8, 0, 16)
        layout.addView(titleValue)

        // Amount section
        val amountLabel = TextView(context)
        amountLabel.text = "Amount"
        amountLabel.textSize = 14f
        amountLabel.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        layout.addView(amountLabel)

        val amountValue = TextView(context)
        val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
        amountValue.text = format.format(expense.amount)
        amountValue.textSize = 20f
        amountValue.setTextColor(ContextCompat.getColor(context, R.color.amount_text_color))
        amountValue.setTypeface(null, android.graphics.Typeface.BOLD)
        amountValue.setPadding(0, 8, 0, 16)
        layout.addView(amountValue)

        // Category section
        val categoryLabel = TextView(context)
        categoryLabel.text = "Category"
        categoryLabel.textSize = 14f
        categoryLabel.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        layout.addView(categoryLabel)

        val categoryValue = TextView(context)
        categoryValue.text = expense.categoryName
        categoryValue.textSize = 16f
        categoryValue.setPadding(0, 8, 0, 16)
        layout.addView(categoryValue)

        // Date section
        val dateLabel = TextView(context)
        dateLabel.text = "Date"
        dateLabel.textSize = 14f
        dateLabel.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        layout.addView(dateLabel)

        val dateValue = TextView(context)
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        dateValue.text = dateFormat.format(expense.date)
        dateValue.textSize = 16f
        dateValue.setPadding(0, 8, 0, 16)
        layout.addView(dateValue)

        // Notes section
        if (!expense.notes.isNullOrBlank()) {
            val notesLabel = TextView(context)
            notesLabel.text = "Notes"
            notesLabel.textSize = 14f
            notesLabel.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            layout.addView(notesLabel)

            val notesValue = TextView(context)
            notesValue.text = expense.notes
            notesValue.textSize = 16f
            notesValue.setPadding(0, 8, 0, 16)
            layout.addView(notesValue)
        }

        // Receipt section
        if (!expense.receiptImagePath.isNullOrBlank()) {
            try {
                val file = File(expense.receiptImagePath)
                if (file.exists()) {
                    val receiptLabel = TextView(context)
                    receiptLabel.text = "Receipt"
                    receiptLabel.textSize = 14f
                    receiptLabel.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    layout.addView(receiptLabel)

                    val receiptClickHint = TextView(context)
                    receiptClickHint.text = "Tap to view full size"
                    receiptClickHint.textSize = 12f
                    receiptClickHint.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    receiptClickHint.setPadding(0, 4, 0, 8)
                    layout.addView(receiptClickHint)

                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(file)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        BitmapFactory.decodeFile(file.absolutePath)
                    }

                    if (bitmap != null) {
                        val receiptImage = ImageView(context)
                        receiptImage.setImageBitmap(bitmap)
                        receiptImage.scaleType = ImageView.ScaleType.CENTER_CROP
                        receiptImage.adjustViewBounds = true

                        val layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            400
                        )
                        layoutParams.setMargins(0, 0, 0, 16)
                        receiptImage.layoutParams = layoutParams

                        receiptImage.isClickable = true
                        receiptImage.isFocusable = true

                        receiptImage.setOnClickListener {
                            showFullSizeReceipt(expense.receiptImagePath)
                        }

                        layout.addView(receiptImage)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return layout
    }

    private fun showFullSizeReceipt(imagePath: String) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Toast.makeText(this, "Receipt image not found", Toast.LENGTH_SHORT).show()
                return
            }

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                BitmapFactory.decodeFile(file.absolutePath)
            }

            if (bitmap != null) {
                val imageView = ImageView(this)
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                imageView.adjustViewBounds = true

                val displayMetrics = resources.displayMetrics
                val maxWidth = (displayMetrics.widthPixels * 0.9).toInt()
                val maxHeight = (displayMetrics.heightPixels * 0.8).toInt()
                imageView.maxWidth = maxWidth
                imageView.maxHeight = maxHeight

                MaterialAlertDialogBuilder(this)
                    .setTitle("Receipt Image")
                    .setView(imageView)
                    .setPositiveButton("Close") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                Toast.makeText(this, "Failed to load receipt image", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation(expense: ExpenseWithDetails) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to delete '${expense.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                val expenseToDelete = Expense(
                    id = expense.id,
                    title = expense.title,
                    amount = expense.amount,
                    categoryId = expense.categoryId,
                    date = expense.date,
                    notes = expense.notes,
                    receiptImagePath = expense.receiptImagePath,
                    isPersonal = expense.isPersonal,
                    groupId = expense.groupId,
                    paidById = expense.paidById,
                    createdAt = expense.createdAt,
                    updatedAt = expense.updatedAt
                )
                viewModel.deleteExpense(expenseToDelete)
                Toast.makeText(this, "Expense deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

class PersonalExpenseAdapter(
    private val onItemClick: (ExpenseWithDetails) -> Unit
) : RecyclerView.Adapter<PersonalExpenseAdapter.ExpenseViewHolder>() {

    private var expenses = listOf<ExpenseWithDetails>()

    fun submitList(newExpenses: List<ExpenseWithDetails>) {
        expenses = newExpenses
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemPersonalExpenseBinding.inflate(
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
        private val binding: ItemPersonalExpenseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(expenses[position])
                }
            }
        }


        fun bind(expense: ExpenseWithDetails) {
            binding.tvExpenseTitle.text = expense.title

            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
            binding.tvExpenseAmount.text = format.format(expense.amount)

            binding.tvExpenseCategory.text = expense.categoryName

            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            binding.tvExpenseDate.text = dateFormat.format(expense.date)

            if (!expense.receiptImagePath.isNullOrBlank()) {
                binding.ivReceiptIndicator.visibility = View.VISIBLE
                binding.ivReceiptIndicator.setImageResource(R.drawable.ic_receipt)
            } else {
                binding.ivReceiptIndicator.visibility = View.GONE
            }
        }
    }
}