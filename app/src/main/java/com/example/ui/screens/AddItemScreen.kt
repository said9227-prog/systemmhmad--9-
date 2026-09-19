package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ImageViewerDialog
import com.example.util.ImageUtils
import com.example.data.model.Item
import com.example.data.model.ItemCategory
import com.example.data.model.ItemUnit
import com.example.data.model.SupplierCompany
import com.example.ui.viewmodel.AppViewModel
import com.example.util.DateTimeUtils
import com.example.util.FormatUtils
import com.example.util.scrollToTopOnFocus
import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val categoriesList by viewModel.categories.collectAsState()
    val unitsList by viewModel.units.collectAsState()
    val companiesList by viewModel.companies.collectAsState()
    val allItemsList by viewModel.items.collectAsState()
    val individualSuppliersList = remember(allItemsList) {
        allItemsList.map { it.individualSupplierName }.filter { it.isNotBlank() }.distinct()
    }

    // Compute recently added categories (newest first)
    val recentCategories = remember(categoriesList, allItemsList) {
        val categoryTimestampMap = mutableMapOf<String, Long>()
        allItemsList.forEach { item ->
            val trimmed = item.category.trim()
            if (trimmed.isNotBlank()) {
                val prev = categoryTimestampMap[trimmed] ?: 0L
                categoryTimestampMap[trimmed] = maxOf(prev, item.createdAt)
            }
        }
        categoriesList.forEach { cat ->
            val trimmed = cat.name.trim()
            if (trimmed.isNotBlank()) {
                val prev = categoryTimestampMap[trimmed] ?: 0L
                val time = if (cat.createdAt > 0L) cat.createdAt else (cat.id * 1000L)
                categoryTimestampMap[trimmed] = maxOf(prev, time)
            }
        }
        val sorted = categoryTimestampMap.entries.sortedByDescending { it.value }.map { it.key }
        if (sorted.isEmpty()) listOf("عام") else sorted
    }

    // Compute recently added units (newest first)
    val recentUnits = remember(unitsList, allItemsList) {
        val unitTimestampMap = mutableMapOf<String, Long>()
        allItemsList.forEach { item ->
            val trimmed = item.unit.trim()
            if (trimmed.isNotBlank()) {
                val prev = unitTimestampMap[trimmed] ?: 0L
                unitTimestampMap[trimmed] = maxOf(prev, item.createdAt)
            }
        }
        unitsList.forEach { u ->
            val trimmed = u.name.trim()
            if (trimmed.isNotBlank()) {
                val prev = unitTimestampMap[trimmed] ?: 0L
                val time = if (u.createdAt > 0L) u.createdAt else (u.id * 1000L)
                unitTimestampMap[trimmed] = maxOf(prev, time)
            }
        }
        val sorted = unitTimestampMap.entries.sortedByDescending { it.value }.map { it.key }
        if (sorted.isEmpty()) listOf("قطعة") else sorted
    }

    val itemSuggestions by viewModel.itemSuggestions.collectAsState()

    // Form fields
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(recentCategories.firstOrNull() ?: "عام") }
    var selectedUnit by remember { mutableStateOf(recentUnits.firstOrNull() ?: "قطعة") }

    var supplierType by remember { mutableStateOf("شركة") }
    var selectedCompany by remember { mutableStateOf<SupplierCompany?>(companiesList.firstOrNull()) }
    var supplierCompanyNameInput by remember { mutableStateOf("") }
    var companyPhone by remember { mutableStateOf("") }
    var individualSupplierName by remember { mutableStateOf("") }
    var individualSupplierPhone by remember { mutableStateOf("") }
    var individualSupplierNotes by remember { mutableStateOf("") }

    LaunchedEffect(selectedCompany) {
        if (selectedCompany != null) {
            companyPhone = selectedCompany?.phone ?: ""
            supplierCompanyNameInput = selectedCompany?.name ?: ""
        }
    }

    var purchasePriceStr by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    var minQuantityStr by remember { mutableStateOf("3") }
    var purchaseDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var sellingPriceStr by remember { mutableStateOf("") }
    
    var imageUriStr by remember { mutableStateOf<String?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }
    var showImagePreviewDialog by remember { mutableStateOf(false) }
    var showDeleteImageWarning by remember { mutableStateOf(false) }

    // Errors
    var nameError by remember { mutableStateOf<String?>(null) }
    var supplierCompanyError by remember { mutableStateOf<String?>(null) }
    var individualSupplierError by remember { mutableStateOf<String?>(null) }
    var purchasePriceError by remember { mutableStateOf<String?>(null) }
    var sellingPriceError by remember { mutableStateOf<String?>(null) }
    var quantityError by remember { mutableStateOf<String?>(null) }
    var minQuantityError by remember { mutableStateOf<String?>(null) }

    // Dialogs
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddUnitDialog by remember { mutableStateOf(false) }
    var showAddCompanyDialog by remember { mutableStateOf(false) }
    var showSkipSellingPriceDialog by remember { mutableStateOf(false) }

    // Focus state for autocomplete
    var isNameFocused by remember { mutableStateOf(false) }

    // Photo picker
    val scrollState = rememberScrollState()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessingImage = true
                val savedUri = ImageUtils.saveImageSafely(context, uri)
                if (savedUri != null) {
                    if (imageUriStr != null && imageUriStr != savedUri) {
                        ImageUtils.deleteImageFile(context, imageUriStr)
                    }
                    imageUriStr = savedUri
                } else {
                    Toast.makeText(context, "تعذر معالجة الصورة، يرجى اختيار صورة أخرى", Toast.LENGTH_SHORT).show()
                }
                isProcessingImage = false
            }
        }
    }

    // Default category/unit selection
    LaunchedEffect(recentCategories) {
        if (selectedCategory.isBlank() && recentCategories.isNotEmpty()) {
            selectedCategory = recentCategories.first()
        }
    }
    LaunchedEffect(recentUnits) {
        if (selectedUnit.isBlank() && recentUnits.isNotEmpty()) {
            selectedUnit = recentUnits.first()
        }
    }
    LaunchedEffect(companiesList) {
        if (companiesList.isNotEmpty() && selectedCompany == null && supplierCompanyNameInput.isEmpty()) {
            selectedCompany = companiesList.first()
            supplierCompanyNameInput = selectedCompany?.name ?: ""
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            existingCategories = categoriesList.map { it.name },
            onDismiss = { showAddCategoryDialog = false },
            onSave = { categoryName, isPermanent ->
                if (isPermanent) {
                    viewModel.addCategory(categoryName, isPermanent = true)
                }
                selectedCategory = categoryName
                showAddCategoryDialog = false
            }
        )
    }

    if (showAddUnitDialog) {
        AddUnitDialog(
            existingUnits = unitsList.map { it.name },
            onDismiss = { showAddUnitDialog = false },
            onSave = { unitName, isPermanent ->
                if (isPermanent) {
                    viewModel.addUnit(unitName, isPermanent = true)
                }
                selectedUnit = unitName
                showAddUnitDialog = false
            }
        )
    }

    if (showAddCompanyDialog) {
        AddCompanyDialog(
            existingCompanies = companiesList.map { it.name },
            onDismiss = { showAddCompanyDialog = false },
            onSave = { companyName, phone, address, notes ->
                viewModel.addCompany(companyName, phone, address, notes) { newId ->
                    selectedCompany = SupplierCompany(
                        id = newId.toInt(),
                        name = companyName,
                        phone = phone,
                        address = address,
                        notes = notes
                    )
                }
                showAddCompanyDialog = false
            }
        )
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = purchaseDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        purchaseDateMillis = it
                    }
                    showDatePickerDialog = false
                }) {
                    Text("تأكيد", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    if (showDeleteImageWarning) {
        AlertDialog(
            onDismissRequest = { showDeleteImageWarning = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("تحذير: حذف الصورة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حذف صورة هذا الصنف؟ لا يمكن التراجع عن هذه الخطوة.") },
            confirmButton = {
                Button(
                    onClick = {
                        ImageUtils.deleteImageFile(context, imageUriStr)
                        imageUriStr = null
                        showDeleteImageWarning = false
                        showImagePreviewDialog = false
                        Toast.makeText(context, "تم حذف الصورة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، حذف الصورة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteImageWarning = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showImagePreviewDialog && imageUriStr != null) {
        ImageViewerDialog(
            imageUri = imageUriStr!!,
            title = if (name.isNotBlank()) name else "معاينة صورة الصنف",
            onDismiss = { showImagePreviewDialog = false },
            onRequestRemove = {
                showDeleteImageWarning = true
            },
            onRequestReplace = {
                showImagePreviewDialog = false
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }

    var isSaveAndNewRequested by remember { mutableStateOf(false) }

    val performSave: (Double) -> Unit = { finalSellingPrice ->
        val pPrice = purchasePriceStr.toDoubleOrNull() ?: 0.0
        val qty = quantityStr.toIntOrNull() ?: 0
        val minQty = minQuantityStr.toIntOrNull() ?: 3

        viewModel.addOrAccumulateItem(
            name = name,
            barcode = barcode,
            category = selectedCategory,
            unit = selectedUnit,
            supplierType = supplierType,
            supplierCompanyId = if (supplierType == "شركة") selectedCompany?.id else null,
            supplierCompanyName = if (supplierType == "شركة") supplierCompanyNameInput else "",
            companyPhone = if (supplierType == "شركة") companyPhone else "",
            individualSupplierName = if (supplierType == "مورد فردي") individualSupplierName else "",
            individualSupplierPhone = if (supplierType == "مورد فردي") individualSupplierPhone else "",
            individualSupplierNotes = if (supplierType == "مورد فردي") individualSupplierNotes else "",
            purchaseDate = purchaseDateMillis,
            purchasePrice = pPrice,
            sellingPrice = finalSellingPrice,
            quantity = qty,
            minQty = minQty,
            imageUri = imageUriStr,
            onSuccess = { isAccumulated, totalQty ->
                if (isAccumulated) {
                    Toast.makeText(context, "تم تحديث الصنف ($name) وجمع الكمية السابقة والجديدة بنجاح! الكمية الحالية: $totalQty", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "تم حفظ الصنف بنجاح! الكمية: $totalQty", Toast.LENGTH_SHORT).show()
                }
                
                if (isSaveAndNewRequested) {
                    // Reset item fields
                    name = ""
                    barcode = ""
                    purchasePriceStr = ""
                    sellingPriceStr = ""
                    quantityStr = ""
                    imageUriStr = null
                    
                    // Reset errors
                    nameError = null
                    purchasePriceError = null
                    sellingPriceError = null
                    quantityError = null
                    minQuantityError = null
                } else {
                    onNavigateBack()
                }
            }
        )
    }

    val validateAndSave: (Boolean) -> Unit = { saveAndNew ->
        isSaveAndNewRequested = saveAndNew
        nameError = null
        supplierCompanyError = null
        individualSupplierError = null
        purchasePriceError = null
        sellingPriceError = null
        quantityError = null
        minQuantityError = null

        var hasError = false

        if (name.trim().isBlank()) { nameError = "يرجى إدخال اسم الصنف"; hasError = true }
        
        if (supplierType == "شركة") {
            if (supplierCompanyNameInput.trim().isBlank()) { supplierCompanyError = "يرجى إدخال اسم شركة التوريد"; hasError = true }
        } else {
            if (individualSupplierName.trim().isBlank()) { individualSupplierError = "يرجى إدخال اسم المورد الفردي"; hasError = true }
        }

        val pPrice = purchasePriceStr.toDoubleOrNull()
        if (pPrice == null || pPrice < 0) { purchasePriceError = "يرجى إدخال سعر شراء صحيح (صفر أو أكثر)"; hasError = true }
        
        val sPrice = sellingPriceStr.toDoubleOrNull()
        if (sellingPriceStr.isNotBlank() && (sPrice == null || sPrice < 0)) {
            sellingPriceError = "يرجى إدخال سعر بيع صحيح أو ترك الخانة فارغة"
            hasError = true
        }
        
        val qty = quantityStr.toIntOrNull()
        if (qty == null || qty < 0) { quantityError = "يرجى إدخال كمية صحيحة (صفر أو أكثر)"; hasError = true }
        
        val minQty = minQuantityStr.toIntOrNull()
        if (minQty == null || minQty < 0) { minQuantityError = "يرجى إدخال الحد الأدنى صحيح (صفر أو أكثر)"; hasError = true }

        if (!hasError) {
            if (sellingPriceStr.trim().isBlank()) {
                showSkipSellingPriceDialog = true
            } else {
                performSave(sPrice ?: 0.0)
            }
        }
    }

    if (showSkipSellingPriceDialog) {
        AlertDialog(
            onDismissRequest = { showSkipSellingPriceDialog = false },
            icon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("تأكيد تخطي سعر البيع", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "لم تقم بتحديد سعر بيع لهذا الصنف (${name.ifBlank { "الصنف" }}).\n\nهل تريد المتابعة وحفظ الصنف بدون سعر بيع؟ (يمكنك تحديده لاحقاً)",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSkipSellingPriceDialog = false
                        performSave(0.0)
                    }
                ) {
                    Text("نعم، حفظ بدون سعر بيع")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipSellingPriceDialog = false }) {
                    Text("إلغاء لتحديد السعر")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إضافة صنف جديد", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("add_item_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { validateAndSave(true) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ وإضافة آخر", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { validateAndSave(false) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ وخروج", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // Image Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isProcessingImage) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("جاري معالجة وضغط الصورة...", fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else if (imageUriStr != null) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { showImagePreviewDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUriStr)
                                    .size(300)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "صورة الصنف",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Zoom button overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { showImagePreviewDialog = true }
                                    .padding(5.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "معاينة وتكبير", modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                            // Edit photo button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                    .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    .padding(6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "تغيير الصورة", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            // Delete photo button (triggers warning)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                                    .clickable { showDeleteImageWarning = true }
                                    .padding(6.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف الصورة", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text("انقر على الصورة لمعاينتها وتكبيرها بالتفصيل (Zoom)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "إضافة صورة",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Text("إضافة صورة للصنف", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Info Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("معلومات الصنف", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    // SMART AUTOCOMPLETE TEXT FIELD
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { 
                                name = it
                                nameError = null
                                viewModel.searchItemAutocomplete(it)
                            },
                            label = { Text("اسم الصنف *") },
                            placeholder = { Text("مثال: بانادول إكسترا") },
                            isError = nameError != null,
                            supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                        
                        // Suggestion Dropdown
                        if (name.isNotEmpty() && itemSuggestions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 65.dp)
                                    .heightIn(max = 200.dp),
                                shadowElevation = 8.dp,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(modifier = Modifier.verticalScroll(scrollState)) {
                                    itemSuggestions.forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    name = suggestion.name
                                                    selectedCategory = suggestion.category
                                                    selectedUnit = suggestion.unit
                                                    barcode = suggestion.barcode
                                                    viewModel.clearItemSuggestions()
                                                }
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(suggestion.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("التصنيف: ${suggestion.category} - الوحدة: ${suggestion.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("الباركود (اختياري)") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Category field with triangle dropdown for recently added
                        RecentDropdownField(
                            value = selectedCategory,
                            onValueChange = { selectedCategory = it },
                            recentOptions = recentCategories,
                            label = "التصنيف *",
                            icon = Icons.Default.Category,
                            onAddNewClick = { showAddCategoryDialog = true },
                            addNewLabel = "إضافة تصنيف جديد...",
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Unit field with triangle dropdown for recently added
                        RecentDropdownField(
                            value = selectedUnit,
                            onValueChange = { selectedUnit = it },
                            recentOptions = recentUnits,
                            label = "الوحدة *",
                            icon = Icons.Default.Inventory2,
                            onAddNewClick = { showAddUnitDialog = true },
                            addNewLabel = "إضافة وحدة جديدة...",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Supplier Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المورد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { supplierType = "شركة" }) {
                            RadioButton(selected = supplierType == "شركة", onClick = { supplierType = "شركة" })
                            Text("شركة")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { supplierType = "مورد فردي" }) {
                            RadioButton(selected = supplierType == "مورد فردي", onClick = { supplierType = "مورد فردي" })
                            Text("مورد فردي")
                        }
                    }

                    if (supplierType == "شركة") {
                        SearchableDropdownString(
                            value = supplierCompanyNameInput,
                            onValueChange = { input -> 
                                supplierCompanyNameInput = input
                                supplierCompanyError = null
                                // Try to match an existing company
                                val matched = companiesList.find { it.name.equals(input, ignoreCase = true) }
                                if (matched != null) {
                                    selectedCompany = matched
                                    companyPhone = matched.phone
                                } else {
                                    selectedCompany = null
                                }
                            },
                            options = companiesList.map { it.name },
                            label = "اسم الشركة *",
                            isError = supplierCompanyError != null,
                            errorMessage = supplierCompanyError
                        )

                        // Company Phone field
                        OutlinedTextField(
                            value = companyPhone,
                            onValueChange = { companyPhone = it },
                            label = { Text("رقم الشركة") },
                            placeholder = { Text("رقم هاتف الشركة / التوريد") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    } else {
                        SearchableDropdownString(
                            value = individualSupplierName,
                            onValueChange = { 
                                individualSupplierName = it
                                individualSupplierError = null
                                // Auto-fill phone if we have it from another item (optional but nice)
                                val matchedItem = allItemsList.find { item -> item.individualSupplierName.equals(it, ignoreCase = true) && item.individualSupplierPhone.isNotBlank() }
                                if (matchedItem != null) {
                                    individualSupplierPhone = matchedItem.individualSupplierPhone
                                }
                            },
                            options = individualSuppliersList,
                            label = "اسم المورد *",
                            isError = individualSupplierError != null,
                            errorMessage = individualSupplierError
                        )
                        OutlinedTextField(
                            value = individualSupplierPhone,
                            onValueChange = { individualSupplierPhone = it },
                            label = { Text("رقم الهاتف") },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }
            }

            // Financial & Stock Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الشراء والبيع والمخزون", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it; purchasePriceError = null },
                            label = { Text("سعر الشراء *") },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = purchasePriceError != null,
                            supportingText = purchasePriceError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        OutlinedTextField(
                            value = sellingPriceStr,
                            onValueChange = { sellingPriceStr = it; sellingPriceError = null },
                            label = { Text("سعر البيع (اختياري)") },
                            placeholder = { Text("0.0") },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = sellingPriceError != null,
                            supportingText = sellingPriceError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it; quantityError = null },
                            label = { Text("الكمية الحالية *") },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = quantityError != null,
                            supportingText = quantityError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        OutlinedTextField(
                            value = minQuantityStr,
                            onValueChange = { minQuantityStr = it; minQuantityError = null },
                            label = { Text("الحد الأدنى للتنبيه *") },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = minQuantityError != null,
                            supportingText = minQuantityError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                    }

                    // Purchase/Sale Totals Display
                    val parsedQty = quantityStr.toIntOrNull() ?: 0
                    val parsedPurchase = purchasePriceStr.toDoubleOrNull() ?: 0.0
                    val parsedSale = sellingPriceStr.toDoubleOrNull() ?: 0.0
                    if (parsedQty > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (parsedPurchase > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("إجمالي تكلفة الشراء:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${FormatUtils.formatAmount(parsedPurchase * parsedQty)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (parsedSale > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("إجمالي قيمة البيع المتوقعة:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${FormatUtils.formatAmount(parsedSale * parsedQty)}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    }
                                }
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = SimpleDateFormat("yyyy-MM-dd", Locale("ar")).format(Date(purchaseDateMillis)),
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("تاريخ الشراء") },
                        trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth().clickable { showDatePickerDialog = true }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    recentOptions: List<String>,
    label: String,
    icon: ImageVector,
    onAddNewClick: (() -> Unit)? = null,
    addNewLabel: String = "إضافة جديد...",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val displayList = remember(recentOptions, isSearchActive, searchQuery) {
        if (isSearchActive && searchQuery.isNotBlank()) {
            recentOptions.filter { it.contains(searchQuery, ignoreCase = true) }
        } else {
            recentOptions
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { 
            expanded = it
            if (it) {
                isSearchActive = false
                searchQuery = ""
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                searchQuery = newValue
                isSearchActive = true
                expanded = true
            },
            label = { Text(label) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotEmpty() && expanded) {
                        IconButton(
                            onClick = {
                                onValueChange("")
                                searchQuery = ""
                                isSearchActive = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "مسح",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Triangle button (المثلث)
                    IconButton(
                        onClick = {
                            isSearchActive = false
                            searchQuery = ""
                            expanded = !expanded
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("dropdown_triangle_${label}")
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "عرض $label المضافة مؤخراً",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .menuAnchor()
                .scrollToTopOnFocus()
                .fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                isSearchActive = false
            },
            modifier = Modifier
                .widthIn(min = 190.dp)
                .heightIn(max = 290.dp)
        ) {
            // Header showing recently added
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$label المضافة مؤخراً (الأحدث أولاً)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider()

            if (displayList.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "لا توجد نتائج مطابقة لـ \"$searchQuery\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { },
                    enabled = false
                )
            } else {
                displayList.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = option,
                                        fontSize = 13.sp,
                                        fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal,
                                        color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (index < 2) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "مؤخراً",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (option == value) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "محدد",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                            isSearchActive = false
                        }
                    )
                }
            }

            if (onAddNewClick != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = addNewLabel,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        isSearchActive = false
                        onAddNewClick()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableDropdownString(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val filteredOptions = if (value.length >= 2 || expanded) {
        options.filter { it.contains(value, ignoreCase = true) }
    } else {
        emptyList()
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { 
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            trailingIcon = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expanded && value.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "مسح",
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().clickable { onValueChange("") }
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "بحث", modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            isError = isError,
            supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true,
            keyboardOptions = keyboardOptions
        )
        if (expanded) {
            val showAddOption = value.isNotEmpty() && !options.any { it.equals(value, ignoreCase = true) }
            if (filteredOptions.isNotEmpty() || showAddOption) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    filteredOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                    if (showAddOption) {
                        DropdownMenuItem(
                            text = { Text("إضافة '$value' كجديد", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                            onClick = {
                                onValueChange(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// Dialog Composable placeholders (AddCategoryDialog, AddUnitDialog, AddCompanyDialog)
@Composable
fun AddCategoryDialog(
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, isPermanent: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isPermanent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة تصنيف جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم التصنيف *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم التصنيف"; return@Button }
                if (existingCategories.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                    errorMessage = "هذا التصنيف موجود بالفعل."
                    return@Button
                }
                onSave(name.trim(), isPermanent)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AddUnitDialog(
    existingUnits: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, isPermanent: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isPermanent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة وحدة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم الوحدة *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم الوحدة"; return@Button }
                if (existingUnits.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                    errorMessage = "هذه الوحدة موجودة بالفعل."
                    return@Button
                }
                onSave(name.trim(), isPermanent)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AddCompanyDialog(
    existingCompanies: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة شركة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم الشركة *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم الشركة"; return@Button }
                if (existingCompanies.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                    errorMessage = "هذه الشركة موجودة بالفعل."
                    return@Button
                }
                onSave(name.trim(), phone.trim(), address.trim(), notes.trim())
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
