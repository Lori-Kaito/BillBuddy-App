package com.billbuddy.app

import android.content.Intent
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
import com.billbuddy.app.databinding.ActivityGroupsBinding
import com.billbuddy.app.databinding.ItemGroupBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.NumberFormat
import java.util.Locale

class GroupsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupsBinding
    private lateinit var viewModel: GroupsViewModel
    private lateinit var adapter: GroupsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[GroupsViewModel::class.java]

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

        adapter = GroupsAdapter(
            onItemClick = { group ->
                val intent = Intent(this, GroupDetailActivity::class.java)
                intent.putExtra("group_id", group.id)
                startActivity(intent)
            },
            onItemLongClick = { group ->
                showGroupOptions(group)
            }
        )

        binding.rvGroups.layoutManager = LinearLayoutManager(this)
        binding.rvGroups.adapter = adapter
    }

    private fun observeData() {
        viewModel.groupsWithCalculations.observe(this) { groups ->
            if (groups.isEmpty()) {
                binding.rvGroups.visibility = View.GONE
                binding.tvEmptyGroups.visibility = View.VISIBLE
            } else {
                binding.rvGroups.visibility = View.VISIBLE
                binding.tvEmptyGroups.visibility = View.GONE
                adapter.submitList(groups)
            }
        }
    }

    private fun showGroupOptions(group: GroupWithDetails) {
        val options = arrayOf("Edit Group", "Delete Group")

        MaterialAlertDialogBuilder(this)
            .setTitle("Group Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditGroupDialog(group)
                    1 -> showDeleteGroupConfirmation(group)
                }
            }
            .show()
    }

    private fun showEditGroupDialog(group: GroupWithDetails) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_group, null)
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)
        val etGroupDescription = dialogView.findViewById<TextInputEditText>(R.id.etGroupDescription)

        etGroupName.setText(group.name)
        etGroupDescription.setText(group.description ?: "")

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Group")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = etGroupName.text.toString().trim()
                val description = etGroupDescription.text.toString().trim()

                if (name.isNotEmpty()) {
                    val updatedGroup = ExpenseGroup(
                        id = group.id,
                        name = name,
                        description = description.ifBlank { null },
                        createdAt = group.createdAt,
                        updatedAt = java.util.Date()
                    )
                    viewModel.updateGroup(updatedGroup)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteGroupConfirmation(group: GroupWithDetails) {
        val messageBuilder = StringBuilder()
        messageBuilder.append("Are you sure you want to delete '${group.name}'?")

        if (group.totalExpenses > 0 || group.memberCount > 0) {
            messageBuilder.append("\n\nThis will also delete:")
            if (group.memberCount > 0) {
                messageBuilder.append("\n• ${group.memberCount} members")
            }
            if (group.totalExpenses > 0) {
                val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
                messageBuilder.append("\n• All expenses (${format.format(group.totalExpenses)} total)")
            }
        }

        messageBuilder.append("\n\nThis action cannot be undone.")

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Group")
            .setMessage(messageBuilder.toString())
            .setPositiveButton("Delete") { _, _ ->
                val groupToDelete = ExpenseGroup(
                    id = group.id,
                    name = group.name,
                    description = group.description,
                    createdAt = group.createdAt,
                    updatedAt = group.updatedAt
                )
                viewModel.deleteGroup(groupToDelete)
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

class GroupsAdapter(
    private val onItemClick: (GroupWithDetails) -> Unit,
    private val onItemLongClick: (GroupWithDetails) -> Unit
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    private var groups = listOf<GroupWithDetails>()

    fun submitList(newGroups: List<GroupWithDetails>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount() = groups.size

    inner class GroupViewHolder(
        private val binding: ItemGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(groups[position])
                }
            }

            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(groups[position])
                    true
                } else {
                    false
                }
            }
        }

        fun bind(group: GroupWithDetails) {
            binding.tvGroupName.text = group.name
            binding.tvGroupDescription.text = group.description ?: "No description"
            binding.tvMemberCount.text = "${group.memberCount} members"

            val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH"))
            binding.tvTotalExpenses.text = format.format(group.totalExpenses)
        }
    }
}