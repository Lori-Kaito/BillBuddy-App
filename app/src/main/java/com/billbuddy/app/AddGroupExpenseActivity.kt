package com.billbuddy.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.billbuddy.app.databinding.ActivityAddGroupExpenseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AddGroupExpenseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddGroupExpenseBinding
    private lateinit var viewModel: AddGroupExpenseViewModel
    private var currentPhotoPath: String? = null
    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedCategoryId: Long = 0
    private var capturedBitmap: Bitmap? = null

    // Store all group members for automatic sharing
    private var allGroupMembers = listOf<GroupMember>()

    // Group creation variables
    private var createdGroupId: Long = 0
    private var isGroupCreated = false

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                displayImage(path)
                binding.btnScanText.isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddGroupExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AddGroupExpenseViewModel::class.java]

        setupUI()
        observeData()
        setupBackPressedCallback()

        // Start by asking how many members
        showMemberCountDialog()
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isGroupCreated) {
                    MaterialAlertDialogBuilder(this@AddGroupExpenseActivity)
                        .setTitle("Cancel Group Expense?")
                        .setMessage("The group has already been created. Are you sure you want to cancel?")
                        .setPositiveButton("Yes, Cancel") { _, _ -> finish() }
                        .setNegativeButton("Continue", null)
                        .show()
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initially hide the form until group is created
        binding.scrollView.visibility = View.GONE
        binding.loadingLayout.visibility = View.VISIBLE

        updateDateDisplay()
        setupAmountWatcher()

        binding.btnCaptureReceipt.setOnClickListener {
            checkCameraPermission()
        }

        binding.btnScanText.setOnClickListener {
            scanTextFromImage()
        }

        binding.btnScanText.isEnabled = false

        binding.etDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSaveExpense.setOnClickListener {
            saveGroupExpense()
        }

        binding.llCameraPrompt.setOnClickListener {
            checkCameraPermission()
        }
    }

    private fun setupAmountWatcher() {
        binding.etAmount.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateShareDisplay()
            }
        })
    }

    private fun observeData() {
        // Load categories
        viewModel.categories.observe(this) { categories ->
            setupCategoryDropdown(categories)
        }

        // Observe group creation
        viewModel.groupCreated.observe(this) { groupId ->
            if (groupId > 0) {
                createdGroupId = groupId
                isGroupCreated = true
                binding.loadingLayout.visibility = View.GONE
                binding.scrollView.visibility = View.VISIBLE

                // Load the newly created group
                viewModel.loadGroupMembers(groupId)
            }
        }

        // Load group members after group is created
        viewModel.groupMembers.observe(this) { members ->
            if (isGroupCreated) {
                allGroupMembers = members
                setupShareDisplay(members)
            }
        }
    }

    private fun showMemberCountDialog() {
        val input = TextInputEditText(this)
        input.hint = "Number of members (2-50)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.setText("2") // Default value

        val textInputLayout = com.google.android.material.textfield.TextInputLayout(this)
        textInputLayout.hint = "Number of members"
        textInputLayout.addView(input)

        val paddingInDp = 24
        val paddingInPx = (paddingInDp * resources.displayMetrics.density).toInt()
        textInputLayout.setPadding(paddingInPx, paddingInPx / 2, paddingInPx, 0)

        MaterialAlertDialogBuilder(this)
            .setTitle("How many members?")
            .setMessage("Please enter the number of members to add to the group.")
            .setView(textInputLayout)
            .setPositiveButton("Next") { _, _ ->
                val memberCountText = input.text.toString().trim()
                val memberCount = memberCountText.toIntOrNull()

                when {
                    memberCount == null -> {
                        Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                        showMemberCountDialog()
                    }
                    memberCount < 2 -> {
                        Toast.makeText(this, "Group must have at least 2 members", Toast.LENGTH_SHORT).show()
                        showMemberCountDialog()
                    }
                    memberCount > 50 -> {
                        Toast.makeText(this, "Maximum 50 members allowed", Toast.LENGTH_SHORT).show()
                        showMemberCountDialog()
                    }
                    else -> {
                        showCreateGroupDialog(memberCount)
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()

        input.requestFocus()
        input.selectAll()
    }

    private fun showCreateGroupDialog(memberCount: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_group_with_members, null)
        val etGroupName = dialogView.findViewById<TextInputEditText>(R.id.etGroupName)
        val etGroupDescription = dialogView.findViewById<TextInputEditText>(R.id.etGroupDescription)
        val membersContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.llMembersContainer)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogCancel)
        val btnCreateGroup = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogCreateGroup)

        membersContainer.removeAllViews()
        repeat(memberCount) { index ->
            val memberInput = TextInputEditText(this)
            memberInput.hint = "Member ${index + 1} Name"

            val layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomMargin = 24
            memberInput.layoutParams = layoutParams

            membersContainer.addView(memberInput)
        }

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Create Group for Expense")
            .setMessage("Enter group details and member names.")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        alertDialog.show()

        // Ensure dialog doesn't get too tall
        alertDialog.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val maxHeight = (displayMetrics.heightPixels * 0.8).toInt()
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(maxHeight, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        btnCancel.setOnClickListener {
            alertDialog.dismiss()
            finish()
        }

        btnCreateGroup.setOnClickListener {
            val groupName = etGroupName.text.toString().trim()
            val description = etGroupDescription.text.toString().trim()

            val members = mutableListOf<String>()
            for (i in 0 until membersContainer.childCount) {
                val input = membersContainer.getChildAt(i) as TextInputEditText
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    members.add(name)
                }
            }

            if (groupName.isNotEmpty() && members.size >= 2) {
                viewModel.createGroupWithMembers(
                    groupName = groupName,
                    description = description.ifBlank { null },
                    memberNames = members
                )
                alertDialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter group name and at least 2 members", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupCategoryDropdown(categories: List<Category>) {
        val categoryNames = categories.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryNames)
        binding.actvCategory.setAdapter(adapter)

        binding.actvCategory.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = categories[position].id
        }

        if (categories.isNotEmpty()) {
            binding.actvCategory.setText(categories[0].name, false)
            selectedCategoryId = categories[0].id
        }
    }

    // Show sharing info
    private fun setupShareDisplay(members: List<GroupMember>) {
        binding.llMemberSelection.removeAllViews()

        if (members.isEmpty()) {
            val emptyText = android.widget.TextView(this)
            emptyText.text = "No members found in this group."
            binding.llMemberSelection.addView(emptyText)
            return
        }

        // Create the sharing info section programmatically
        createShareInfoProgrammatically(members)
        updateShareDisplay()
    }

    private fun createShareInfoProgrammatically(members: List<GroupMember>) {
        // Create the sharing info section programmatically
        val cardView = com.google.android.material.card.MaterialCardView(this)
        cardView.radius = 24f
        cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
        cardView.cardElevation = 4f

        val cardParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.setMargins(0, 16, 0, 16)
        cardView.layoutParams = cardParams

        val containerLayout = android.widget.LinearLayout(this)
        containerLayout.orientation = android.widget.LinearLayout.VERTICAL
        containerLayout.setPadding(48, 32, 48, 32)

        // Title
        val titleText = android.widget.TextView(this)
        titleText.text = "Expense will be shared equally among:"
        titleText.textSize = 16f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        titleText.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.black))
        containerLayout.addView(titleText)

        // Add spacing
        val spacer = android.view.View(this)
        val spacerParams = android.widget.LinearLayout.LayoutParams(0, 24)
        spacer.layoutParams = spacerParams
        containerLayout.addView(spacer)

        // List members
        members.forEach { member ->
            val memberLayout = android.widget.LinearLayout(this)
            memberLayout.orientation = android.widget.LinearLayout.HORIZONTAL
            memberLayout.gravity = android.view.Gravity.CENTER_VERTICAL

            val memberParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            memberParams.setMargins(0, 8, 0, 8)
            memberLayout.layoutParams = memberParams

            // Checkmark icon
            val checkIcon = android.widget.TextView(this)
            checkIcon.text = "✓"
            checkIcon.textSize = 16f
            checkIcon.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_green_dark))
            checkIcon.setTypeface(null, android.graphics.Typeface.BOLD)

            val iconParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            iconParams.setMargins(0, 0, 24, 0)
            checkIcon.layoutParams = iconParams
            memberLayout.addView(checkIcon)

            // Member name
            val memberText = android.widget.TextView(this)
            memberText.text = member.name
            memberText.textSize = 14f
            memberText.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.black))
            memberLayout.addView(memberText)

            containerLayout.addView(memberLayout)
        }

        // Add share calculation text
        val shareText = android.widget.TextView(this)
        shareText.id = android.R.id.text1
        shareText.textSize = 14f
        shareText.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.darker_gray))
        shareText.setTypeface(null, android.graphics.Typeface.ITALIC)

        val shareParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        shareParams.setMargins(0, 24, 0, 0)
        shareText.layoutParams = shareParams
        containerLayout.addView(shareText)

        cardView.addView(containerLayout)
        binding.llMemberSelection.addView(cardView)
    }

    private fun updateShareDisplay() {
        val shareText = binding.llMemberSelection.findViewById<android.widget.TextView>(android.R.id.text1)
        val currentAmount = binding.etAmount.text.toString().toDoubleOrNull() ?: 0.0
        val memberCount = allGroupMembers.size

        if (memberCount > 0 && currentAmount > 0) {
            val sharePerPerson = currentAmount / memberCount
            shareText?.text = "Each person will pay: ₱${"%.2f".format(sharePerPerson)} ($memberCount people sharing)"
        } else if (memberCount > 0) {
            shareText?.text = "$memberCount people will share this expense equally"
        } else {
            shareText?.text = "Loading group members..."
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val photoFile = try {
            createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
            null
        }

        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "com.billbuddy.app.fileprovider",
                it
            )
            takePictureLauncher.launch(photoURI)
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "RECEIPT_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun displayImage(imagePath: String) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, Uri.fromFile(File(imagePath)))
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(File(imagePath)))
            }

            capturedBitmap = bitmap
            binding.ivReceiptPreview.setImageBitmap(bitmap)
            binding.ivReceiptPreview.visibility = View.VISIBLE
            binding.llCameraPrompt.visibility = View.GONE
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanTextFromImage() {
        capturedBitmap?.let { bitmap ->
            val image = InputImage.fromBitmap(bitmap, 0)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    processExtractedText(visionText.text)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: Toast.makeText(this, "Please capture an image first", Toast.LENGTH_SHORT).show()
    }

    private fun processExtractedText(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show()
            return
        }

        println("=== RECEIPT SCANNING DEBUG ===")
        println("Raw text from ML Kit:")
        println(text)
        println("===============================")

        // Show scanned text if the card exists in your layout
        try {
            binding.cardScannedText.visibility = View.VISIBLE
            binding.tvScannedText.text = text
        } catch (e: Exception) {

        }

        // Extract title first
        val title = extractReceiptTitle(text)
        if (title.isNotBlank() && binding.etExpenseTitle.text.isNullOrBlank()) {
            binding.etExpenseTitle.setText(title)
            println("DEBUG: Found title: '$title'")
        }

        // Extract total amount
        val amount = findTotalAmount(text)
        amount?.let {
            binding.etAmount.setText(it.toString())
            updateShareDisplay()
            Toast.makeText(this, "Found total: ₱${String.format("%.2f", it)}", Toast.LENGTH_SHORT).show()
            println("DEBUG: Found amount: ₱$it")
        } ?: run {
            Toast.makeText(this, "Could not find total amount. Please enter manually.", Toast.LENGTH_LONG).show()
            println("DEBUG: No total amount found")
        }
    }

    private fun extractReceiptTitle(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        println("DEBUG: Extracting title from ${lines.size} lines")

        // Look for the first meaningful line that could be a store name
        for (line in lines) {
            val cleanLine = line.trim()
            val lowerLine = cleanLine.lowercase(Locale.getDefault())

            println("DEBUG: Checking line: '$cleanLine'")

            // Skip lines that are clearly not store names
            if (shouldSkipLineForTitle(lowerLine, cleanLine)) {
                println("DEBUG: Skipping line (not a title): '$cleanLine'")
                continue
            }

            // This looks like a potential store name
            if (cleanLine.length >= 3 && cleanLine.length <= 40) {
                println("DEBUG: Found potential title: '$cleanLine'")
                return cleanLine.take(30) // Limit to 30 characters
            }
        }

        println("DEBUG: No suitable title found")
        return ""
    }

    private fun shouldSkipLineForTitle(lowerLine: String, cleanLine: String): Boolean {
        return (
                // Skip lines with long numbers (dates, phone numbers, addresses)
                lowerLine.contains(Regex("""\d{4,}""")) ||
                        // Skip common receipt footer terms
                        lowerLine.contains("vat") ||
                        lowerLine.contains("tax") ||
                        lowerLine.contains("tin") ||
                        lowerLine.contains("address") ||
                        lowerLine.contains("tel") ||
                        lowerLine.contains("phone") ||
                        lowerLine.contains("receipt") ||
                        lowerLine.contains("invoice") ||
                        lowerLine.contains("thank") ||
                        lowerLine.contains("visit") ||
                        lowerLine.contains("www") ||
                        lowerLine.contains(".com") ||
                        // Skip lines with currency symbols
                        lowerLine.contains("₱") ||
                        lowerLine.contains("php") ||
                        lowerLine.contains("peso") ||
                        // Skip lines that start with common amount patterns
                        lowerLine.startsWith("p ") ||
                        lowerLine.startsWith("p.") ||
                        // Skip very short or very long lines
                        cleanLine.length < 3 ||
                        cleanLine.length > 40 ||
                        // Skip lines that are mostly numbers
                        cleanLine.replace(Regex("""[\d\s.,:-]"""), "").length < 2
                )
    }

    private fun findTotalAmount(text: String): Double? {
        println("DEBUG: Starting amount extraction...")

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Look for "TOTAL" keyword and find amount nearby
        val totalAmount = findAmountNearTotal(lines)
        if (totalAmount != null) {
            println("DEBUG: Found amount near TOTAL: $totalAmount")
            return totalAmount
        }

        // Look for other total indicators
        val otherTotalAmount = findAmountNearOtherTotalKeywords(lines)
        if (otherTotalAmount != null) {
            println("DEBUG: Found amount near other total keywords: $otherTotalAmount")
            return otherTotalAmount
        }

        // Find the largest reasonable amount with currency symbol
        val largestCurrencyAmount = findLargestCurrencyAmount(text)
        if (largestCurrencyAmount != null) {
            println("DEBUG: Found largest currency amount: $largestCurrencyAmount")
            return largestCurrencyAmount
        }

        println("DEBUG: No total amount found using any strategy")
        return null
    }

    private fun findAmountNearTotal(lines: List<String>): Double? {
        val totalKeywords = listOf("total", "amount due", "grand total", "net total")

        for (keyword in totalKeywords) {
            for (i in lines.indices) {
                val line = lines[i].lowercase(Locale.getDefault())

                if (line.contains(keyword)) {
                    println("DEBUG: Found '$keyword' in line $i: '${lines[i]}'")

                    // Check current line first
                    val amountInSameLine = extractAmountFromLine(lines[i])
                    if (amountInSameLine != null) {
                        println("DEBUG: Found amount in same line: $amountInSameLine")
                        return amountInSameLine
                    }

                    // Check next 3 lines
                    for (offset in 1..3) {
                        if (i + offset < lines.size) {
                            val nextLine = lines[i + offset]
                            val amount = extractAmountFromLine(nextLine)
                            if (amount != null) {
                                println("DEBUG: Found amount in line ${i + offset}: $amount")
                                return amount
                            }
                        }
                    }

                    // Check previous 2 lines (sometimes amount comes before total)
                    for (offset in 1..2) {
                        if (i - offset >= 0) {
                            val prevLine = lines[i - offset]
                            val amount = extractAmountFromLine(prevLine)
                            if (amount != null) {
                                println("DEBUG: Found amount in previous line ${i - offset}: $amount")
                                return amount
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    private fun findAmountNearOtherTotalKeywords(lines: List<String>): Double? {
        val otherKeywords = listOf("subtotal", "sub total", "amount", "balance", "pay", "due")

        for (keyword in otherKeywords) {
            for (i in lines.indices) {
                val line = lines[i].lowercase(Locale.getDefault())

                if (line.contains(keyword)) {
                    val amountInSameLine = extractAmountFromLine(lines[i])
                    if (amountInSameLine != null) {
                        return amountInSameLine
                    }

                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        val amount = extractAmountFromLine(nextLine)
                        if (amount != null) {
                            return amount
                        }
                    }
                }
            }
        }

        return null
    }

    private fun findLargestCurrencyAmount(text: String): Double? {
        val currencyPatterns = listOf(
            // Philippine peso patterns
            Regex("""₱\s*([0-9,]+\.?[0-9]*)"""),
            Regex("""PHP\s*([0-9,]+\.?[0-9]*)""", RegexOption.IGNORE_CASE),
            Regex("""P\s+([0-9,]+\.[0-9]{2})"""), // P 150.00 format
            Regex("""P([0-9,]+\.[0-9]{2})"""),    // P150.00 format
            // Dollar patterns
            Regex("""\$\s*([0-9,]+\.?[0-9]*)""")
        )

        val amounts = mutableListOf<Double>()

        for (pattern in currencyPatterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount >= 1.0 && amount <= 100000.0) {
                    amounts.add(amount)
                    println("DEBUG: Found currency amount: $amount from '${match.value}'")
                }
            }
        }

        return amounts.maxOrNull()
    }

    private fun extractAmountFromLine(line: String): Double? {
        if (line.isBlank()) return null

        println("DEBUG: Extracting amount from line: '$line'")

        // Comprehensive amount patterns
        val amountPatterns = listOf(
            // Currency symbol patterns
            Regex("""₱\s*([0-9,]+\.?[0-9]*)"""),
            Regex("""PHP\s*([0-9,]+\.?[0-9]*)""", RegexOption.IGNORE_CASE),
            Regex("""P\s+([0-9,]+\.[0-9]{2})"""),
            Regex("""P([0-9,]+\.[0-9]{2})"""),
            Regex("""\$\s*([0-9,]+\.?[0-9]*)"""),

            // Decimal number patterns (likely to be amounts)
            Regex("""([0-9,]+\.[0-9]{2})"""),  // 150.00, 1,500.50
            Regex("""([0-9,]+\.[0-9]{1})"""),  // 150.5

            // Whole number patterns with thousands separators
            Regex("""([0-9]{1,3}(?:,[0-9]{3})+)"""), // 1,500 or 12,345

            // Simple number patterns
            Regex("""([0-9]{2,6})""") // 50 to 999999 (2-6 digits only)
        )

        for ((index, pattern) in amountPatterns.withIndex()) {
            val match = pattern.find(line)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull()

                if (amount != null && amount >= 1.0 && amount <= 100000.0) {
                    println("DEBUG: Pattern $index matched amount: $amount from '${match.value}'")
                    return amount
                }
            }
        }

        return null
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth)
                updateDateDisplay()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        binding.etDate.setText(dateFormat.format(selectedDate.time))
    }

    private fun saveGroupExpense() {
        val title = binding.etExpenseTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()

        // Validation
        if (title.isEmpty()) {
            binding.etExpenseTitle.error = "Title is required"
            binding.etExpenseTitle.requestFocus()
            return
        }

        if (amountStr.isEmpty()) {
            binding.etAmount.error = "Amount is required"
            binding.etAmount.requestFocus()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.etAmount.error = "Please enter a valid amount"
            binding.etAmount.requestFocus()
            return
        }

        if (allGroupMembers.isEmpty()) {
            Toast.makeText(this, "No group members found. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCategoryId == 0L) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        // Automatically split among all group members
        val allMemberIds = allGroupMembers.map { it.id }

        // Save through ViewModel
        viewModel.addGroupExpense(
            groupId = createdGroupId,
            title = title,
            amount = amount,
            categoryId = selectedCategoryId,
            notes = notes.ifBlank { null },
            receiptPath = currentPhotoPath,
            splitAmong = allMemberIds
        )

        Toast.makeText(this, "Group expense saved successfully!", Toast.LENGTH_SHORT).show()

        // Navigate to Groups activity
        val intent = Intent(this, GroupsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isGroupCreated) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Cancel Group Expense?")
                        .setMessage("The group has already been created. Are you sure you want to cancel?")
                        .setPositiveButton("Yes, Cancel") { _, _ -> finish() }
                        .setNegativeButton("Continue", null)
                        .show()
                } else {
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer.close()
    }
}