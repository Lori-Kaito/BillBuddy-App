package com.billbuddy.app

import android.graphics.ImageDecoder
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.billbuddy.app.databinding.ActivityGroupDetailBinding
import com.billbuddy.app.databinding.ItemGroupExpenseBinding
import com.billbuddy.app.databinding.ItemGroupMemberBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.app.Dialog
import android.widget.Button

class GroupDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupDetailBinding
    private lateinit var viewModel: GroupDetailViewModel
    private lateinit var memberAdapter: GroupMemberAdapter
    private lateinit var expenseAdapter: GroupExpenseAdapter
    private var groupId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getLongExtra("group_id", 0)
        viewModel = ViewModelProvider(
            this,
            GroupDetailViewModelFactory(application, groupId)
        )[GroupDetailViewModel::class.java]

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

        memberAdapter = GroupMemberAdapter(
            onItemClick = { member ->
                showMemberOptions(member)
            },
            onPaymentStatusChanged = { memberId, isSettled ->
                handleMemberPaymentStatusChange(memberId, isSettled)
            }
        )

        // Initialize expense adapter without click functionality
        expenseAdapter = GroupExpenseAdapter()

        binding.rvMembers.layoutManager = LinearLayoutManager(this)
        binding.rvMembers.adapter = memberAdapter

        binding.rvExpenses.layoutManager = LinearLayoutManager(this)
        binding.rvExpenses.adapter = expenseAdapter
    }

    private fun handleMemberPaymentStatusChange(memberId: Long, isSettled: Boolean) {
        if (isSettled) {
            // Mark all unpaid splits for this member as paid
            viewModel.markMemberAsSettled(memberId)
            Toast.makeText(this, "Member marked as settled", Toast.LENGTH_SHORT).show()
        } else {
            // Mark all paid splits for this member as unpaid
            viewModel.markMemberAsUnsettled(memberId)
            Toast.makeText(this, "Member marked as unsettled", Toast.LENGTH_SHORT).show()
        }

        // Force update the progress bar after a short delay to allow database update
        binding.root.postDelayed({
            updateProgressBar()
        }, 100)
    }

    private fun observeData() {
        viewModel.group.observe(this) { group ->
            group?.let {
                supportActionBar?.title = it.name
                binding.tvGroupDescription.text = it.description ?: "No description"
            }
        }

        viewModel.groupMembers.observe(this) { members ->
            memberAdapter.submitList(members)
            binding.tvMemberCount.text = "${members.size} members"
            updateProgressBar()
        }

        viewModel.memberBalances.observe(this) { balances ->
            memberAdapter.updateBalances(balances)
            updateProgressBar()
        }

        viewModel.groupExpenses.observe(this) { expenses ->
            if (expenses.isEmpty()) {
                binding.rvExpenses.visibility = View.GONE
                binding.tvNoExpenses.visibility = View.VISIBLE
                binding.cardReceiptImage.visibility = View.GONE
            } else {
                binding.rvExpenses.visibility = View.VISIBLE
                binding.tvNoExpenses.visibility = View.GONE
                expenseAdapter.submitList(expenses)

                // Show receipt for the latest expense
                displayLatestExpenseReceipt(expenses)
            }
            updateProgressBar()
        }
    }

    // Receipt display
    private fun displayLatestExpenseReceipt(expenses: List<Expense>) {
        println("DEBUG: Checking for receipts in ${expenses.size} expenses")

        // Get the most recent expense with a receipt
        val expenseWithReceipt = expenses.firstOrNull { expense ->
            !expense.receiptImagePath.isNullOrBlank()
        }

        if (expenseWithReceipt?.receiptImagePath != null) {
            println("DEBUG: Found expense with receipt: ${expenseWithReceipt.title}")
            println("DEBUG: Receipt path: ${expenseWithReceipt.receiptImagePath}")

            try {
                val file = File(expenseWithReceipt.receiptImagePath)
                println("DEBUG: File exists: ${file.exists()}")

                if (file.exists()) {
                    // Load and display the image
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(file)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        BitmapFactory.decodeFile(file.absolutePath)
                    }

                    if (bitmap != null) {
                        binding.ivReceiptImage.setImageBitmap(bitmap)
                        binding.cardReceiptImage.visibility = View.VISIBLE
                        binding.tvReceiptLabel.text = "Receipt for: ${expenseWithReceipt.title}"

                        println("DEBUG: Receipt image loaded successfully")

                        binding.cardReceiptImage.setOnClickListener {
                            println("DEBUG: Receipt card clicked!")
                            showFullSizeReceipt(expenseWithReceipt.receiptImagePath)
                        }

                        binding.ivReceiptImage.setOnClickListener {
                            println("DEBUG: Receipt image clicked!")
                            showFullSizeReceipt(expenseWithReceipt.receiptImagePath)
                        }
                    } else {
                        println("DEBUG: Failed to decode bitmap")
                        binding.cardReceiptImage.visibility = View.GONE
                    }
                } else {
                    println("DEBUG: Receipt file does not exist")
                    binding.cardReceiptImage.visibility = View.GONE
                }
            } catch (e: Exception) {
                println("ERROR: Exception loading receipt: ${e.message}")
                e.printStackTrace()
                binding.cardReceiptImage.visibility = View.GONE
            }
        } else {
            println("DEBUG: No expenses with receipts found")
            binding.cardReceiptImage.visibility = View.GONE
        }
    }

    // Full size receipt viewer
    private fun showFullSizeReceipt(imagePath: String) {
        println("DEBUG: showFullSizeReceipt called with path: $imagePath")

        try {
            val file = File(imagePath)
            if (!file.exists()) {
                println("ERROR: Receipt file not found: $imagePath")
                Toast.makeText(this, "Receipt image not found", Toast.LENGTH_SHORT).show()
                return
            }

            println("DEBUG: Creating dialog...")

            showSimpleReceiptDialog(imagePath)

        } catch (e: Exception) {
            println("ERROR: Exception in showFullSizeReceipt: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error loading receipt: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Simple receipt dialog
    private fun showSimpleReceiptDialog(imagePath: String) {
        try {
            val file = File(imagePath)

            // Load the bitmap
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                BitmapFactory.decodeFile(file.absolutePath)
            }

            if (bitmap != null) {
                // Create ImageView for dialog
                val imageView = ImageView(this)
                imageView.setImageBitmap(bitmap)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                imageView.adjustViewBounds = true

                // Create and show dialog
                MaterialAlertDialogBuilder(this)
                    .setTitle("Receipt")
                    .setView(imageView)
                    .setPositiveButton("Close", null)
                    .show()

                println("DEBUG: Simple dialog shown successfully")
            } else {
                Toast.makeText(this, "Failed to load receipt image", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            println("ERROR: Exception in showSimpleReceiptDialog: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Updates the progress bar:
    private fun updateProgressBar() {
        val members = memberAdapter.getMembers()
        val balances = memberAdapter.getBalances()

        if (members.isEmpty()) {
            binding.progressBarPayments.progress = 0
            binding.tvProgressLabel.text = "No members yet"
            binding.tvProgressPercentage.text = "0%"
            return
        }

        val settledCount = balances.count { it.owedAmount == 0.0 }
        val totalMembers = members.size
        val progressPercentage = if (totalMembers > 0) {
            (settledCount * 100) / totalMembers
        } else {
            0
        }

        binding.progressBarPayments.progress = progressPercentage
        binding.tvProgressLabel.text = "$settledCount of $totalMembers members settled"
        binding.tvProgressPercentage.text = "$progressPercentage%"

        // Change progress bar color based on completion
        val progressTint = when {
            progressPercentage == 100 -> android.R.color.holo_green_dark
            progressPercentage >= 50 -> android.R.color.holo_orange_dark
            else -> android.R.color.holo_red_dark
        }
        binding.progressBarPayments.progressTintList =
            ContextCompat.getColorStateList(this, progressTint)
    }

    private fun showMemberOptions(member: GroupMember) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Member Options")
            .setMessage("What would you like to do with ${member.name}?")
            .setPositiveButton("Edit Name") { _, _ ->
                showEditMemberNameDialog(member)
            }
            .setNeutralButton("Remove Member") { _, _ ->
                showRemoveMemberConfirmation(member)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Method to handle editing member names
    private fun showEditMemberNameDialog(member: GroupMember) {
        val input = TextInputEditText(this)
        input.setText(member.name)
        input.hint = "Member name"
        input.selectAll()

        val paddingInDp = 16
        val paddingInPx = (paddingInDp * resources.displayMetrics.density).toInt()
        input.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Member Name")
            .setMessage("Enter the new name for this member:")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != member.name) {
                    val updatedMember = member.copy(name = newName)
                    viewModel.updateMember(updatedMember)
                    Toast.makeText(this, "Member name updated to '$newName'", Toast.LENGTH_SHORT).show()
                } else if (newName.isEmpty()) {
                    Toast.makeText(this, "Member name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No changes made", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        // Focus the input and show keyboard
        input.requestFocus()
    }

    private fun showRemoveMemberConfirmation(member: GroupMember) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Member")
            .setMessage("Are you sure you want to remove ${member.name} from this group?\n\nThis action cannot be undone.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removeMember(member)
                Toast.makeText(this, "${member.name} has been removed from the group", Toast.LENGTH_SHORT).show()
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

// Adapter for displaying expenses
class GroupExpenseAdapter : RecyclerView.Adapter<GroupExpenseAdapter.ExpenseViewHolder>() {

    private var expenses = listOf<Expense>()

    fun submitList(newExpenses: List<Expense>) {
        expenses = newExpenses
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemGroupExpenseBinding.inflate(
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
        private val binding: ItemGroupExpenseBinding
    ) : RecyclerView.ViewHolder(binding.root) {


        fun bind(expense: Expense) {
            binding.tvExpenseTitle.text = expense.title

            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
            binding.tvExpenseAmount.text = format.format(expense.amount)

            val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
            binding.tvExpenseDate.text = dateFormat.format(expense.date)

            // Show receipt indicator
            if (!expense.receiptImagePath.isNullOrBlank()) {
                binding.ivHasReceipt.visibility = View.VISIBLE
            } else {
                binding.ivHasReceipt.visibility = View.GONE
            }

            // Show notes if they exist
            if (!expense.notes.isNullOrBlank()) {
                binding.tvExpenseNotes.text = "📝 ${expense.notes}"
                binding.tvExpenseNotes.visibility = View.VISIBLE
            } else {
                binding.tvExpenseNotes.visibility = View.GONE
            }
        }
    }
}

class GroupMemberAdapter(
    private val onItemClick: (GroupMember) -> Unit,
    private val onPaymentStatusChanged: (Long, Boolean) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.MemberViewHolder>() {

    private var members = listOf<GroupMember>()
    private var balances = listOf<MemberBalance>()

    fun submitList(newMembers: List<GroupMember>) {
        members = newMembers
        notifyDataSetChanged()
    }

    fun updateBalances(newBalances: List<MemberBalance>) {
        balances = newBalances
        notifyDataSetChanged()
    }

    fun getMembers(): List<GroupMember> = members

    fun getBalances(): List<MemberBalance> = balances

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemGroupMemberBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(members[position])
    }

    override fun getItemCount() = members.size

    inner class MemberViewHolder(
        private val binding: ItemGroupMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(members[position])
                }
            }

            // Prevent checkbox from interfering with card clicks
            binding.cbSettled.setOnClickListener {

            }
        }

        fun bind(member: GroupMember) {
            binding.tvMemberName.text = member.name

            // Set the first letter of the member's name in the icon
            binding.tvMemberIcon.text = member.name.firstOrNull()?.uppercaseChar()?.toString() ?: "M"

            val memberBalance = balances.find { it.memberId == member.id }
            if (memberBalance != null) {
                val owedAmount = memberBalance.owedAmount
                val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))

                if (owedAmount > 0) {
                    binding.tvMemberBalance.text = "Owes ${format.format(owedAmount)}"
                    binding.tvMemberBalance.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_red_dark)
                    )
                    binding.cbSettled.visibility = View.VISIBLE

                    // Clear listener before setting state
                    binding.cbSettled.setOnCheckedChangeListener(null)
                    binding.cbSettled.isChecked = false

                    // Set listener after setting state
                    binding.cbSettled.setOnCheckedChangeListener { _, isChecked ->
                        onPaymentStatusChanged(member.id, isChecked)
                    }

                } else {
                    binding.tvMemberBalance.text = "Settled"
                    binding.tvMemberBalance.setTextColor(
                        binding.root.context.getColor(android.R.color.holo_green_dark)
                    )
                    binding.cbSettled.visibility = View.VISIBLE

                    // Clear listener before setting state
                    binding.cbSettled.setOnCheckedChangeListener(null)
                    binding.cbSettled.isChecked = true

                    // Set listener after setting state
                    binding.cbSettled.setOnCheckedChangeListener { _, isChecked ->
                        onPaymentStatusChanged(member.id, isChecked)
                    }
                }
            } else {
                binding.tvMemberBalance.text = "No expenses"
                binding.tvMemberBalance.setTextColor(
                    binding.root.context.getColor(android.R.color.darker_gray)
                )
                binding.cbSettled.visibility = View.GONE

                // Always clear listener for items without balance
                binding.cbSettled.setOnCheckedChangeListener(null)
            }
        }
    }
}