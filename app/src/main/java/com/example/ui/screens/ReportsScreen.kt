package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.report.CurrencyReport
import com.example.domain.report.DateFilterType
import com.example.domain.report.ReportData
import com.example.domain.report.DateRange
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    reportViewModel: com.example.ui.viewmodel.ReportViewModel,
    onNavigateToTopMovingItems: () -> Unit = {}
) {
    val reportData by reportViewModel.reportData.collectAsState()
    val selectedCurrency by reportViewModel.selectedReportCurrency.collectAsState()
    val selectedDateFilter by reportViewModel.selectedDateFilter.collectAsState()
    val customDateRange by reportViewModel.customDateRange.collectAsState()
    val settings by reportViewModel.storeSettings.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // UI States
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    var showDateDropdown by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    val tabs = listOf("🛒 المبيعات والتحصيل", "📦 المشتريات", "👥 العملاء والمخزون", "📈 تحليل مالي")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 التقارير", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (isGeneratingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            if (reportData != null) {
                                isGeneratingPdf = true
                                coroutineScope.launch {
                                    try {
                                        val file = com.example.util.ReportPdfManager.exportFinancialReportToPdf(
                                            context, reportData!!, settings ?: com.example.data.model.StoreSettings()
                                        )
                                        if (file != null) {
                                            com.example.util.ShareManager.sharePdf(context, file)
                                        } else {
                                            Toast.makeText(context, "تعذر إنشاء التقرير حاليًا. يرجى المحاولة مرة أخرى.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "حدث خطأ أثناء إنشاء التقرير.", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isGeneratingPdf = false
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filters Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date Filter
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showDateDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedDateFilter.label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(
                                expanded = showDateDropdown,
                                onDismissRequest = { showDateDropdown = false }
                            ) {
                                DateFilterType.values().forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.label, fontSize = 13.sp) },
                                        onClick = {
                                            showDateDropdown = false
                                            if (filter == DateFilterType.CUSTOM) {
                                                showCustomDateDialog = true
                                            } else {
                                                reportViewModel.setReportDateFilter(filter)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Currency Filter
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showCurrencyDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedCurrency,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(
                                expanded = showCurrencyDropdown,
                                onDismissRequest = { showCurrencyDropdown = false }
                            ) {
                                val availableCurrencies = listOf("كل العملات", "الريال اليمني", "الريال السعودي", "الدولار الأمريكي")
                                availableCurrencies.forEach { cur ->
                                    DropdownMenuItem(
                                        text = { Text(cur, fontSize = 13.sp) },
                                        onClick = {
                                            showCurrencyDropdown = false
                                            reportViewModel.setReportCurrency(cur)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedDateFilter == DateFilterType.CUSTOM) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        Text(
                            text = "من: ${sdf.format(Date(customDateRange.start))} إلى: ${sdf.format(Date(customDateRange.end))}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // Quick access banner for Most Active Items Report
            Surface(
                onClick = onNavigateToTopMovingItems,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEA580C).copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔥", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "تحليل الأصناف الأكثر حركة وسحب العملاء",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C)
                            )
                            Text(
                                text = "معرفة الأصناف الأكثر مبيعاً ومن يسحب كل صنف بالتفصيل",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = onNavigateToTopMovingItems,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("عرض", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }

            // Content
            if (reportData == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (reportData!!.currencyReports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("📊 لا توجد بيانات", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("لا توجد معاملات ضمن الفترة المحددة.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(reportData!!.currencyReports) { currencyReport ->
                        CurrencyReportSection(currencyReport = currencyReport, selectedTab = selectedTab)
                    }
                }
            }
        }
    }

    if (showCustomDateDialog) {
        // Implement custom date dialog
        var startMillis by remember { mutableLongStateOf(customDateRange.start) }
        var endMillis by remember { mutableLongStateOf(customDateRange.end) }
        
        // A simple alert dialog with fake date pickers for now
        // In a real app, use DatePickerDialog
        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            title = { Text("تحديد فترة مخصصة") },
            text = {
                Text("يرجى تحديد تاريخ البداية والنهاية من التطبيق.") // Simplified for architecture integrity
            },
            confirmButton = {
                TextButton(onClick = {
                    showCustomDateDialog = false
                    reportViewModel.setReportCustomDateRange(startMillis, endMillis)
                }) {
                    Text("تأكيد", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("إلغاء", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

@Composable
fun CurrencyReportSection(currencyReport: CurrencyReport, selectedTab: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Currency Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "العملة: ${currencyReport.currency}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 16.sp
                )
            }

            when (selectedTab) {
                0 -> { // Sales & Collection
                    SalesSummarySection(currencyReport)
                    CollectionSection(currencyReport)
                    ReturnsSection(currencyReport)
                }
                1 -> { // Purchases
                    PurchasesSection(currencyReport)
                }
                2 -> { // Customers & Inventory
                    CustomerSection(currencyReport)
                    DebtAgingSection(currencyReport)
                    InventorySection(currencyReport)
                }
                3 -> { // Financial Analysis
                    FinancialAnalysisSection(currencyReport)
                }
            }
        }
    }
}

@Composable
fun SalesSummarySection(report: CurrencyReport) {
    ReportBlock(title = "🛒 ملخص المبيعات", icon = Icons.Default.ShoppingCart) {
        ReportRow("إجمالي المبيعات", report.salesReport.totalSales, report.currency, isBold = true)
        ReportRow("مرتجعات المبيعات", report.salesReport.totalReturns, report.currency, color = Color.Red)
        ReportRow("صافي المبيعات", report.salesReport.netSales, report.currency, isBold = true, color = Color(0xFF059669))
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("عدد فواتير البيع", "${report.salesReport.invoiceCount} فاتورة")
        ReportRow("متوسط قيمة الفاتورة", report.salesReport.averageInvoiceValue, report.currency)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("الفواتير المدفوعة", "${report.salesReport.fullyPaidCount}")
        ReportRowText("الفواتير الجزئية", "${report.salesReport.partiallyPaidCount}")
        ReportRowText("الفواتير غير المسددة", "${report.salesReport.unpaidCount}", color = Color.Red)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("أعلى عميل شراءً", report.salesReport.topCustomerName)
        ReportRow("قيمة مشترياته", report.salesReport.topCustomerAmount, report.currency)
        ReportRowText("أكثر صنف مبيعاً", report.salesReport.topItemName)
    }
}

@Composable
fun CollectionSection(report: CurrencyReport) {
    ReportBlock(title = "📥 تقرير التحصيل", icon = Icons.Default.Payments) {
        ReportRow("إجمالي المستحقات (فواتير)", report.collectionReport.totalDue, report.currency)
        ReportRow("إجمالي المقبوضات", report.collectionReport.totalCollected, report.currency, color = Color(0xFF059669), isBold = true)
        ReportRow("إجمالي المتبقي", report.collectionReport.totalRemaining, report.currency, color = Color.Red, isBold = true)
        
        val ratioColor = if (report.collectionReport.collectionRatio >= 80) Color(0xFF059669) 
                         else if (report.collectionReport.collectionRatio >= 50) Color(0xFFD97706) 
                         else Color.Red
        ReportRowText("نسبة التحصيل للفترة", "${FormatUtils.formatAmount(report.collectionReport.collectionRatio)}%", color = ratioColor, isBold = true)
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRow("الأقساط المستحقة القادمة", report.collectionReport.dueInstallments, report.currency)
        ReportRow("الأقساط المتأخرة السداد", report.collectionReport.overdueInstallments, report.currency, color = Color.Red)
    }
}

@Composable
fun PurchasesSection(report: CurrencyReport) {
    ReportBlock(title = "📦 تقرير المشتريات والموردين", icon = Icons.Default.Storefront) {
        Text(
            "ملاحظة: نظام إدارة المشتريات التفصيلي وحسابات الموردين غير مدعوم في هذه النسخة حالياً.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ReportRow("إجمالي المشتريات", report.purchaseReport.totalPurchases, report.currency)
        ReportRow("إجمالي المدفوع للموردين", report.purchaseReport.totalPayments, report.currency)
        ReportRow("إجمالي المتبقي للموردين", report.purchaseReport.totalRemaining, report.currency)
        ReportRowText("عدد الموردين", "${report.supplierReport.supplierCount}")
    }
}

@Composable
fun ReturnsSection(report: CurrencyReport) {
    ReportBlock(title = "↩️ تقرير المرتجعات", icon = Icons.Default.AssignmentReturn) {
        ReportRow("مرتجعات المبيعات", report.returnsReport.totalSalesReturns, report.currency)
        ReportRow("مرتجعات المشتريات", report.returnsReport.totalPurchaseReturns, report.currency)
    }
}

@Composable
fun CustomerSection(report: CurrencyReport) {
    ReportBlock(title = "👥 تقرير حركة العملاء", icon = Icons.Default.People) {
        ReportRowText("إجمالي العملاء المتفاعلين", "${report.customerReport.activeCustomersCount}")
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text("حالة ديون العملاء الحالية (كل الأوقات):", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        ReportRowText("العملاء المدينون (عليهم)", "${report.customerReport.debtorsCount}", color = Color.Red)
        ReportRowText("العملاء الدائنون (لهم)", "${report.customerReport.creditorsCount}", color = Color(0xFF059669))
        ReportRowText("العملاء المتعادلون", "${report.customerReport.neutralCount}")
    }
}

@Composable
fun InventorySection(report: CurrencyReport) {
    ReportBlock(title = "📦 تقرير المخزون", icon = Icons.Default.Inventory) {
        ReportRowText("إجمالي الأصناف المسجلة", "${report.inventoryReport.totalItemsCount}")
        ReportRowText("الأصناف المتوفرة", "${report.inventoryReport.availableItemsCount}", color = Color(0xFF059669))
        ReportRowText("الأصناف منخفضة المخزون", "${report.inventoryReport.lowStockItemsCount}", color = Color(0xFFD97706))
        ReportRowText("الأصناف النافدة", "${report.inventoryReport.outOfStockItemsCount}", color = Color.Red)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("إجمالي عدد القطع", "${report.inventoryReport.totalQuantity} قطعة")
        ReportRowText("القطع المباعة خلال الفترة", "${report.inventoryReport.soldQuantityPeriod} قطعة")
    }
}

@Composable
fun DebtAgingSection(report: CurrencyReport) {
    ReportBlock(title = "💳 تحليل أعمار الديون (مستحقات غير مسددة)", icon = Icons.Default.AccountBalanceWallet) {
        ReportRowText("العملاء ذوي ديون غير مسددة", "${report.debtAgingReport.totalCustomers}")
        ReportRowText("فواتير غير مسددة بالكامل", "${report.debtAgingReport.totalInvoices}")
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRow("مستحق اليوم", report.debtAgingReport.currentDue, report.currency, color = Color(0xFFD97706))
        ReportRow("متأخر 1-7 أيام", report.debtAgingReport.overdue1_7, report.currency, color = Color.Red)
        ReportRow("متأخر 8-30 يومًا", report.debtAgingReport.overdue8_30, report.currency, color = Color.Red)
        ReportRow("متأخر 31-60 يومًا", report.debtAgingReport.overdue31_60, report.currency, color = Color.Red)
        ReportRow("متأخر أكثر من 60 يومًا", report.debtAgingReport.overdue60Plus, report.currency, color = Color.Red, isBold = true)
    }
}

@Composable
fun FinancialAnalysisSection(report: CurrencyReport) {
    ReportBlock(title = "📈 التحليل المالي", icon = Icons.Default.TrendingUp) {
        ReportRow("صافي المبيعات", report.financialAnalysis.netSales, report.currency, color = Color(0xFF059669), isBold = true)
        ReportRow("إجمالي المقبوضات (تدفق نقدي)", report.financialAnalysis.cashFlow, report.currency, color = Color(0xFF059669))
        ReportRow("الذمم المدينة (أموال بالخارج)", report.financialAnalysis.accountsReceivable, report.currency, color = Color.Red, isBold = true)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "📊 هامش/مؤشر مالي متاح:",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Text(
            "النظام الحالي لا يمتلك بيانات تكلفة شاملة وتسجيل مصروفات لحساب صافي الربح الدقيق. الأرقام تعكس حركة الأموال المسجلة فقط.",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ReportBlock(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            content()
        }
    }
}

@Composable
fun ReportRow(label: String, amount: Double, currency: String, isBold: Boolean = false, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = if (color == Color.Unspecified) Color.DarkGray else color, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(
            "${FormatUtils.formatAmount(amount)} $currency",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
    }
}

@Composable
fun ReportRowText(label: String, value: String, isBold: Boolean = false, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = if (color == Color.Unspecified) Color.DarkGray else color, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
    }
}
