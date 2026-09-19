package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.data.model.StoreSettings
import com.example.domain.report.ReportData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportPdfManager {

    fun exportFinancialReportToPdf(
        context: Context,
        reportData: ReportData,
        settings: StoreSettings
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.CENTER }
        val subtitlePaint = Paint().apply { textSize = 12f; color = Color.DKGRAY; textAlign = Paint.Align.CENTER }
        val headerPaint = Paint().apply { textSize = 14f; isFakeBoldText = true; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val textPaint = Paint().apply { textSize = 12f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        var y = 50f
        
        fun checkNewPage(neededHeight: Float = 40f) {
            if (y + neededHeight > 800f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        // Title
        canvas.drawText("التقرير المالي والإحصائي", 595f / 2f, y, titlePaint)
        y += 20f
        canvas.drawText(settings.storeName, 595f / 2f, y, titlePaint)
        y += 30f
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateRangeStr = "الفترة: من ${sdf.format(Date(reportData.dateRange.start))} إلى ${sdf.format(Date(reportData.dateRange.end))}"
        canvas.drawText(dateRangeStr, 595f / 2f, y, subtitlePaint)
        y += 20f
        canvas.drawText("العملة: ${reportData.selectedCurrency}", 595f / 2f, y, subtitlePaint)
        y += 30f

        canvas.drawLine(50f, y, 545f, y, linePaint)
        y += 30f

        // For each currency
        reportData.currencyReports.forEach { cr ->
            checkNewPage(200f)
            
            canvas.drawText("--- عملة التقرير: ${cr.currency} ---", 545f, y, headerPaint)
            y += 25f

            // Sales
            canvas.drawText("ملخص المبيعات:", 545f, y, headerPaint)
            y += 20f
            canvas.drawText("إجمالي المبيعات: ${FormatUtils.formatAmount(cr.salesReport.totalSales)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("مرتجعات المبيعات: ${FormatUtils.formatAmount(cr.salesReport.totalReturns)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("صافي المبيعات: ${FormatUtils.formatAmount(cr.salesReport.netSales)}", 545f, y, textPaint)
            y += 30f

            // Collection
            canvas.drawText("تقرير التحصيل:", 545f, y, headerPaint)
            y += 20f
            canvas.drawText("إجمالي المقبوضات: ${FormatUtils.formatAmount(cr.collectionReport.totalCollected)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("إجمالي المتبقي: ${FormatUtils.formatAmount(cr.collectionReport.totalRemaining)}", 545f, y, textPaint)
            y += 30f

            // Customers
            canvas.drawText("تقرير العملاء والديون:", 545f, y, headerPaint)
            y += 20f
            canvas.drawText("العملاء المتفاعلون: ${cr.customerReport.activeCustomersCount}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("ديون غير مسددة بالكامل: ${cr.debtAgingReport.totalInvoices} فاتورة", 545f, y, textPaint)
            y += 20f
            canvas.drawText("مستحق اليوم: ${FormatUtils.formatAmount(cr.debtAgingReport.currentDue)}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("متأخر أكثر من 60 يوم: ${FormatUtils.formatAmount(cr.debtAgingReport.overdue60Plus)}", 545f, y, textPaint)
            y += 30f
            
            // Separator
            canvas.drawLine(50f, y, 545f, y, linePaint)
            y += 30f
        }

        // Inventory is not currency specific, print once at the end
        if (reportData.currencyReports.isNotEmpty()) {
            val inv = reportData.currencyReports.first().inventoryReport
            checkNewPage(150f)
            canvas.drawText("ملخص المخزون:", 545f, y, headerPaint)
            y += 20f
            canvas.drawText("إجمالي الأصناف: ${inv.totalItemsCount}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("الأصناف النافدة: ${inv.outOfStockItemsCount}", 545f, y, textPaint)
            y += 20f
            canvas.drawText("إجمالي القطع المتوفرة: ${inv.totalQuantity}", 545f, y, textPaint)
        }

        pdfDocument.finishPage(page)

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "تقارير ${settings.storeName}")
            if (!appDir.exists()) appDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(appDir, "تقرير_مالي_$timestamp.pdf")
            pdfDocument.writeTo(FileOutputStream(file))
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }
}
