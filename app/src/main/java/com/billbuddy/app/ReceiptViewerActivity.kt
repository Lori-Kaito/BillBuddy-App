package com.billbuddy.app

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.billbuddy.app.databinding.ActivityReceiptViewerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ReceiptViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiptViewerBinding

    companion object {
        const val EXTRA_EXPENSE_TITLE = "extra_expense_title"
        const val EXTRA_EXPENSE_AMOUNT = "extra_expense_amount"
        const val EXTRA_EXPENSE_DATE = "extra_expense_date"
        const val EXTRA_EXPENSE_CATEGORY = "extra_expense_category"
        const val EXTRA_RECEIPT_PATH = "extra_receipt_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityReceiptViewerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setupUI()
            loadExpenseData()
        } catch (e: Exception) {
            e.printStackTrace()
            // If binding fails, finish the activity
            Toast.makeText(this, "Error loading receipt viewer", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Receipt Details"
        } catch (e: Exception) {
            e.printStackTrace()
            // Set title manually if toolbar setup fails
            title = "Receipt Details"
        }
    }

    private fun loadExpenseData() {
        try {
            // Get data from intent with safe defaults
            val title = intent.getStringExtra(EXTRA_EXPENSE_TITLE) ?: "Unknown Expense"
            val amount = intent.getDoubleExtra(EXTRA_EXPENSE_AMOUNT, 0.0)
            val date = intent.getLongExtra(EXTRA_EXPENSE_DATE, System.currentTimeMillis())
            val category = intent.getStringExtra(EXTRA_EXPENSE_CATEGORY) ?: "Unknown Category"
            val receiptPath = intent.getStringExtra(EXTRA_RECEIPT_PATH)

            // Display expense details safely
            binding.tvExpenseTitle.text = title

            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
            binding.tvExpenseAmount.text = format.format(amount)

            val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
            binding.tvExpenseDate.text = dateFormat.format(Date(date))

            binding.tvExpenseCategory.text = category

            // Load receipt image
            loadReceiptImage(receiptPath)
        } catch (e: Exception) {
            e.printStackTrace()
            // Show error state
            binding.tvExpenseTitle.text = "Error loading expense"
            binding.tvExpenseAmount.text = "₱0.00"
            binding.tvExpenseDate.text = "Unknown date"
            binding.tvExpenseCategory.text = "Unknown category"

            // Hide receipt section
            binding.ivReceiptImage.visibility = View.GONE
            if (::binding.isInitialized) {
                try {
                    binding.llNoReceiptContainer.visibility = View.VISIBLE
                    binding.tvNoReceipt.visibility = View.VISIBLE
                    binding.tvNoReceipt.text = "Error loading expense data"
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        }
    }

    private fun loadReceiptImage(receiptPath: String?) {
        try {
            if (receiptPath.isNullOrBlank()) {
                // No receipt available - show icon and message
                binding.ivReceiptImage.visibility = View.GONE
                binding.llNoReceiptContainer.visibility = View.VISIBLE
                binding.tvNoReceipt.visibility = View.VISIBLE
                binding.tvNoReceipt.text = "No scanned receipt for this Personal Expense"
            } else {
                // Check if receipt file exists
                val receiptFile = File(receiptPath)
                if (receiptFile.exists()) {
                    // Load the receipt image and make it clickable
                    binding.ivReceiptImage.visibility = View.VISIBLE
                    binding.llNoReceiptContainer.visibility = View.GONE
                    binding.tvNoReceipt.visibility = View.GONE

                    // Try to load with Glide first, fallback to basic loading
                    try {
                        // Check if Glide is available
                        val glideClass = Class.forName("com.bumptech.glide.Glide")
                        val glide = glideClass.getMethod("with", android.content.Context::class.java).invoke(null, this)
                        val requestBuilder = glide.javaClass.getMethod("load", File::class.java).invoke(glide, receiptFile)
                        requestBuilder.javaClass.getMethod("into", ImageView::class.java).invoke(requestBuilder, binding.ivReceiptImage)
                    } catch (glideException: Exception) {
                        // Fallback to basic bitmap loading
                        loadImageBasic(receiptFile)
                    }

                    // Make receipt image clickable for full-screen view
                    binding.ivReceiptImage.setOnClickListener {
                        showFullScreenReceipt(receiptPath)
                    }

                    // Add visual feedback for clickability
                    binding.ivReceiptImage.isClickable = true
                    binding.ivReceiptImage.isFocusable = true
                    try {
                        binding.ivReceiptImage.foreground = getDrawable(android.R.attr.selectableItemBackground)
                    } catch (e: Exception) {
                        // Ignore if foreground setting fails
                    }
                } else {
                    // Receipt file doesn't exist - show icon and error message
                    binding.ivReceiptImage.visibility = View.GONE
                    binding.llNoReceiptContainer.visibility = View.VISIBLE
                    binding.tvNoReceipt.visibility = View.VISIBLE
                    binding.tvNoReceipt.text = "Receipt file not found"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Error loading receipt - show error message
            binding.ivReceiptImage.visibility = View.GONE
            binding.llNoReceiptContainer.visibility = View.VISIBLE
            binding.tvNoReceipt.visibility = View.VISIBLE
            binding.tvNoReceipt.text = "Error loading receipt"
        }
    }

    private fun loadImageBasic(receiptFile: File) {
        try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(receiptFile.absolutePath)
            if (bitmap != null) {
                binding.ivReceiptImage.setImageBitmap(bitmap)
            } else {
                throw Exception("Failed to decode bitmap")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.ivReceiptImage.visibility = View.GONE
            binding.llNoReceiptContainer.visibility = View.VISIBLE
            binding.tvNoReceipt.visibility = View.VISIBLE
            binding.tvNoReceipt.text = "Error loading receipt image"
        }
    }

    // Show full-screen receipt viewer
    private fun showFullScreenReceipt(receiptPath: String) {
        try {
            val receiptFile = File(receiptPath)
            if (!receiptFile.exists()) {
                Toast.makeText(this, "Receipt image not found", Toast.LENGTH_SHORT).show()
                return
            }

            // Create ImageView for full-screen display
            val imageView = ImageView(this)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.adjustViewBounds = true

            // Load image with basic method
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(receiptFile.absolutePath)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this, "Failed to load receipt image", Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error loading receipt: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }

            // Show in dialog
            MaterialAlertDialogBuilder(this)
                .setTitle("Receipt Image")
                .setView(imageView)
                .setPositiveButton("Close", null)
                .show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error showing receipt: ${e.message}", Toast.LENGTH_SHORT).show()
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