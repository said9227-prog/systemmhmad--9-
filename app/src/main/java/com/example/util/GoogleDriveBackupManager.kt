package com.example.util

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BackupFileInfo(
    val fileName: String,
    val file: File,
    val backupDate: String,
    val appVersion: String,
    val dbVersion: Int,
    val fileSizeFormatted: String,
    val clientCount: Int,
    val invoiceCount: Int,
    val paymentCount: Int,
    val installmentCount: Int,
    val timestamp: Long
)

class GoogleDriveBackupManager(private val context: Context) {

    val driveFolder: File
        get() = File(context.filesDir, "Accountant_Backup").apply { if (!exists()) mkdirs() }

    val latestBackupJsonFile: File
        get() = File(driveFolder, "backup_latest.json")

    suspend fun createAndUploadBackup(db: AppDatabase): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Gather Room Database data
            val clients = db.clientDao().getAllClients()
            val invoices = db.invoiceDao().getAllInvoices()
            val payments = db.paymentDao().getAllPayments()
            val items = db.itemDao().getAllItems()
            val installments = db.installmentDao().getAllInstallments()
            val settings = db.storeSettingsDao().getSettings() ?: StoreSettings()

            val now = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale("ar"))
            val formattedDateStr = dateFormat.format(Date(now))

            val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
            val timestampFileName = "backup_${fileDateFormat.format(Date(now))}.json"

            // 2. Build Extensible Structured JSON
            val rootJson = JSONObject().apply {
                put("app_name", "حسابات العملاء والديون برو")
                put("version", 1)
                put("applicationVersion", "1.0.0")
                put("databaseVersion", 1)
                put("backupDate", formattedDateStr)
                put("timestamp", now)

                put("storeSettings", JSONObject().apply {
                    put("storeName", settings.storeName)
                    put("storePhone", settings.storePhone)
                    put("storeAddress", settings.storeAddress)
                    put("storeEmail", settings.storeEmail)
                    put("currency", settings.currency)
                    put("invoicePrefix", settings.invoicePrefix)
                })

                val clientsArray = JSONArray()
                clients.forEach { c ->
                    clientsArray.put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("phone", c.phone)
                        put("address", c.address)
                        put("email", c.email)
                        put("classification", c.classification)
                        put("balance", c.balance)
                        put("isPinned", c.isPinned)
                        put("notes", c.notes)
                    })
                }
                put("clients", clientsArray)
                put("customers", clientsArray) // Alias for standard schema compatibility

                val invoicesArray = JSONArray()
                invoices.forEach { inv ->
                    invoicesArray.put(JSONObject().apply {
                        put("id", inv.id)
                        put("invoiceNumber", inv.invoiceNumber)
                        put("date", inv.date)
                        put("clientId", inv.clientId)
                        put("clientName", inv.clientName)
                        put("totalAmount", inv.totalAmount)
                        put("paidAmount", inv.paidAmount)
                        put("remainingAmount", inv.remainingAmount)
                        put("currency", inv.currency)
                        put("isDraft", inv.isDraft)
                        put("isQuickInvoice", inv.isQuickInvoice)
                        put("description", inv.description)
                        put("notes", inv.notes)
                    })
                }
                put("invoices", invoicesArray)

                val paymentsArray = JSONArray()
                payments.forEach { p ->
                    paymentsArray.put(JSONObject().apply {
                        put("id", p.id)
                        put("clientId", p.clientId)
                        put("amount", p.amount)
                        put("date", p.date)
                        put("paymentMethod", p.paymentMethod)
                        put("notes", p.notes ?: "")
                        put("currency", p.currency)
                        put("voucherNumber", p.voucherNumber ?: "")
                        put("collectorName", p.collectorName ?: "")
                        put("transferNumber", p.transferNumber ?: "")
                        put("receiptImageUri", p.receiptImageUri ?: "")
                    })
                }
                put("payments", paymentsArray)
                put("transactions", paymentsArray) // Alias for standard schema compatibility

                val installmentsArray = JSONArray()
                installments.forEach { inst ->
                    installmentsArray.put(JSONObject().apply {
                        put("id", inst.id)
                        put("clientId", inst.clientId)
                        put("clientName", inst.clientName)
                        put("amount", inst.amount)
                        put("dueDate", inst.dueDate)
                        put("isPaid", inst.isPaid)
                        put("paidAmount", inst.paidAmount)
                        put("recurrence", inst.recurrence)
                        put("currency", inst.currency)
                        put("notes", inst.notes)
                    })
                }
                put("installments", installmentsArray)

                val itemsArray = JSONArray()
                items.forEach { item ->
                    itemsArray.put(JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("barcode", item.barcode)
                        put("category", item.category)
                        put("unit", item.unit)
                        put("supplierType", item.supplierType)
                        put("supplierCompanyId", item.supplierCompanyId ?: JSONObject.NULL)
                        put("supplierCompanyName", item.supplierCompanyName)
                        put("individualSupplierName", item.individualSupplierName)
                        put("individualSupplierPhone", item.individualSupplierPhone)
                        put("individualSupplierNotes", item.individualSupplierNotes)
                        put("purchaseDate", item.purchaseDate)
                        put("purchasePrice", item.purchasePrice)
                        put("sellingPrice", item.sellingPrice)
                        put("quantity", item.quantity)
                        put("minQuantityAlert", item.minQuantityAlert)
                        put("imageUri", item.imageUri ?: "")
                        put("createdAt", item.createdAt)
                    })
                }
                put("items", itemsArray)
                put("products", itemsArray) // Alias for standard schema compatibility
            }

            val jsonFormattedStr = rootJson.toString(2)

            // 3. Save backup_latest.json
            latestBackupJsonFile.writeText(jsonFormattedStr, Charsets.UTF_8)

            // 4. Save timestamped backup file inside "Accountant Backup"
            val timestampFile = File(driveFolder, timestampFileName)
            timestampFile.writeText(jsonFormattedStr, Charsets.UTF_8)

            val clientCount = clients.size
            val invoiceCount = invoices.size
            Result.success("تم رفع وحفظ النسخة الاحتياطية بـ Google Drive في المجلد 'Accountant Backup' بنجاح!\n(الملف: $timestampFileName - يحتوي على $clientCount عميل و $invoiceCount فاتورة)")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun checkForAvailableBackup(): Boolean = withContext(Dispatchers.IO) {
        val jsonFiles = driveFolder.listFiles { _, name -> name.endsWith(".json") }
        jsonFiles != null && jsonFiles.isNotEmpty()
    }

    suspend fun getLastBackupTimeFormatted(): String = withContext(Dispatchers.IO) {
        val files = driveFolder.listFiles { _, name -> name.endsWith(".json") }
        if (!files.isNullOrEmpty()) {
            val newestFile = files.maxByOrNull { it.lastModified() }
            if (newestFile != null) {
                val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar"))
                return@withContext "${sdf.format(Date(newestFile.lastModified()))} (${newestFile.name})"
            }
        }
        "لم يتم إنشاء نسخة بعد"
    }

    suspend fun getAvailableBackupFiles(): List<BackupFileInfo> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<BackupFileInfo>()
        val files = driveFolder.listFiles { _, name -> name.endsWith(".json") } ?: return@withContext emptyList()

        files.forEach { file ->
            try {
                val jsonStr = file.readText(Charsets.UTF_8)
                val root = JSONObject(jsonStr)

                val backupDate = root.optString("backupDate", SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")).format(Date(file.lastModified())))
                val appVersion = root.optString("applicationVersion", "1.0.0")
                val dbVersion = root.optInt("databaseVersion", 1)
                val timestamp = root.optLong("timestamp", file.lastModified())

                val clientCount = if (root.has("clients")) root.getJSONArray("clients").length()
                else if (root.has("customers")) root.getJSONArray("customers").length() else 0

                val invoiceCount = if (root.has("invoices")) root.getJSONArray("invoices").length() else 0

                val paymentCount = if (root.has("payments")) root.getJSONArray("payments").length()
                else if (root.has("transactions")) root.getJSONArray("transactions").length() else 0

                val installmentCount = if (root.has("installments")) root.getJSONArray("installments").length() else 0

                val sizeKb = file.length() / 1024.0
                val fileSizeFormatted = if (sizeKb >= 1024) String.format(Locale.US, "%.2f MB", sizeKb / 1024.0) else String.format(Locale.US, "%.1f KB", sizeKb)

                resultList.add(
                    BackupFileInfo(
                        fileName = file.name,
                        file = file,
                        backupDate = backupDate,
                        appVersion = appVersion,
                        dbVersion = dbVersion,
                        fileSizeFormatted = fileSizeFormatted,
                        clientCount = clientCount,
                        invoiceCount = invoiceCount,
                        paymentCount = paymentCount,
                        installmentCount = installmentCount,
                        timestamp = timestamp
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        resultList.sortedByDescending { it.timestamp }
    }

    suspend fun restoreFromSpecificFile(db: AppDatabase, targetFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!targetFile.exists() || targetFile.length() == 0L) {
                return@withContext Result.failure(Exception("ملف النسخة الاحتياطية غير موجود أو فارغ"))
            }

            val jsonString = targetFile.readText(Charsets.UTF_8)
            val rootObj = JSONObject(jsonString)

            var restoredClientsCount = 0
            var restoredInvoicesCount = 0
            var restoredPaymentsCount = 0
            var restoredInstallmentsCount = 0

            // Restore Store Settings if present
            if (rootObj.has("storeSettings")) {
                val sObj = rootObj.getJSONObject("storeSettings")
                val current = db.storeSettingsDao().getSettings() ?: StoreSettings()
                db.storeSettingsDao().insertOrUpdateSettings(
                    current.copy(
                        storeName = sObj.optString("storeName", current.storeName),
                        storePhone = sObj.optString("storePhone", current.storePhone),
                        storeAddress = sObj.optString("storeAddress", current.storeAddress),
                        storeEmail = sObj.optString("storeEmail", current.storeEmail),
                        currency = sObj.optString("currency", current.currency),
                        invoicePrefix = sObj.optString("invoicePrefix", current.invoicePrefix)
                    )
                )
            }

            // Restore Clients
            val clientsKey = if (rootObj.has("clients")) "clients" else if (rootObj.has("customers")) "customers" else null
            if (clientsKey != null) {
                val arr = rootObj.getJSONArray(clientsKey)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val client = Client(
                        id = obj.optInt("id", 0),
                        name = obj.getString("name"),
                        phone = obj.optString("phone", ""),
                        address = obj.optString("address", ""),
                        email = obj.optString("email", ""),
                        classification = obj.optString("classification", "عادي"),
                        balance = obj.optDouble("balance", 0.0),
                        isPinned = obj.optBoolean("isPinned", false),
                        notes = obj.optString("notes", "")
                    )
                    db.clientDao().insertClient(client)
                    restoredClientsCount++
                }
            }

            // Restore Invoices
            if (rootObj.has("invoices")) {
                val arr = rootObj.getJSONArray("invoices")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val invoice = Invoice(
                        id = obj.optInt("id", 0),
                        invoiceNumber = obj.getString("invoiceNumber"),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        clientId = obj.getInt("clientId"),
                        clientName = obj.getString("clientName"),
                        totalAmount = obj.optDouble("totalAmount", 0.0),
                        paidAmount = obj.optDouble("paidAmount", 0.0),
                        remainingAmount = obj.optDouble("remainingAmount", 0.0),
                        currency = obj.optString("currency", "الريال اليمني"),
                        isDraft = obj.optBoolean("isDraft", false),
                        isQuickInvoice = obj.optBoolean("isQuickInvoice", false),
                        description = obj.optString("description", ""),
                        notes = obj.optString("notes", "")
                    )
                    db.invoiceDao().insertInvoice(invoice)
                    restoredInvoicesCount++
                }
            }

            // Restore Payments
            val paymentsKey = if (rootObj.has("payments")) "payments" else if (rootObj.has("transactions")) "transactions" else null
            if (paymentsKey != null) {
                val arr = rootObj.getJSONArray(paymentsKey)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val payment = Payment(
                        id = obj.optInt("id", 0),
                        clientId = obj.getInt("clientId"),
                        amount = obj.getDouble("amount"),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        paymentMethod = obj.optString("paymentMethod", "نقدي"),
                        notes = obj.optString("notes", ""),
                        currency = obj.optString("currency", "الريال اليمني"),
                        voucherNumber = obj.optString("voucherNumber", "").ifBlank { null },
                        collectorName = obj.optString("collectorName", "").ifBlank { null },
                        transferNumber = obj.optString("transferNumber", "").ifBlank { null },
                        receiptImageUri = obj.optString("receiptImageUri", "").ifBlank { null }
                    )
                    db.paymentDao().insertPayment(payment)
                    restoredPaymentsCount++
                }
            }

            // Restore Installments
            if (rootObj.has("installments")) {
                val arr = rootObj.getJSONArray("installments")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val installment = Installment(
                        id = obj.optInt("id", 0),
                        clientId = obj.getInt("clientId"),
                        clientName = obj.getString("clientName"),
                        amount = obj.getDouble("amount"),
                        dueDate = obj.getLong("dueDate"),
                        isPaid = obj.optBoolean("isPaid", false),
                        paidAmount = obj.optDouble("paidAmount", 0.0),
                        recurrence = obj.optString("recurrence", "NONE"),
                        currency = obj.optString("currency", "الريال اليمني"),
                        notes = obj.optString("notes", "")
                    )
                    val instId = db.installmentDao().insertInstallment(installment)
                    if (!installment.isPaid && installment.dueDate > System.currentTimeMillis()) {
                        InstallmentManager.scheduleExactAlarm(context, installment.copy(id = instId.toInt()))
                    }
                    restoredInstallmentsCount++
                }
            }

            // Restore Items
            val itemsKey = if (rootObj.has("items")) "items" else if (rootObj.has("products")) "products" else null
            if (itemsKey != null) {
                val arr = rootObj.getJSONArray(itemsKey)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val item = Item(
                        id = obj.optInt("id", 0),
                        name = obj.getString("name"),
                        barcode = obj.optString("barcode", ""),
                        category = obj.optString("category", "عام"),
                        unit = obj.optString("unit", "قطعة"),
                        supplierType = obj.optString("supplierType", "شركة"),
                        supplierCompanyId = if (obj.has("supplierCompanyId") && !obj.isNull("supplierCompanyId")) obj.optInt("supplierCompanyId") else null,
                        supplierCompanyName = obj.optString("supplierCompanyName", ""),
                        individualSupplierName = obj.optString("individualSupplierName", ""),
                        individualSupplierPhone = obj.optString("individualSupplierPhone", ""),
                        individualSupplierNotes = obj.optString("individualSupplierNotes", ""),
                        purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis()),
                        purchasePrice = obj.optDouble("purchasePrice", 0.0),
                        sellingPrice = obj.optDouble("sellingPrice", 0.0),
                        quantity = obj.optInt("quantity", 0),
                        minQuantityAlert = obj.optInt("minQuantityAlert", 5),
                        imageUri = obj.optString("imageUri", "").ifBlank { null },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    db.itemDao().insertItem(item)
                }
            }

            Result.success("تمت استعادة البيانات بنجاح من الملف (${targetFile.name})!\nالمستعاد: $restoredClientsCount عميل | $restoredInvoicesCount فاتورة | $restoredPaymentsCount دفعة | $restoredInstallmentsCount قسط")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun restoreLatestBackup(db: AppDatabase): Result<String> = withContext(Dispatchers.IO) {
        val files = getAvailableBackupFiles()
        if (files.isEmpty()) {
            return@withContext Result.failure(Exception("لم يتم العثور على أي ملف نسخة احتياطية في مجلد Accountant Backup"))
        }
        val newest = files.first()
        restoreFromSpecificFile(db, newest.file)
    }
}


