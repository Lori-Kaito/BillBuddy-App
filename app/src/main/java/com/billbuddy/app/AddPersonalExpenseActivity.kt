package com.billbuddy.app

import android.Manifest
import android.app.DatePickerDialog
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
import com.billbuddy.app.databinding.ActivityAddPersonalExpenseBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AddPersonalExpenseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddPersonalExpenseBinding
    private lateinit var viewModel: AddExpenseViewModel
    private var currentPhotoPath: String? = null
    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedCategoryId: Long = 0
    private var capturedBitmap: Bitmap? = null

    // ML Kit text recognizer
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera launcher
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
        binding = ActivityAddPersonalExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AddExpenseViewModel::class.java]

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

        updateDateDisplay()

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
            saveExpense()
        }

        binding.llCameraPrompt.setOnClickListener {
            checkCameraPermission()
        }
    }

    private fun observeData() {
        viewModel.categories.observe(this) { categories ->
            setupCategoryDropdown(categories)
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

        println("=== PERSONAL EXPENSE SCANNING DEBUG ===")
        println("Raw text from ML Kit:")
        println(text)
        println("=======================================")

        // Show scanned text card so user can see what was detected
        // binding.cardScannedText.visibility = View.VISIBLE
        binding.tvScannedText.text = text

        // Extract title first
        val title = extractReceiptTitle(text)
        if (title.isNotBlank() && binding.etExpenseTitle.text.isNullOrBlank()) {
            binding.etExpenseTitle.setText(title)
            Toast.makeText(this, "Found title: $title", Toast.LENGTH_SHORT).show()
            println("DEBUG: Found title: '$title'")
        }

        // Extract total amount
        val amount = findTotalAmount(text)
        amount?.let {
            binding.etAmount.setText(it.toString())
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

        // Look for the first line that could be a store name
        for (line in lines) {
            val cleanLine = line.trim()
            val lowerLine = cleanLine.lowercase(Locale.getDefault())

            println("DEBUG: Checking line: '$cleanLine'")

            // Skip lines that are clearly not store names
            if (shouldSkipLineForTitle(lowerLine, cleanLine)) {
                println("DEBUG: Skipping line (not a title): '$cleanLine'")
                continue
            }

            // Possible store name
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

        // Look for "TOTAL" keyword specifically
        val totalAmount = findAmountNearExactTotal(lines)
        if (totalAmount != null) {
            println("DEBUG: Found amount near TOTAL keyword: $totalAmount")
            return totalAmount
        }

        // Look for amount patterns in the bottom third of receipt
        val bottomAmount = findAmountInBottomSection(lines)
        if (bottomAmount != null) {
            println("DEBUG: Found amount in bottom section: $bottomAmount")
            return bottomAmount
        }

        // Look for other total indicators
        val otherTotalAmount = findAmountNearOtherTotalKeywords(lines)
        if (otherTotalAmount != null) {
            println("DEBUG: Found amount near other total keywords: $otherTotalAmount")
            return otherTotalAmount
        }

        println("DEBUG: No total amount found using any strategy")
        return null
    }

    private fun findAmountNearExactTotal(lines: List<String>): Double? {
        // Look specifically for "TOTAL" keyword
        for (i in lines.indices) {
            val line = lines[i].trim()
            val lowerLine = line.lowercase(Locale.getDefault())

            // Must contain "total" as a standalone word or at start of line
            if (lowerLine.contains("total") && !lowerLine.contains("subtotal")) {
                println("DEBUG: Found TOTAL keyword in line $i: '$line'")

                // Check if amount is on the same line after "total"
                val totalMatch = Regex("""total\s*[:\s]*([P₱]?\s*[0-9,]+\.?[0-9]*)""", RegexOption.IGNORE_CASE)
                val match = totalMatch.find(line)
                if (match != null) {
                    val amount = extractAmountFromText(match.groupValues[1])
                    if (amount != null) {
                        println("DEBUG: Found amount on same line as TOTAL: $amount")
                        return amount
                    }
                }

                // Check next 2 lines for amounts
                for (offset in 1..2) {
                    if (i + offset < lines.size) {
                        val nextLine = lines[i + offset]
                        val amount = extractAmountFromLine(nextLine)
                        if (amount != null && amount >= 10.0) {
                            println("DEBUG: Found total amount in line ${i + offset}: $amount")
                            return amount
                        }
                    }
                }

                // Check if amount is on previous line
                if (i - 1 >= 0) {
                    val prevLine = lines[i - 1]
                    val amount = extractAmountFromLine(prevLine)
                    if (amount != null && amount >= 10.0) {
                        println("DEBUG: Found total amount in previous line: $amount")
                        return amount
                    }
                }
            }
        }
        return null
    }

    private fun findAmountInBottomSection(lines: List<String>): Double? {
        // Look in the bottom 30% of the receipt for the largest amount
        val startIndex = (lines.size * 0.7).toInt()
        val bottomLines = lines.subList(startIndex, lines.size)

        val amounts = mutableListOf<Double>()

        for (line in bottomLines) {
            val amount = extractAmountFromLine(line)
            if (amount != null && amount >= 10.0) {
                amounts.add(amount)
                println("DEBUG: Found amount in bottom section: $amount from '$line'")
            }
        }

        // Return the largest amount found in bottom section
        return amounts.maxOrNull()
    }

    private fun findAmountNearOtherTotalKeywords(lines: List<String>): Double? {
        val otherKeywords = listOf("amount due", "grand total", "net total", "balance due")

        for (keyword in otherKeywords) {
            for (i in lines.indices) {
                val line = lines[i].lowercase(Locale.getDefault())

                if (line.contains(keyword)) {
                    println("DEBUG: Found '$keyword' in line $i: '${lines[i]}'")

                    // Check same line and next line
                    val amountInSameLine = extractAmountFromLine(lines[i])
                    if (amountInSameLine != null && amountInSameLine >= 10.0) {
                        return amountInSameLine
                    }

                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        val amount = extractAmountFromLine(nextLine)
                        if (amount != null && amount >= 10.0) {
                            return amount
                        }
                    }
                }
            }
        }

        return null
    }

    private fun extractAmountFromLine(line: String): Double? {
        if (line.isBlank()) return null

        println("DEBUG: Extracting amount from line: '$line'")

        val amountPatterns = listOf(
            // Philippine peso with currency symbols (highest priority)
            Regex("""P\s*([0-9,]+\.[0-9]{2})"""),     // P24.30, P 24.30
            Regex("""₱\s*([0-9,]+\.[0-9]{2})"""),     // ₱24.30, ₱ 24.30
            Regex("""PHP\s*([0-9,]+\.[0-9]{2})""", RegexOption.IGNORE_CASE), // PHP24.30

            // Decimal amounts that look like money (2 decimal places)
            Regex("""([0-9,]+\.[0-9]{2})"""),         // 24.30, 1,234.56

            // Peso symbols with whole numbers
            Regex("""P\s*([0-9,]+)"""),               // P24, P 150
            Regex("""₱\s*([0-9,]+)"""),               // ₱24, ₱ 150

            // Whole numbers with thousands separators (lower priority)
            Regex("""([0-9]{1,3}(?:,[0-9]{3})+)""")   // 1,500 or 12,345
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

    private fun extractAmountFromText(text: String): Double? {
        // Extract amount from text that might have currency symbols
        val cleanText = text.replace(Regex("""[P₱$]"""), "").trim()
        val amount = cleanText.replace(",", "").toDoubleOrNull()
        return if (amount != null && amount >= 1.0 && amount <= 100000.0) amount else null
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

    private fun saveExpense() {
        val title = binding.etExpenseTitle.text.toString().trim()
        val amountStr = binding.etAmount.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()

        if (title.isEmpty()) {
            binding.etExpenseTitle.error = "Title is required"
            return
        }

        if (amountStr.isEmpty()) {
            binding.etAmount.error = "Amount is required"
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.etAmount.error = "Please enter a valid amount"
            return
        }

        val expense = Expense(
            title = title,
            amount = amount,
            categoryId = selectedCategoryId,
            date = selectedDate.time,
            notes = notes.ifBlank { null },
            receiptImagePath = currentPhotoPath,
            isPersonal = true
        )

        viewModel.saveExpense(expense)

        Toast.makeText(this, "Expense saved successfully", Toast.LENGTH_SHORT).show()
        finish()
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

    override fun onDestroy() {
        super.onDestroy()
        textRecognizer.close()
    }
}