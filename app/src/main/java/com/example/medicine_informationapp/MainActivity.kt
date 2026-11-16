package com.example.medicine_informationapp

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.medicine_informationapp.alarm.AlarmScheduler
import com.example.medicine_informationapp.auth.AuthManager
import com.example.medicine_informationapp.data.api.DrugLabelResult
import com.example.medicine_informationapp.data.api.OpenFdaApiService
import com.example.medicine_informationapp.data.db.AppDatabase
import com.example.medicine_informationapp.data.db.Reminder
import com.example.medicine_informationapp.databinding.ActivityMainBinding
import com.example.medicine_informationapp.databinding.DialogAddReminderBinding
import com.example.medicine_informationapp.ui.RecognizedTextAdapter
import com.example.medicine_informationapp.ui.ReminderAdapter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.tabs.TabLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: AuthManager
    private lateinit var openFdaApi: OpenFdaApiService
    private lateinit var database: AppDatabase

    private lateinit var recognizedTextAdapter: RecognizedTextAdapter
    private lateinit var reminderAdapter: ReminderAdapter

    private var photoUri: Uri? = null

    // Activity result launcher for Google Sign-In
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    authManager.firebaseAuthWithGoogle(idToken) { success, msg ->
                        updateAuthUI()
                        if (success) {
                            Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Auth failed: $msg", Toast.LENGTH_SHORT).show()
                        }
                    }
                } ?: run {
                    updateAuthUI()
                }
            } catch (_: Exception) {
                updateAuthUI()
            }
        }

    // Camera capture launcher
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                photoUri?.let { uri ->
                    binding.ivMedicinePreview.visibility = View.VISIBLE
                    binding.ivMedicinePreview.setImageURI(uri)
                    processImageForText(uri)
                }
            }
        }

    // Gallery image picker launcher
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                binding.ivMedicinePreview.visibility = View.VISIBLE
                binding.ivMedicinePreview.setImageURI(it)
                processImageForText(it)
            }
        }

    // Camera permission launcher
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to capture images", Toast.LENGTH_SHORT).show()
            }
        }

    // Notification permission launcher (Android 13+)
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        authManager = AuthManager(this)
        openFdaApi = OpenFdaApiService.create()
        database = AppDatabase.getDatabase(this)

        setupUI()
        setupTabs()
        setupRecognizedTextRecyclerView()
        setupRemindersRecyclerView()
        checkNotificationPermission()
        updateAuthUI()
        observeReminders()
    }

    private fun setupUI() {
        // Auth Sign In / Sign Out button
        binding.btnAuth.setOnClickListener {
            val user = authManager.getCurrentUser()
            val account = authManager.getGoogleAccount()
            if (user != null || account != null) {
                authManager.signOut {
                    updateAuthUI()
                    Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                }
            } else {
                val signInIntent = authManager.googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        // Capture photo
        binding.btnCaptureImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Pick image from gallery
        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Manual search button
        binding.btnSearch.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            if (query.isNotEmpty()) {
                searchOpenFda(query)
            } else {
                Toast.makeText(this, "Please enter a medicine name", Toast.LENGTH_SHORT).show()
            }
        }

        // Expandable Cards click handlers
        setupExpandableCard(binding.headerDosage, binding.tvDosageContent, binding.ivDosageChevron)
        setupExpandableCard(binding.headerWarnings, binding.tvWarningsContent, binding.ivWarningsChevron)
        setupExpandableCard(binding.headerDoNotUse, binding.tvDoNotUseContent, binding.ivDoNotUseChevron)

        // Add Reminder button
        binding.btnAddReminder.setOnClickListener {
            showAddReminderDialog()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutScannerSection.visibility = View.VISIBLE
                        binding.layoutRemindersSection.visibility = View.GONE
                    }
                    1 -> {
                        binding.layoutScannerSection.visibility = View.GONE
                        binding.layoutRemindersSection.visibility = View.VISIBLE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecognizedTextRecyclerView() {
        recognizedTextAdapter = RecognizedTextAdapter { tappedLine ->
            val cleanedQuery = tappedLine.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
            binding.etSearchQuery.setText(cleanedQuery)
            searchOpenFda(cleanedQuery)
        }
        binding.rvRecognizedLines.layoutManager = LinearLayoutManager(this)
        binding.rvRecognizedLines.adapter = recognizedTextAdapter
    }

    private fun setupRemindersRecyclerView() {
        reminderAdapter = ReminderAdapter(
            onToggleEnable = { reminder, isChecked ->
                val updated = reminder.copy(isEnabled = isChecked)
                lifecycleScope.launch(Dispatchers.IO) {
                    database.reminderDao().updateReminder(updated)
                    if (isChecked) {
                        AlarmScheduler.scheduleAlarm(this@MainActivity, updated)
                    } else {
                        AlarmScheduler.cancelAlarm(this@MainActivity, updated)
                    }
                }
            },
            onDelete = { reminder ->
                lifecycleScope.launch(Dispatchers.IO) {
                    database.reminderDao().deleteReminder(reminder)
                    AlarmScheduler.cancelAlarm(this@MainActivity, reminder)
                }
            }
        )
        binding.rvReminders.layoutManager = LinearLayoutManager(this)
        binding.rvReminders.adapter = reminderAdapter
    }

    private fun observeReminders() {
        lifecycleScope.launch {
            database.reminderDao().getAllReminders().collect { reminders ->
                reminderAdapter.submitList(reminders)
                if (reminders.isEmpty()) {
                    binding.tvNoReminders.visibility = View.VISIBLE
                    binding.rvReminders.visibility = View.GONE
                } else {
                    binding.tvNoReminders.visibility = View.GONE
                    binding.rvReminders.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun launchCamera() {
        val photoFile = File.createTempFile("MED_", ".jpg", cacheDir)
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            photoFile
        )
        photoUri = uri
        takePictureLauncher.launch(uri)
    }

    // ML Kit Text Recognition via Google Play Services
    private fun processImageForText(imageUri: Uri) {
        try {
            val inputImage = InputImage.fromFilePath(this, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            binding.progressBar.visibility = View.VISIBLE
            binding.layoutRecognizedLines.visibility = View.GONE

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    binding.progressBar.visibility = View.GONE
                    val lines = mutableListOf<String>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            if (line.text.isNotBlank()) {
                                lines.add(line.text.trim())
                            }
                        }
                    }

                    if (lines.isNotEmpty()) {
                        binding.layoutRecognizedLines.visibility = View.VISIBLE
                        recognizedTextAdapter.submitList(lines)
                    } else {
                        Toast.makeText(this, "No text recognized in image", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Text recognition failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Failed to load image for recognition", Toast.LENGTH_SHORT).show()
        }
    }

    // openFDA Drug Label Search
    private fun searchOpenFda(brandName: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNotFound.visibility = View.GONE
        binding.layoutLabelResults.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val searchQuery = "openfda.brand_name:\"$brandName\""
                val response = withContext(Dispatchers.IO) {
                    openFdaApi.searchDrugLabel(searchQuery = searchQuery, limit = 1)
                }

                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body()?.results?.isNotEmpty() == true) {
                    val labelResult = response.body()!!.results!!.first()
                    displayLabelDetails(brandName, labelResult)
                } else {
                    val code = response.code()
                    binding.tvNotFound.text = "No matches found (HTTP $code) for \"$brandName\". Try selecting another line."
                    binding.tvNotFound.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvNotFound.text = "Error connecting to openFDA: ${e.localizedMessage}"
                binding.tvNotFound.visibility = View.VISIBLE
            }
        }
    }

    private fun displayLabelDetails(queriedName: String, label: DrugLabelResult) {
        val openFda = label.openfda
        val brand = openFda?.brandName?.firstOrNull() ?: queriedName
        val generic = openFda?.genericName?.joinToString(", ") ?: "Not specified"
        val manufacturer = openFda?.manufacturerName?.joinToString(", ") ?: "Not specified"
        val purpose = label.purpose?.joinToString("\n\n") ?: "Not specified"
        val indications = label.indicationsAndUsage?.joinToString("\n\n") ?: "Not specified"

        binding.tvBrandName.text = brand
        binding.tvGenericName.text = "Generic: $generic"
        binding.tvManufacturer.text = "Manufacturer: $manufacturer"
        binding.tvPurpose.text = "Purpose:\n$purpose"
        binding.tvIndications.text = "Indications & Usage:\n$indications"

        // Expandable Sections
        binding.tvDosageContent.text = label.dosageAndAdministration?.joinToString("\n\n") ?: "No dosage information available."
        binding.tvWarningsContent.text = label.warnings?.joinToString("\n\n") ?: "No warnings specified."
        binding.tvDoNotUseContent.text = label.doNotUse?.joinToString("\n\n") ?: "No 'Do Not Use' guidelines specified."

        binding.layoutLabelResults.visibility = View.VISIBLE
    }

    private fun setupExpandableCard(headerView: View, contentView: View, chevronView: View) {
        headerView.setOnClickListener {
            if (contentView.visibility == View.VISIBLE) {
                contentView.visibility = View.GONE
                chevronView.animate().rotation(0f).setDuration(200).start()
            } else {
                contentView.visibility = View.VISIBLE
                chevronView.animate().rotation(180f).setDuration(200).start()
            }
        }
    }

    private fun updateAuthUI() {
        val user = authManager.getCurrentUser()
        val account = authManager.getGoogleAccount()

        if (user != null) {
            binding.tvUserStatus.text = "Signed in as: ${user.email ?: user.displayName ?: "User"}"
            binding.btnAuth.text = "Sign Out"
        } else if (account != null) {
            binding.tvUserStatus.text = "Signed in as: ${account.email ?: account.displayName ?: "User"}"
            binding.btnAuth.text = "Sign Out"
        } else {
            binding.tvUserStatus.text = "Not signed in"
            binding.btnAuth.text = "Sign In"
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showAddReminderDialog() {
        val dialogBinding = DialogAddReminderBinding.inflate(layoutInflater)

        var selectedHour = 8
        var selectedMinute = 0

        val updateTimeText = {
            dialogBinding.tvSelectedTime.text = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                selectedHour,
                selectedMinute
            )
        }

        dialogBinding.btnSelectTime.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                updateTimeText()
            }, selectedHour, selectedMinute, false).show()
        }

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogBinding.etMedicineName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter medicine name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedDays = mutableListOf<String>()
                if (dialogBinding.cbMon.isChecked) selectedDays.add("Mon")
                if (dialogBinding.cbTue.isChecked) selectedDays.add("Tue")
                if (dialogBinding.cbWed.isChecked) selectedDays.add("Wed")
                if (dialogBinding.cbThu.isChecked) selectedDays.add("Thu")
                if (dialogBinding.cbFri.isChecked) selectedDays.add("Fri")
                if (dialogBinding.cbSat.isChecked) selectedDays.add("Sat")
                if (dialogBinding.cbSun.isChecked) selectedDays.add("Sun")

                val daysString = if (selectedDays.size == 7) "Everyday"
                else if (selectedDays.isEmpty()) "Daily"
                else selectedDays.joinToString(", ")

                val newReminder = Reminder(
                    medicineName = name,
                    hour = selectedHour,
                    minute = selectedMinute,
                    daysOfWeek = daysString,
                    isEnabled = true
                )

                lifecycleScope.launch(Dispatchers.IO) {
                    val id = database.reminderDao().insertReminder(newReminder)
                    val reminderWithId = newReminder.copy(id = id)
                    AlarmScheduler.scheduleAlarm(this@MainActivity, reminderWithId)
                }

                Toast.makeText(this, "Reminder added for $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
