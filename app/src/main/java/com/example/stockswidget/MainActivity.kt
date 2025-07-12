
package com.example.stockswidget

import android.content.Context // Added import for SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete // Added for portfolio delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext // Added import for SharedPreferences
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.stockswidget.data.VusaTransaction
import com.example.stockswidget.data.VusaTransactionDao
import com.example.stockswidget.data.VusaViewModel
import com.example.stockswidget.data.VusaViewModelFactory
import com.example.stockswidget.ui.theme.StocksWidgetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class VusaData(
    val closePrice: String = "Loading...",
    val rawClosePrice: Double = 0.0,
    val lastUpdateTime: String = "Loading..."
)

fun formatDynamicDecimal(value: Double): String {
    if (value.isNaN() || value.isInfinite()) {
        return value.toString() // Or a placeholder like "N/A"
    }
    // Check if it's a whole number
    if (value % 1.0 == 0.0) {
        return value.toLong().toString()
    }
    // For numbers with decimals, use BigDecimal to strip trailing zeros accurately
    return BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
}

// Helper function to format currency based on the symbol (dynamic decimals)
fun formatCurrency(amount: Double, currencySymbol: String): String {
    val formattedNumber = formatDynamicDecimal(amount)
    return when (currencySymbol) {
        "€" -> "€ $formattedNumber"
        "$" -> "$ $formattedNumber"
        "Other" -> formattedNumber // No symbol for "Other"
        else -> formattedNumber // Default, no symbol
    }
}

// Helper function to format currency with fixed two decimal places
fun formatCurrencyFixed(amount: Double, currencySymbol: String): String {
    val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val formattedNumber = numberFormat.format(amount)
    return when (currencySymbol) {
        "€" -> "€ $formattedNumber"
        "$" -> "$ $formattedNumber"
        "Other" -> formattedNumber // No symbol for "Other"
        else -> formattedNumber // Default, no symbol
    }
}


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val application = application as StocksWidgetApplication
        val vusaTransactionDao = application.vusaTransactionDao
        val viewModelFactory = VusaViewModelFactory(vusaTransactionDao)
        val vusaViewModel = ViewModelProvider(this, viewModelFactory)[VusaViewModel::class.java]

        setContent {
            StocksWidgetTheme {
                var showAddPortfolioDialog by remember { mutableStateOf(false) }
                var newPortfolioTitle by remember { mutableStateOf("") }

                // State for portfolio deletion confirmation
                var showDeletePortfolioConfirmDialog by remember { mutableStateOf(false) }
                var portfolioTitleToDelete by remember { mutableStateOf<String?>(null) }

                // SharedPreferences logic for customPortfolios
                val context = LocalContext.current
                val sharedPreferences = remember { context.getSharedPreferences("portfolio_prefs", Context.MODE_PRIVATE) }
                val savedPortfolios = remember {
                    sharedPreferences.getStringSet("custom_portfolio_titles", emptySet())?.toList() ?: emptyList()
                }
                val customPortfolios = remember { mutableStateListOf<String>().apply { addAll(savedPortfolios) } }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Stock Portfolio and Widgets") }
                        )
                    },
                    bottomBar = {
                        BottomAppBar {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Button(
                                    onClick = { showAddPortfolioDialog = true },
                                    modifier = Modifier.fillMaxWidth() // Changed to fillMaxWidth
                                ) {
                                    Text("Add New Portfolio")
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    var vusaData by remember { mutableStateOf<VusaData?>(null) }
                    var isLoading by remember { mutableStateOf(false) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    val coroutineScope = rememberCoroutineScope()
                    val snackbarHostState = remember { SnackbarHostState() }

                    fun fetchVusaData() {
                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                vusaData = fetchVusaPriceData()
                            } catch (e: Exception) {
                                errorMessage = "Error: ${e.message}"
                                vusaData = VusaData("N/A", 0.0, "N/A")
                            } finally {
                                isLoading = false
                            }
                        }
                    }

                    VusaScreen(
                        modifier = Modifier.padding(innerPadding),
                        vusaViewModel = vusaViewModel,
                        vusaData = vusaData,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRefresh = { fetchVusaData() },
                        snackbarHostState = snackbarHostState,
                        coroutineScope = coroutineScope,
                        customPortfolios = customPortfolios,
                        onDeletePortfolioRequest = { portfolioTitle ->
                            portfolioTitleToDelete = portfolioTitle
                            showDeletePortfolioConfirmDialog = true
                        },
                        onPortfolioClick = { portfolioTitle -> // New lambda
                            val intent = PortfolioDetailActivity.newIntent(context, portfolioTitle)
                            context.startActivity(intent)
                        }
                    )
                    LaunchedEffect(Unit) {
                        if (vusaData == null && !isLoading) {
                            fetchVusaData()
                        }
                    }

                    if (showAddPortfolioDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showAddPortfolioDialog = false
                                newPortfolioTitle = ""
                            },
                            title = { Text("Add New Portfolio") },
                            text = {
                                OutlinedTextField(
                                    value = newPortfolioTitle,
                                    onValueChange = { newPortfolioTitle = it },
                                    label = { Text("Portfolio Title") },
                                    singleLine = true, shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (newPortfolioTitle.isNotBlank()) {
                                            val portfolioTitle = newPortfolioTitle.trim()
                                            customPortfolios.add(portfolioTitle)
                                            // Save to SharedPreferences
                                            with(sharedPreferences.edit()) {
                                                putStringSet("custom_portfolio_titles", customPortfolios.toSet())
                                                apply()
                                            }
                                            
                                            // Reset title and dismiss dialog BEFORE starting activity
                                            newPortfolioTitle = ""
                                            showAddPortfolioDialog = false

                                            // Launch PortfolioDetailActivity
                                            val intent = PortfolioDetailActivity.newIntent(context, portfolioTitle)
                                            context.startActivity(intent)
                                        }
                                    },
                                    enabled = newPortfolioTitle.isNotBlank()
                                ) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showAddPortfolioDialog = false
                                    newPortfolioTitle = ""
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Dialog for confirming portfolio deletion
                    if (showDeletePortfolioConfirmDialog && portfolioTitleToDelete != null) {
                        AlertDialog(
                            onDismissRequest = {
                                showDeletePortfolioConfirmDialog = false
                                portfolioTitleToDelete = null
                            },
                            title = { Text("Confirm Deletion") },
                            text = { Text("Are you sure you want to delete the portfolio \"$portfolioTitleToDelete\"?" ) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        portfolioTitleToDelete?.let { title ->
                                            customPortfolios.remove(title)
                                            // Update SharedPreferences
                                            with(sharedPreferences.edit()) {
                                                putStringSet("custom_portfolio_titles", customPortfolios.toSet())
                                                apply()
                                            }
                                        }
                                        showDeletePortfolioConfirmDialog = false
                                        portfolioTitleToDelete = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDeletePortfolioConfirmDialog = false
                                    portfolioTitleToDelete = null
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

suspend fun fetchVusaPriceData(): VusaData {
    return withContext(Dispatchers.IO) {
        val urlString = "https://scanner.tradingview.com/symbol?symbol=EURONEXT%3AVUSA&fields=close%2Clast_bar_update_time"
        val url = URL(urlString)
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonObject = JSONObject(response.toString())
                val closePrice = jsonObject.optDouble("close", Double.NaN)
                val lastBarUpdateTime = jsonObject.optLong("last_bar_update_time", -1L)

                val formattedPriceDisplay = if (closePrice.isNaN()) "N/A" else formatCurrencyFixed(closePrice, "€")
                val rawPrice = if (closePrice.isNaN()) 0.0 else closePrice

                val formattedTime = if (lastBarUpdateTime == -1L) {
                    "N/A"
                } else {
                    try {
                        val date = Date(lastBarUpdateTime * 1000L)
                        val sdf = SimpleDateFormat("h:mm a", Locale.US)
                        sdf.timeZone = TimeZone.getDefault()
                        sdf.format(date)
                    } catch (e: Exception) {
                        "Time Format Error"
                    }
                }
                VusaData(closePrice = formattedPriceDisplay, rawClosePrice = rawPrice, lastUpdateTime = formattedTime)
            } else {
                throw Exception("HTTP error code: $responseCode")
            }
        } finally {
            connection?.disconnect()
        }
    }
}

@Composable
fun Portfolio(
    transactions: List<VusaTransaction>,
    currentMarketPrice: Double?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddFirstTransactionClick: () -> Unit
) {
    if (transactions.isNotEmpty() && currentMarketPrice != null && currentMarketPrice != 0.0) {
        val totalPortfolioValue = transactions.sumOf { it.amount * currentMarketPrice }
        val totalCost = transactions.sumOf { it.amount * it.buyPrice }
        val totalProfitLoss = totalPortfolioValue - totalCost
        val currencySymbol = transactions.first().currency

        val profitLossColor = when {
            totalProfitLoss > 0 -> Color(0xFF4CAF50) // Green
            totalProfitLoss < 0 -> Color(0xFFF44336) // Red
            else -> Color.White
        }
        val (icon, iconColor) = when {
            totalProfitLoss > 0 -> Icons.Filled.ArrowUpward to profitLossColor
            totalProfitLoss < 0 -> Icons.Filled.ArrowDownward to profitLossColor
            else -> null to profitLossColor
        }

        val percentageString = when {
            totalCost != 0.0 -> {
                val percentage = (totalProfitLoss / totalCost) * 100.0
                String.format(Locale.US, "(%+.2f%%)", percentage)
            }
            totalProfitLoss > 0 -> "(+∞%)"
            totalProfitLoss == 0.0 -> "(0.00%)"
            else -> "(-%)"
        }

        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AMS:VUSA\nPortfolio",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrencyFixed(totalPortfolioValue, currencySymbol),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            icon?.let {
                                Icon(
                                    imageVector = it,
                                    contentDescription = if (totalProfitLoss > 0) "Profit" else "Loss",
                                    tint = iconColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = formatCurrencyFixed(totalProfitLoss, currencySymbol),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = profitLossColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = percentageString,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = profitLossColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color.White
                    )
                }
            }
            Divider()
        }
    } else if (transactions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No transactions yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onAddFirstTransactionClick) {
                Text("Add First Transaction")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VusaScreen(
    modifier: Modifier = Modifier,
    vusaViewModel: VusaViewModel,
    vusaData: VusaData?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    customPortfolios: List<String>,
    onDeletePortfolioRequest: (portfolioTitle: String) -> Unit, // New parameter for delete
    onPortfolioClick: (String) -> Unit // Add this line
) {
    var amountInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var selectedBuyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var selectedCurrency by remember { mutableStateOf("€") }

    var calculatedTotal by remember { mutableStateOf<String?>(null) }

    val transactions by vusaViewModel.allTransactions.collectAsState(initial = emptyList())

    var showDeleteTransactionConfirmDialog by remember { mutableStateOf<VusaTransaction?>(null) } // For VusaTransaction deletion
    var transactionToEdit by remember { mutableStateOf<VusaTransaction?>(null) }

    var isTransactionsExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(transactions) {
        Log.d("VusaScreen", "Transactions list updated. Size: ${transactions.size}")
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { innerPaddingFromVusaScaffold ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPaddingFromVusaScaffold)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onRefresh) { Text("Retry") }
            } else if (vusaData != null) {
                customPortfolios.forEach { portfolioTitle ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = portfolioTitle,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier
                                .weight(1f) // Allow text to take available space
                                .padding(end = 8.dp) // Padding before delete icon
                                .clickable { onPortfolioClick(portfolioTitle) } // Make text clickable
                        )
                        IconButton(onClick = { onDeletePortfolioRequest(portfolioTitle) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete Portfolio $portfolioTitle",
                                tint = Color.LightGray // Or MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text("VUSA Data", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Close Price: ${vusaData.closePrice}, Last Update: ${vusaData.lastUpdateTime}", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Portfolio(
                    transactions = transactions,
                    currentMarketPrice = vusaData?.rawClosePrice,
                    isExpanded = isTransactionsExpanded,
                    onToggleExpand = { isTransactionsExpanded = !isTransactionsExpanded },
                    onAddFirstTransactionClick = { isTransactionsExpanded = true }
                )
                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(visible = isTransactionsExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { newText ->
                                    val filtered = newText.foldIndexed("") { _, acc, char ->
                                        if (char.isDigit()) acc + char
                                        else if ((char == '.' || char == ',') && !acc.contains('.')) acc + '.'
                                        else acc
                                    }
                                    val parts = filtered.split('.', limit = 2)
                                    val integerPart = parts[0]
                                    val fractionalPart = if (parts.size > 1) parts[1].take(4) else null
                                    amountInput = when {
                                        fractionalPart != null -> if (integerPart.isEmpty()) "0.$fractionalPart" else "$integerPart.$fractionalPart"
                                        integerPart.isEmpty() && filtered.contains('.') -> "0."
                                        else -> integerPart
                                    }
                                },
                                label = { Text("Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = if (amountInput.isNotEmpty()) {
                                    {
                                        IconButton(
                                            onClick = { amountInput = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Clear,
                                                "Clear",
                                                Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                } else null
                            )
                            OutlinedTextField(
                                value = priceInput,
                                onValueChange = { newText ->
                                    val filtered = newText.foldIndexed("") { _, acc, char ->
                                        if (char.isDigit()) acc + char
                                        else if ((char == '.' || char == ',') && !acc.contains('.')) acc + '.'
                                        else acc
                                    }
                                    val parts = filtered.split('.', limit = 2)
                                    val integerPart = parts[0]
                                    val fractionalPart = if (parts.size > 1) parts[1].take(4) else null
                                    priceInput = when {
                                        fractionalPart != null -> if (integerPart.isEmpty()) "0.$fractionalPart" else "$integerPart.$fractionalPart"
                                        integerPart.isEmpty() && filtered.contains('.') -> "0."
                                        else -> integerPart
                                    }
                                },
                                label = { Text("Price") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true, shape = MaterialTheme.shapes.large, modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = if (priceInput.isNotEmpty()) {
                                    {
                                        IconButton(
                                            onClick = { priceInput = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Clear,
                                                "Clear",
                                                Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                } else null
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ClickableTextField(
                                value = dateFormatter.format(Date(selectedBuyDateMillis)),
                                label = "Buy Date",
                                onClick = { showDatePickerDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Select Date", modifier = Modifier
                                    .size(18.dp)
                                    .offset(x = 4.dp)) }
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                    //.padding(top = 8.dp), // Removed as per prior request for alignment
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currencies = listOf("€", "$", "Other")
                                currencies.forEach { currency ->
                                    val isSelected = selectedCurrency == currency
                                    OutlinedButton(
                                        onClick = { selectedCurrency = currency },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        contentPadding = PaddingValues(all = 8.dp),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF666666) else Color(0xFF555555)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) Color(0xFF444444) else Color(0xFF222222),
                                            contentColor = Color.White // Default content color
                                        ),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text(
                                            text = currency,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White // Explicitly white text
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val amount = amountInput.toDoubleOrNull()
                                val buyPrice = priceInput.toDoubleOrNull()
                                if (amount != null && buyPrice != null) {
                                    val calendar = Calendar.getInstance()
                                    calendar.timeInMillis = selectedBuyDateMillis
                                    calendar.set(Calendar.HOUR_OF_DAY, 12)
                                    calendar.set(Calendar.MINUTE, 0)
                                    calendar.set(Calendar.SECOND, 0)
                                    calendar.set(Calendar.MILLISECOND, 0)
                                    val adjustedTimestamp = calendar.timeInMillis

                                    vusaViewModel.insertTransaction(
                                        amount = amount,
                                        buyPrice = buyPrice,
                                        transactionTimestamp = adjustedTimestamp,
                                        currency = selectedCurrency
                                    )
                                    amountInput = ""; priceInput = ""
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Transaction Saved!",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    calculatedTotal = null
                                } else {
                                    calculatedTotal = "Invalid input. Please check amount and price."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = amountInput.isNotBlank() && priceInput.isNotBlank() && !isLoading,
                            shape = MaterialTheme.shapes.large
                        ) { Text("Save") }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (calculatedTotal != null) {
                            Text(calculatedTotal!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (transactions.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f) // Ensure LazyColumn takes remaining space if needed
                                    .fillMaxWidth()
                            ) {
                                items(transactions, key = { it.id }) { transaction ->
                                    TransactionItem(
                                        transaction = transaction,
                                        currentMarketPrice = vusaData?.rawClosePrice,
                                        onEditClick = { transactionToEdit = it },
                                    )
                                    Divider()
                                }
                            }
                        }
                    }
                }
                if (transactions.isEmpty() && !isTransactionsExpanded) {
                    // Content handled by Portfolio composable
                }
            } else {
                Text("Tap 'Refresh Data' to load market information.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    focusManager.clearFocus()
                    onRefresh()
                }) { Text("Refresh Data") }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedBuyDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedBuyDateMillis = it
                    }
                    showDatePickerDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    showDeleteTransactionConfirmDialog?.let { transactionToDelete -> // For VusaTransaction deletion
        DeleteConfirmationDialog(
            transaction = transactionToDelete,
            onConfirmDelete = {
                vusaViewModel.deleteTransactionById(transactionToDelete.id)
                showDeleteTransactionConfirmDialog = null
            },
            onDismiss = { showDeleteTransactionConfirmDialog = null }
        )
    }

    transactionToEdit?.let { currentTransactionToEdit ->
        EditTransactionDialog(
            transaction = currentTransactionToEdit,
            onSave = { updatedTransaction ->
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = updatedTransaction.transactionTimestamp
                calendar.set(Calendar.HOUR_OF_DAY, 12)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val adjustedTimestamp = calendar.timeInMillis

                vusaViewModel.updateTransaction(
                    updatedTransaction.copy(
                        currency = currentTransactionToEdit.currency, // Persist original currency
                        transactionTimestamp = adjustedTimestamp
                    )
                )
                transactionToEdit = null
            },
            onDeleteConfirm = { // This is called when delete is confirmed from Edit dialog
                showDeleteTransactionConfirmDialog = currentTransactionToEdit // Show the transaction delete dialog
                transactionToEdit = null // Close edit dialog
            },
            onDismiss = {
                transactionToEdit = null
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickableTextField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { /* Read-only */ },
            label = { Text(label) },
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        )
    }
}

@Composable
fun TransactionItem(
    transaction: VusaTransaction,
    currentMarketPrice: Double?,
    onEditClick: (VusaTransaction) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .fillMaxWidth()
            .clickable { onEditClick(transaction) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "AMS:VUSA",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                "Amount: ${formatDynamicDecimal(transaction.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
            Text(
                "Buy Price: ${formatCurrency(transaction.buyPrice, transaction.currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
            Text(
                "Buy Date: ${dateFormat.format(Date(transaction.transactionTimestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
        }

        currentMarketPrice?.let { marketPrice ->
            val totalBuyValue = transaction.amount * transaction.buyPrice
            val currentValue = transaction.amount * marketPrice
            val profitOrLoss = currentValue - totalBuyValue
            val profitLossText = formatCurrencyFixed(profitOrLoss, transaction.currency)

            val percentageString = if (totalBuyValue != 0.0) {
                val percentage = (profitOrLoss / totalBuyValue) * 100
                String.format(Locale.US, " (%+.2f%%)", percentage)
            } else if (profitOrLoss > 0) {
                 " (+∞%)"
            }
             else {
                ""
            }

            val profitLossColor = when {
                profitOrLoss > 0 -> Color(0xFF4CAF50)
                profitOrLoss < 0 -> Color(0xFFF44336)
                else -> MaterialTheme.colorScheme.onSurface
            }
            val (icon, iconColor) = when {
                profitOrLoss > 0 -> Icons.Filled.ArrowUpward to Color(0xFF4CAF50)
                profitOrLoss < 0 -> Icons.Filled.ArrowDownward to Color(0xFFF44336)
                else -> null to profitLossColor
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "${formatCurrencyFixed(currentValue, transaction.currency)}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = if (profitOrLoss > 0) "Profit" else "Loss",
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = when {
                            profitOrLoss > 0 -> "$profitLossText$percentageString"
                            profitOrLoss < 0 -> "$profitLossText$percentageString"
                            else -> "P/L: $profitLossText$percentageString"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = profitLossColor
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    transaction: VusaTransaction,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Deletion") },
        text = { Text("Are you sure you want to delete this transaction? Amount: ${formatDynamicDecimal(transaction.amount)} Price: ${formatCurrency(transaction.buyPrice, transaction.currency)}") },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: VusaTransaction,
    onSave: (VusaTransaction) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var editAmount by remember { mutableStateOf(formatDynamicDecimal(transaction.amount)) }
    var editPrice by remember(transaction.buyPrice) { mutableStateOf(formatDynamicDecimal(transaction.buyPrice)) }
    var editSelectedDateMillis by remember { mutableStateOf(transaction.transactionTimestamp) }
    var showEditDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") },
        text = {
            Column {
                OutlinedTextField(
                    value = editAmount,
                    onValueChange = { newValue ->
                        val filtered = newValue.foldIndexed("") { _, acc, char ->
                            if (char.isDigit()) acc + char
                            else if ((char == '.' || char == ',') && !acc.contains('.') && !acc.contains(',')) acc + '.'
                            else acc
                        }
                        val parts = filtered.split('.', limit = 2)
                        val integerPart = parts[0]
                        val fractionalPart = if (parts.size > 1) parts[1].take(4) else null
                        editAmount = when {
                            fractionalPart != null -> if (integerPart.isEmpty()) "0.$fractionalPart" else "$integerPart.$fractionalPart"
                            integerPart.isEmpty() && filtered.contains('.') -> "0."
                            else -> integerPart
                        }
                    },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editPrice,
                    onValueChange = { newValue ->
                        val filtered = newValue.foldIndexed("") { _, acc, char ->
                            if (char.isDigit()) acc + char
                            else if ((char == '.' || char == ',') && !acc.contains('.') && !acc.contains(',')) acc + '.'
                            else acc
                        }
                        val parts = filtered.split('.', limit = 2)
                        val integerPart = parts[0]
                        val fractionalPart = if (parts.size > 1) parts[1].take(4) else null
                        editPrice = when {
                            fractionalPart != null -> if (integerPart.isEmpty()) "0.$fractionalPart" else "$integerPart.$fractionalPart"
                            integerPart.isEmpty() && filtered.contains('.') -> "0."
                            else -> integerPart
                        }
                    },
                    label = { Text("Price (${transaction.currency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                ClickableTextField(
                    value = dateFormatter.format(Date(editSelectedDateMillis)),
                    label = "Transaction Date",
                    onClick = { showEditDatePickerDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Select Date", modifier = Modifier
                        .size(18.dp)
                        .offset(x = 4.dp)) }
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(
                    onClick = onDeleteConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
                Button(onClick = {
                    val newAmount = editAmount.toDoubleOrNull()
                    val newPrice = editPrice.toDoubleOrNull()

                    if (newAmount != null && newPrice != null) {
                        onSave(transaction.copy(
                            amount = newAmount,
                            buyPrice = newPrice,
                            transactionTimestamp = editSelectedDateMillis
                        ))
                    }
                }) { Text("Save") }
            }
        },
        dismissButton = null // No separate dismiss for this condensed layout
    )

    if (showEditDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = editSelectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEditDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        editSelectedDateMillis = it
                    }
                    showEditDatePickerDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDatePickerDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// --- Preview and Fake DAO ---
class FakeVusaTransactionDao : VusaTransactionDao {
    private val _transactions = mutableListOf<VusaTransaction>()
    private val transactionsFlow = MutableStateFlow<List<VusaTransaction>>(emptyList())
    private var nextId = 1

    init {
        val sampleData = listOf(
            VusaTransaction(id=nextId++, amount = 10.0, buyPrice = 80.50, transactionTimestamp = System.currentTimeMillis() - 200000, currency = "€"),
            VusaTransaction(id=nextId++, amount = 5.0, buyPrice = 82.30, transactionTimestamp = System.currentTimeMillis() - 100000, currency = "$"),
            VusaTransaction(id=nextId++, amount = 12.5, buyPrice = 1500.75, transactionTimestamp = System.currentTimeMillis() - 50000, currency = "Other")
        )
        _transactions.addAll(sampleData)
        transactionsFlow.value = _transactions.toList().sortedByDescending { it.transactionTimestamp }
    }

    override suspend fun insertTransaction(transaction: VusaTransaction) {
        val newTransaction = if (transaction.id == 0) transaction.copy(id = nextId++) else transaction
        _transactions.add(0, newTransaction)
        _transactions.sortByDescending { it.transactionTimestamp }
        transactionsFlow.value = _transactions.toList()
    }

    override suspend fun updateTransaction(transaction: VusaTransaction) {
        val index = _transactions.indexOfFirst { it.id == transaction.id }
        if (index != -1) {
            _transactions[index] = transaction
            transactionsFlow.value = _transactions.toList().sortedByDescending { it.transactionTimestamp }
        }
    }
    override fun getAllTransactions(): Flow<List<VusaTransaction>> = transactionsFlow
    override suspend fun getTransactionById(id: Int): VusaTransaction? = _transactions.find { it.id == id }
    override suspend fun deleteTransactionById(id: Int) {
        _transactions.removeAll { it.id == id }
        transactionsFlow.value = _transactions.toList()
    }
}

fun getPreviewVusaViewModel(): VusaViewModel {
    return VusaViewModel(FakeVusaTransactionDao())
}

@Preview(showBackground = true, name = "Vusa Screen - Data & List")
@Composable
fun MainScreenPreview() {
    StocksWidgetTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()
        VusaScreen(
            vusaViewModel = getPreviewVusaViewModel(),
            vusaData = VusaData("€85.50", 85.50, "10:30 AM"),
            isLoading = false,
            errorMessage = null,
            onRefresh = {},
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope,
            customPortfolios = listOf("My Tech Portfolio", "Retirement Fund"),
            onDeletePortfolioRequest = {}, // Added for preview
            onPortfolioClick = {} // Added for preview
        )
    }
}


@Preview(showBackground = true, name = "Vusa Screen - Edit Dialog")
@Composable
fun VusaScreenPreviewEditDialog() {
    StocksWidgetTheme {
        val sampleTransaction = remember { VusaTransaction(id = 1, amount = 10.0, buyPrice = 80.5, transactionTimestamp = System.currentTimeMillis(), currency = "€") }
        EditTransactionDialog(transaction = sampleTransaction, onSave = {}, onDeleteConfirm = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, name = "Vusa Screen - Delete Dialog")
@Composable
fun VusaScreenPreviewDeleteDialog() {
    StocksWidgetTheme {
        val sampleTransaction = remember { VusaTransaction(id = 1, amount = 10.0, buyPrice = 80.5, transactionTimestamp = System.currentTimeMillis(), currency = "€") }
        DeleteConfirmationDialog(transaction = sampleTransaction, onConfirmDelete = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, name = "Transaction Item Preview - Euro")
@Composable
fun TransactionItemPreviewEuro() {
    StocksWidgetTheme {
        val transaction = VusaTransaction(id = 1, amount = 10.0, buyPrice = 80.50, currency = "€", transactionTimestamp = System.currentTimeMillis())
        TransactionItem(transaction = transaction, currentMarketPrice = 85.50, onEditClick = {})
    }
}

@Preview(showBackground = true, name = "Transaction Item Preview - Dollar Profit")
@Composable
fun TransactionItemPreviewDollarProfit() {
    StocksWidgetTheme {
        val transaction = VusaTransaction(id = 1, amount = 5.0, buyPrice = 100.0, currency = "$", transactionTimestamp = System.currentTimeMillis())
        TransactionItem(transaction = transaction, currentMarketPrice = 110.0, onEditClick = {})
    }
}

@Preview(showBackground = true, name = "Transaction Item Preview - Other Loss")
@Composable
fun TransactionItemPreviewOtherLoss() {
    StocksWidgetTheme {
        val transaction = VusaTransaction(id = 1, amount = 20.0, buyPrice = 50.0, currency = "Other", transactionTimestamp = System.currentTimeMillis())
        TransactionItem(transaction = transaction, currentMarketPrice = 45.0, onEditClick = {})
    }
}

