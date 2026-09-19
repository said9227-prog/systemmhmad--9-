package com.example.util

import android.content.Context
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipFile

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val totalRestored: Int = 0,
    val details: Map<String, Int> = emptyMap()
)

data class BackupPreview(
    val formatVersion: Int,
    val appVersion: String,
    val databaseVersion: Int,
    val timestamp: Long,
    val recordCounts: Map<String, Int>,
    val fileSizeBytes: Long,
    val fileName: String,
    val isCompatible: Boolean,
    val compatibilityMessage: String,
    val tempFile: File
)

object RestoreEngine {

    private fun getMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun restoreFromFile(
        context: Context,
        db: AppDatabase,
        backupFile: File,
        onProgress: suspend (progress: Int, stage: String) -> Unit = { _, _ -> }
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            onProgress(10, "🔍 جاري فحص ملف النسخة الاحتياطية...")

            if (!backupFile.exists() || backupFile.length() <= 0L) {
                return@withContext RestoreResult(false, "ملف النسخة الاحتياطية غير موجود أو فارغ")
            }

            // Step 1: Read ZIP or JSON
            val (manifest, backupPackage) = readBackupArchive(backupFile)
                ?: return@withContext RestoreResult(false, "تعذر قراءة محتويات ملف النسخة الاحتياطية (تنسيق غير صالح)")

            onProgress(30, "🔐 جاري التحقق من سلامة البيانات والتوافق...")

            // Step 2: Compatibility check
            if (manifest.databaseVersion > 12) {
                return@withContext RestoreResult(
                    false,
                    "إصدار قاعدة بيانات النسخة (${manifest.databaseVersion}) أحدث من إصدار التطبيق الحالي (12). يرجى تحديث التطبيق أولاً."
                )
            }

            // Step 3: Safety Backup of current state before touching tables
            onProgress(45, "🛡️ جاري إنشاء نسخة أمان تلقائية قبل البدء...")
            try {
                BackupEngine.createBackup(
                    context = context,
                    db = db,
                    destination = "الهاتف",
                    onProgress = { _, _ -> }
                )
            } catch (e: Exception) {
                // If safety backup fails, log but proceed cautiously
                e.printStackTrace()
            }

            onProgress(60, "♻️ جاري استعادة الجداول وتحديث السجلات داخل معاملة آمنة...")

            // Step 4: Atomic Transaction
            val counts = mutableMapOf<String, Int>()
            db.withTransaction {
                val categories = manifest.categories.ifEmpty { BackupEngine.ALL_CATEGORIES }

                // Invoices and Invoice Items
                if (categories.contains("invoices") || categories.contains("invoiceItems")) {
                    db.invoiceDao().deleteAllInvoiceItems()
                    db.invoiceDao().deleteAllInvoices()
                    if (backupPackage.invoices.isNotEmpty()) {
                        db.invoiceDao().insertAllInvoices(backupPackage.invoices)
                        counts["invoices"] = backupPackage.invoices.size
                    }
                    if (backupPackage.invoiceItems.isNotEmpty()) {
                        db.invoiceDao().insertAllInvoiceItems(backupPackage.invoiceItems)
                        counts["invoiceItems"] = backupPackage.invoiceItems.size
                    }
                }

                // Payments
                if (categories.contains("payments")) {
                    db.paymentDao().deleteAllPayments()
                    if (backupPackage.payments.isNotEmpty()) {
                        db.paymentDao().insertAll(backupPackage.payments)
                        counts["payments"] = backupPackage.payments.size
                    }
                }

                // Installments
                if (categories.contains("installments")) {
                    db.installmentDao().deleteAllInstallments()
                    if (backupPackage.installments.isNotEmpty()) {
                        db.installmentDao().insertAll(backupPackage.installments)
                        counts["installments"] = backupPackage.installments.size
                    }
                }

                // Clients
                if (categories.contains("clients")) {
                    db.clientDao().deleteAllClients()
                    if (backupPackage.clients.isNotEmpty()) {
                        db.clientDao().insertAll(backupPackage.clients)
                        counts["clients"] = backupPackage.clients.size
                    }
                }

                // Supplier Companies
                if (categories.contains("supplierCompanies")) {
                    db.supplierCompanyDao().deleteAllCompanies()
                    if (backupPackage.supplierCompanies.isNotEmpty()) {
                        db.supplierCompanyDao().insertAll(backupPackage.supplierCompanies)
                        counts["supplierCompanies"] = backupPackage.supplierCompanies.size
                    }
                }

                // Items, Categories, Units
                if (categories.contains("items")) {
                    db.itemDao().deleteAllItems()
                    if (backupPackage.items.isNotEmpty()) {
                        db.itemDao().insertAll(backupPackage.items)
                        counts["items"] = backupPackage.items.size
                    }
                }

                if (categories.contains("itemCategories")) {
                    db.itemCategoryDao().deleteAllCategories()
                    if (backupPackage.itemCategories.isNotEmpty()) {
                        db.itemCategoryDao().insertAll(backupPackage.itemCategories)
                        counts["itemCategories"] = backupPackage.itemCategories.size
                    }
                }

                if (categories.contains("itemUnits")) {
                    db.itemUnitDao().deleteAllUnits()
                    if (backupPackage.itemUnits.isNotEmpty()) {
                        db.itemUnitDao().insertAll(backupPackage.itemUnits)
                        counts["itemUnits"] = backupPackage.itemUnits.size
                    }
                }

                // Installment Reminders
                if (categories.contains("installmentReminders")) {
                    db.installmentReminderDao().deleteAllReminders()
                    if (backupPackage.installmentReminders.isNotEmpty()) {
                        db.installmentReminderDao().insertAll(backupPackage.installmentReminders)
                        counts["installmentReminders"] = backupPackage.installmentReminders.size
                    }
                }

                // Audit Logs
                if (categories.contains("auditLogs")) {
                    db.auditLogDao().deleteAllLogs()
                    if (backupPackage.auditLogs.isNotEmpty()) {
                        db.auditLogDao().insertAll(backupPackage.auditLogs)
                        counts["auditLogs"] = backupPackage.auditLogs.size
                    }
                }

                // Store Settings
                if (categories.contains("storeSettings") && backupPackage.storeSettings != null) {
                    db.storeSettingsDao().insertOrUpdateSettings(backupPackage.storeSettings)
                    counts["storeSettings"] = 1
                }
            }

            onProgress(90, "📊 جاري إعادة تدقيق ومطابقة أرصدة العملاء...")

            // Post-restore balance audit
            try {
                val allClients = db.clientDao().getAllClients()
                allClients.forEach { client ->
                    val invoices = db.invoiceDao().getInvoicesByClient(client.id)
                    val payments = db.paymentDao().getPaymentsByClient(client.id)
                    val invoicesTotal = invoices.sumOf { it.totalAmount }
                    val paymentsTotal = payments.sumOf { it.amount }
                    val currentBalance = client.initialBalance + (invoicesTotal - paymentsTotal)
                    if (currentBalance != client.balance) {
                        db.clientDao().updateClient(client.copy(balance = currentBalance))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onProgress(100, "✓ تم استرجاع البيانات بنجاح تام!")

            val total = counts.values.sum()
            RestoreResult(
                success = true,
                message = "تمت استعادة $total سجلاً بنجاح من النسخة الاحتياطية",
                totalRestored = total,
                details = counts
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(
                success = false,
                message = "فشلت عملية الاستعادة: ${e.localizedMessage ?: "حدث خطأ غير متوقع"}"
            )
        }
    }

    suspend fun inspectBackupUri(context: Context, uri: android.net.Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            val tempFileResult = SafStorageManager.copyUriToTempFile(context, uri, "inspect_restore_temp.zip")
            val tempFile = tempFileResult.getOrNull()
                ?: return@withContext Result.failure(tempFileResult.exceptionOrNull() ?: IOException("تعذر قراءة ملف النسخة"))
            inspectBackupFile(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectBackupFile(file: File): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("ملف النسخة الاحتياطية فارغ أو غير موجود"))
            }

            val (manifest, backupPackage) = readBackupArchive(file)
                ?: return@withContext Result.failure(IllegalArgumentException("تنسيق الملف غير صالح أو لا يحتوي على بنية نسخة احتياطية صحيحة"))

            val currentAppDbVersion = 12
            val isCompatible = manifest.databaseVersion <= currentAppDbVersion
            val compatibilityMessage = if (isCompatible) {
                "متوافق تماماً مع إصدار التطبيق وقاعدة البيانات الحالية"
            } else {
                "غير متوافق: إصدار قاعدة البيانات (${manifest.databaseVersion}) أحدث من إصدار التطبيق ($currentAppDbVersion)"
            }

            val counts = if (manifest.recordCounts.isNotEmpty()) {
                manifest.recordCounts
            } else {
                mapOf(
                    "clients" to backupPackage.clients.size,
                    "invoices" to backupPackage.invoices.size,
                    "payments" to backupPackage.payments.size,
                    "installments" to backupPackage.installments.size,
                    "items" to backupPackage.items.size
                )
            }

            Result.success(
                BackupPreview(
                    formatVersion = manifest.formatVersion,
                    appVersion = manifest.appVersion,
                    databaseVersion = manifest.databaseVersion,
                    timestamp = manifest.timestamp,
                    recordCounts = counts,
                    fileSizeBytes = file.length(),
                    fileName = file.name,
                    isCompatible = isCompatible,
                    compatibilityMessage = compatibilityMessage,
                    tempFile = file
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun restoreFromUri(
        context: Context,
        db: AppDatabase,
        uri: android.net.Uri,
        onProgress: suspend (progress: Int, stage: String) -> Unit = { _, _ -> }
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "📥 جاري قراءة ملف النسخة الاحتياطية من المجلد...")
            val tempFileResult = SafStorageManager.copyUriToTempFile(context, uri, "active_restore_temp.zip")
            val tempFile = tempFileResult.getOrNull()
                ?: return@withContext RestoreResult(false, "تعذر قراءة ملف النسخة الاحتياطية: ${tempFileResult.exceptionOrNull()?.localizedMessage}")

            val result = restoreFromFile(context, db, tempFile, onProgress)
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
            result
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(false, "حدث خطأ غير متوقع أثناء استعادة البيانات: ${e.localizedMessage}")
        }
    }

    private fun readBackupArchive(file: File): Pair<BackupManifest, BackupPackage>? {
        val moshi = getMoshi()
        val manifestAdapter = moshi.adapter(BackupManifest::class.java)
        val packageAdapter = moshi.adapter(BackupPackage::class.java)

        return try {
            if (file.extension.equals("zip", ignoreCase = true)) {
                ZipFile(file).use { zip ->
                    val manifestEntry = zip.getEntry("metadata.json") ?: zip.getEntry("manifest.json")
                    val dataEntry = zip.getEntry("data.json")

                    if (dataEntry == null) {
                        // Attempt fallback if individual tables are present
                        return null
                    }

                    val dataBytes = zip.getInputStream(dataEntry).readBytes()
                    val dataJson = String(dataBytes, Charsets.UTF_8)
                    val backupPkg = packageAdapter.fromJson(dataJson) ?: return null

                    val manifest = if (manifestEntry != null) {
                        val manifestBytes = zip.getInputStream(manifestEntry).readBytes()
                        manifestAdapter.fromJson(String(manifestBytes, Charsets.UTF_8)) ?: backupPkg.manifest
                    } else {
                        backupPkg.manifest
                    }

                    // Checksum verification
                    if (manifest.checksum.isNotBlank()) {
                        val actualChecksum = computeSha256(dataBytes)
                        if (!actualChecksum.equals(manifest.checksum, ignoreCase = true)) {
                            android.util.Log.w("RestoreEngine", "Checksum mismatch in backup archive")
                        }
                    }

                    Pair(manifest, backupPkg)
                }
            } else {
                // Fallback: Plain JSON file
                val jsonStr = file.readText(Charsets.UTF_8)
                val backupPkg = packageAdapter.fromJson(jsonStr) ?: return null
                Pair(backupPkg.manifest, backupPkg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
