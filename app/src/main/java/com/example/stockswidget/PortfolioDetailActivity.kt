
package com.example.stockswidget

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.stockswidget.data.VusaTransaction
import com.example.stockswidget.data.VusaTransactionDao
import com.example.stockswidget.data.VusaViewModel
import com.example.stockswidget.data.VusaViewModelFactory
import com.example.stockswidget.ui.theme.StocksWidgetTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.input.pointer.pointerInput

// Helper function to format currency (dynamic decimals)
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


// Function to fetch search results using HttpURLConnection
suspend fun fetchSearchResultsFromApi(urlString: String): Map<String, Any?> {
    return withContext(Dispatchers.IO) {
        val url = try {
            URL(urlString)
        } catch (e: java.net.MalformedURLException) {
            return@withContext mapOf("error" to "Invalid URL: ${'$'}{e.message}")
        }
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000 // 15 seconds
            connection.readTimeout = 15000  // 15 seconds
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
                mapOf("content" to response.toString())
            } else {
                mapOf("error" to "HTTP error code: $responseCode - ${'$'}{connection.responseMessage}")
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown network error during search API call"))
        } finally {
            connection?.disconnect()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class PortfolioDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PORTFOLIO_TITLE = "extra_portfolio_title"

        fun newIntent(context: Context, portfolioTitle: String): Intent {
            return Intent(context, PortfolioDetailActivity::class.java).apply {
                putExtra(EXTRA_PORTFOLIO_TITLE, portfolioTitle)
            }
        }
    }

    private lateinit var vusaViewModel: VusaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val portfolioTitleFromIntent = intent.getStringExtra(EXTRA_PORTFOLIO_TITLE) ?: "Portfolio Details"

        val application = application as StocksWidgetApplication
        val vusaTransactionDao = application.vusaTransactionDao
        val viewModelFactory = VusaViewModelFactory(vusaTransactionDao)
        vusaViewModel = ViewModelProvider(this, viewModelFactory)[VusaViewModel::class.java]

        setContent {
            StocksWidgetTheme {
                var showSearchStockSheet by remember { mutableStateOf(false) }
                var showSecondSheet by remember { mutableStateOf(false) }
                val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val secondSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) // Consider true for full screen behavior

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(portfolioTitleFromIntent) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    finish()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            )
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
                                    onClick = { showSearchStockSheet = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Add New Stock") // Or "Search Stock"
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    PortfolioDetailScreenMainContent(
                        modifier = Modifier.padding(innerPadding),
                        portfolioTitle = portfolioTitleFromIntent
                    )
                }

                if (showSearchStockSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSearchStockSheet = false },
                        sheetState = searchSheetState,
                        containerColor = Color.Black
                    ) {
                        SearchBottomSheetContent(
                            onDismiss = { showSearchStockSheet = false },
                            onSearchResultClick = {
                                showSearchStockSheet = false
                                showSecondSheet = true
                                // Later: Pass search result data here
                            }
                        )
                    }
                }

                if (showSecondSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSecondSheet = false },
                        sheetState = secondSheetState,
                        modifier = Modifier.fillMaxSize(), // Makes the sheet itself full screen
                        containerColor = Color.Black
                    ) {
                        // The Box ensures the content within the modal can expand
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            AddTransactionSheetContent(
                                onDismiss = { showSecondSheet = false },
                                vusaViewModel = vusaViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioDetailScreenMainContent(modifier: Modifier = Modifier, portfolioTitle: String) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Details for '$portfolioTitle'",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White // Assuming black background from theme
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheetContent(onDismiss: () -> Unit, onSearchResultClick: () -> Unit) {
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var searchResultJson by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var apiErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Search Stock",
                 style = MaterialTheme.typography.headlineSmall,
                 color = Color.White
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Enter Stock Symbol (e.g., VUSA.L)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    apiErrorMessage = null
                    val result = fetchSearchResultsFromApi("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=${'$'}{searchText.text}&apikey=YOUR_API_KEY") // Replace YOUR_API_KEY
                    isLoading = false
                    if (result.containsKey("error")) {
                        apiErrorMessage = result["error"] as String
                        searchResultJson = ""
                    } else {
                        searchResultJson = result["content"] as String
                        // Potentially parse and display better, or directly enable AddTransactionSheet
                        onSearchResultClick() // Call this to proceed to the next sheet
                    }
                }
            },
            enabled = searchText.text.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text("Search")
        }

        if (apiErrorMessage != null) {
            Text(
                text = "Error: $apiErrorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Displaying the raw JSON result for now, can be parsed and formatted
        // Making the result text clickable to proceed
        if (searchResultJson.isNotBlank() && !isLoading) {
            Text(
                text = searchResultJson,
                color = Color.White,
                modifier = Modifier.clickable { onSearchResultClick() } // This was the original trigger
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheetContent(onDismiss: () -> Unit, vusaViewModel: VusaViewModel) {
    var amountInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var selectedBuyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var selectedCurrency by remember { mutableStateOf("€") } // Default currency
    var calculatedTotal by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isLoading by remember { mutableStateOf(false) } // For save operation

    val context = LocalContext.current // For DatePickerDialog

    val transactions by vusaViewModel.allTransactions.collectAsState(initial = emptyList())
    var showDeleteConfirmDialog by remember { mutableStateOf<VusaTransaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<VusaTransaction?>(null) }


    // Date Picker Dialog
    if (showDatePickerDialog) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = selectedBuyDateMillis
        }
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                calendar.set(year, month, dayOfMonth)
                selectedBuyDateMillis = calendar.timeInMillis
                showDatePickerDialog = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }


    // Edit Transaction Dialog
    transactionToEdit?.let { currentTransaction ->
        var editAmountInput by remember { mutableStateOf(currentTransaction.amount.toString()) }
        var editPriceInput by remember { mutableStateOf(currentTransaction.buyPrice.toString()) }
        var editSelectedBuyDateMillis by remember { mutableStateOf(currentTransaction.transactionTimestamp) }
        var editShowDatePickerDialog by remember { mutableStateOf(false) }
        var editSelectedCurrency by remember { mutableStateOf(currentTransaction.currency) }

        if (editShowDatePickerDialog) {
            val calendar = Calendar.getInstance().apply { timeInMillis = editSelectedBuyDateMillis }
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    editSelectedBuyDateMillis = calendar.timeInMillis
                    editShowDatePickerDialog = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog(
            onDismissRequest = { transactionToEdit = null },
            title = { Text("Edit Transaction") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editAmountInput,
                        onValueChange = { editAmountInput = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = editPriceInput,
                        onValueChange = { editPriceInput = it },
                        label = { Text("Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = dateFormatter.format(Date(editSelectedBuyDateMillis)),
                        onValueChange = {},
                        label = { Text("Buy Date") },
                        readOnly = true,
                        trailingIcon = { Icon(Icons.Filled.DateRange, "Select Date") },
                        modifier = Modifier.clickable { editShowDatePickerDialog = true }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("€", "$", "Other").forEach { currency ->
                            OutlinedButton(
                                onClick = { editSelectedCurrency = currency },
                                border = BorderStroke(1.dp, if (editSelectedCurrency == currency) MaterialTheme.colorScheme.primary else Color.Gray)
                            ) { Text(currency) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = editAmountInput.toDoubleOrNull()
                    val price = editPriceInput.toDoubleOrNull()
                    if (amount != null && price != null) {
                        val updatedTransaction = currentTransaction.copy(
                            amount = amount,
                            buyPrice = price,
                            transactionTimestamp = editSelectedBuyDateMillis,
                            currency = editSelectedCurrency
                        )
                        vusaViewModel.updateTransaction(updatedTransaction)
                        transactionToEdit = null
                        coroutineScope.launch { snackbarHostState.showSnackbar("Transaction Updated!") }
                    }
                }) { Text("Save Changes") }
            },
            dismissButton = { Button(onClick = { transactionToEdit = null }) { Text("Cancel") } }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { transaction ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete this transaction?") },
            confirmButton = {
                Button(onClick = {
                    vusaViewModel.deleteTransactionById(transaction.id)
                    showDeleteConfirmDialog = null
                    coroutineScope.launch { snackbarHostState.showSnackbar("Transaction Deleted!") }
                }) { Text("Delete") }
            },
            dismissButton = { Button(onClick = { showDeleteConfirmDialog = null }) { Text("Cancel") } }
        )
    }


    Scaffold( // Added Scaffold for SnackbarHost
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(16.dp)
                .pointerInput(Unit) { // To dismiss keyboard on tap outside
                    // detectTapGestures(onTap = { focusManager.clearFocus() }) // Re-enable if needed
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add New VUSA Transaction", style = MaterialTheme.typography.titleLarge, color = Color.White)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Sheet", tint = Color.White)
                }
            }

            // Amount Input
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
                label = { Text("Amount (e.g., 10.5)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (amountInput.isNotEmpty()) {
                    { IconButton(onClick = { amountInput = "" }) { Icon(Icons.Filled.Clear, "Clear") } }
                } else null
            )

            // Price Input
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
                label = { Text("Price per Unit (e.g., 75.50)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (priceInput.isNotEmpty()) {
                    { IconButton(onClick = { priceInput = "" }) { Icon(Icons.Filled.Clear, "Clear") } }
                } else null
            )

            // Date Selection
            OutlinedTextField(
                value = dateFormatter.format(Date(selectedBuyDateMillis)),
                onValueChange = { /* Read-only, handled by click */ },
                label = { Text("Buy Date") },
                readOnly = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePickerDialog = true },
                trailingIcon = { Icon(Icons.Filled.DateRange, "Select Date") }
            )

            // Currency Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("€", "$", "Other").forEach { currency ->
                    OutlinedButton(
                        onClick = { selectedCurrency = currency },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(
                            1.dp,
                            if (selectedCurrency == currency) MaterialTheme.colorScheme.primary else Color.Gray
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedCurrency == currency) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) { Text(currency) }
                    if (currency != "Other") Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // Save Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    val amount = amountInput.toDoubleOrNull()
                    val buyPrice = priceInput.toDoubleOrNull()

                    if (amount == null || buyPrice == null || amount <= 0 || buyPrice <= 0) {
                        calculatedTotal = "Invalid amount or price."
                    } else {
                        calculatedTotal = null // Clear previous error
                        isLoading = true
                        // Adjust timestamp to be noon of the selected day to avoid timezone issues with just date
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = selectedBuyDateMillis
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val adjustedTimestamp = calendar.timeInMillis

                        vusaViewModel.insertTransaction(amount, buyPrice, adjustedTimestamp, selectedCurrency)

                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Transaction Saved!")
                            amountInput = ""
                            priceInput = ""
                            // Optionally reset date and currency or keep them for next entry
                            // selectedBuyDateMillis = System.currentTimeMillis()
                            // selectedCurrency = "€"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amountInput.isNotBlank() && priceInput.isNotBlank() && !isLoading,
                shape = MaterialTheme.shapes.large
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White) else Text("Save Transaction")
            }

            calculatedTotal?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("VUSA Transactions", style = MaterialTheme.typography.titleMedium, color = Color.White)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(transactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        currentMarketPrice = 100.0, // Fixed as per request
                        onEditClick = { transactionToEdit = it },
                        onDeleteClick = { showDeleteConfirmDialog = it }
                    )
                    Divider(color = Color.Gray)
                }
            }
        }
    }
}


@Composable
fun TransactionItem(
    transaction: VusaTransaction,
    currentMarketPrice: Double?, // Make nullable if data might be missing
    onEditClick: (VusaTransaction) -> Unit,
    onDeleteClick: (VusaTransaction) -> Unit
) {
    val totalValue = currentMarketPrice?.let { transaction.amount * it } ?: 0.0
    val totalCost = transaction.amount * transaction.buyPrice
    val profitLoss = totalValue - totalCost
    val profitLossPercent = if (totalCost != 0.0) (profitLoss / totalCost) * 100 else 0.0

    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bought: ${formatDynamicDecimal(transaction.amount)} @ ${formatCurrency(transaction.buyPrice, transaction.currency)} on ${dateFormat.format(Date(transaction.transactionTimestamp))}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Row {
                IconButton(onClick = { onEditClick(transaction) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.LightGray)
                }
                IconButton(onClick = { onDeleteClick(transaction) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.LightGray)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Cost: ${formatCurrencyFixed(totalCost, transaction.currency)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray
        )
        if (currentMarketPrice != null) {
            Text(
                text = "Mkt Val: ${formatCurrencyFixed(totalValue, transaction.currency)} (@ ${formatCurrency(currentMarketPrice, transaction.currency)})",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
            Text(
                text = "P/L: ${formatCurrencyFixed(profitLoss, transaction.currency)} (${formatCurrencyFixed(profitLossPercent, "%")}%)",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    profitLoss > 0 -> Color(0xFF4CAF50) // Green
                    profitLoss < 0 -> Color(0xFFF44336) // Red
                    else -> Color.LightGray
                }
            )
        } else {
            Text(
                text = "Market Value: Data N/A",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
        }
    }
}
