package com.example.stockswidget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast // Added for error handling in AddTransactionSheetContent
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stockswidget.data.AppDatabase // Added import
import com.example.stockswidget.data.VusaTransaction
import com.example.stockswidget.data.VusaViewModel
import com.example.stockswidget.data.VusaViewModelFactory
import com.example.stockswidget.ui.theme.StocksWidgetTheme
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


fun formatDecimalInput(input: String): String {
    if (input.isEmpty()) return ""
    val regex = Regex("""^\d*\.?\d*$""") // Allows numbers like "123", "123.", "123.45"
    if (!regex.matches(input)) return input.dropLast(1) // Remove last char if not matching
    return input
}

fun formatCurrency(value: Double, currencySymbol: String): String {
    val formatter = DecimalFormat("0.00")
    return "$currencySymbol${formatter.format(value)}"
}

fun formatCurrencyFixed(value: Double, currencySymbol: String): String {
    val formatter = DecimalFormat("#,##0.00")
    return if (currencySymbol == "%") {
        "${formatter.format(value)}"
    } else {
        "$currencySymbol${formatter.format(value)}"
    }
}

suspend fun fetchSearchResultsFromApi(urlString: String): Map<String, Any> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val response = inputStream.bufferedReader().use { it.readText() }
                mapOf("content" to response)
            } else {
                mapOf("error" to "API request failed with code: ${connection.responseCode}")
            }
        } catch (e: IOException) {
            mapOf("error" to "Network error: ${e.localizedMessage}")
        }
    }
}

data class StockQuote(
    val symbol: String,
    val open: String,
    val high: String,
    val low: String,
    val price: String,
    val volume: String,
    val latestTradingDay: String,
    val previousClose: String,
    val change: String,
    val changePercent: String
)

fun parseStockQuote(jsonString: String): StockQuote? {
    val gson = Gson()
    return try {
        val typeToken = object : TypeToken<Map<String, Map<String, String>>>() {}
        val parsedJson: Map<String, Map<String, String>> = gson.fromJson(jsonString, typeToken.type)
        val globalQuoteObject = parsedJson["Global Quote"]

        globalQuoteObject?.let {
            StockQuote(
                symbol = it["01. symbol"] ?: "N/A",
                open = it["02. open"] ?: "N/A",
                high = it["03. high"] ?: "N/A",
                low = it["04. low"] ?: "N/A",
                price = it["05. price"] ?: "N/A",
                volume = it["06. volume"] ?: "N/A",
                latestTradingDay = it["07. latest trading day"] ?: "N/A",
                previousClose = it["08. previous close"] ?: "N/A",
                change = it["09. change"] ?: "N/A",
                changePercent = it["10. change percent"] ?: "N/A"
            )
        }
    } catch (e: JsonSyntaxException) {
        e.printStackTrace()
        null
    }
}

@Composable
fun TransactionItem(
    transaction: VusaTransaction,
    currentMarketPrice: Double,
    onEditClick: (VusaTransaction) -> Unit,
    onDeleteClick: (VusaTransaction) -> Unit
) {
    val profitLoss = (currentMarketPrice - transaction.buyPrice) * transaction.amount
    val profitLossColor = if (profitLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${transaction.amount} shares @ ${formatCurrencyFixed(transaction.buyPrice, transaction.currency)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Date: ${dateFormatter.format(Date(transaction.transactionTimestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "P/L: ${formatCurrencyFixed(profitLoss, transaction.currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = profitLossColor
                )
            }
            Row {
                IconButton(onClick = { onEditClick(transaction) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Transaction", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onDeleteClick(transaction) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Transaction", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

class PortfolioDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PORTFOLIO_TITLE = "com.example.stockswidget.PORTFOLIO_TITLE"

        fun newIntent(context: Context, portfolioTitle: String): Intent {
            val intent = Intent(context, PortfolioDetailActivity::class.java)
            intent.putExtra(EXTRA_PORTFOLIO_TITLE, portfolioTitle)
            return intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val portfolioName = intent.getStringExtra(EXTRA_PORTFOLIO_TITLE) ?: "Unknown Portfolio"

        setContent {
            StocksWidgetTheme {
                // Get DAO instance from your database
                val database = AppDatabase.getDatabase(applicationContext)
                val vusaTransactionDao = database.vusaTransactionDao()

                // Correctly initialize the ViewModel factory with the DAO
                val vusaViewModel: VusaViewModel = viewModel(factory = VusaViewModelFactory(vusaTransactionDao))
                
                var showAddTransactionSheet by remember { mutableStateOf(false) }
                var showSearchBottomSheet by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                var transactionToEdit by remember { mutableStateOf<VusaTransaction?>(null) }
                var showDeleteTransactionConfirmDialog by remember { mutableStateOf<VusaTransaction?>(null) }

                var editAmountInput by remember { mutableStateOf("") }
                var editPriceInput by remember { mutableStateOf("") }
                var editSelectedBuyDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var editShowDatePickerDialog by remember { mutableStateOf(false) }
                var editSelectedCurrency by remember { mutableStateOf("€") }
                val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

                LaunchedEffect(transactionToEdit) {
                    transactionToEdit?.let {
                        editAmountInput = it.amount.toString()
                        editPriceInput = it.buyPrice.toString()
                        editSelectedBuyDateMillis = it.transactionTimestamp
                        editSelectedCurrency = it.currency
                    }
                }

                if (editShowDatePickerDialog) {
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = editSelectedBuyDateMillis)
                    DatePickerDialog(
                        onDismissRequest = { editShowDatePickerDialog = false },
                        confirmButton = {
                            Button(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    editSelectedBuyDateMillis = millis
                                }
                                editShowDatePickerDialog = false
                            }) { Text("OK") }
                        },
                        dismissButton = { Button(onClick = { editShowDatePickerDialog = false }) { Text("Cancel") } }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text(portfolioName) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showSearchBottomSheet = true }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Search Stock")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAddTransactionSheet = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Transaction")
                        }
                    }
                ) { paddingValues -> // This \'paddingValues\' is provided by Scaffold
                    PortfolioDetailScreenMainContent(
                        modifier = Modifier.padding(paddingValues), // Apply the paddingValues here
                        portfolioTitle = portfolioName,
                        vusaViewModel = vusaViewModel,
                        onEditRequest = { transaction -> transactionToEdit = transaction },
                        onDeleteRequest = { transaction -> showDeleteTransactionConfirmDialog = transaction }
                    )
                }

                if (showAddTransactionSheet) {
                    ModalBottomSheet(onDismissRequest = { showAddTransactionSheet = false }) {
                        AddTransactionSheetContent(
                            onDismiss = { showAddTransactionSheet = false },
                            vusaViewModel = vusaViewModel,
                            portfolioName = portfolioName
                        )
                    }
                }

                if (showSearchBottomSheet) {
                    ModalBottomSheet(onDismissRequest = { showSearchBottomSheet = false }) {
                        SearchBottomSheetContent(
                            onDismiss = { showSearchBottomSheet = false },
                            onSearchResultClick = {
                                showSearchBottomSheet = false
                            }
                        )
                    }
                }

                transactionToEdit?.let { currentTransaction ->
                    AlertDialog(
                        onDismissRequest = { transactionToEdit = null },
                        title = { Text("Edit Transaction") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = editAmountInput,
                                    onValueChange = { editAmountInput = formatDecimalInput(it) },
                                    label = { Text("Amount") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                OutlinedTextField(
                                    value = editPriceInput,
                                    onValueChange = { editPriceInput = formatDecimalInput(it) },
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
                                        currency = editSelectedCurrency,
                                        portfolioName = currentTransaction.portfolioName
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

                showDeleteTransactionConfirmDialog?.let { transaction ->
                    AlertDialog(
                        onDismissRequest = { showDeleteTransactionConfirmDialog = null },
                        title = { Text("Confirm Delete") },
                        text = { Text("Are you sure you want to delete this transaction?") },
                        confirmButton = {
                            Button(onClick = {
                                vusaViewModel.deleteTransactionById(transaction.id)
                                showDeleteTransactionConfirmDialog = null
                                coroutineScope.launch { snackbarHostState.showSnackbar("Transaction Deleted!") }
                            }) { Text("Delete") }
                        },
                        dismissButton = { Button(onClick = { showDeleteTransactionConfirmDialog = null }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
}

@Composable
fun PortfolioDetailScreenMainContent(
    modifier: Modifier = Modifier, // Ensure this parameter exists
    portfolioTitle: String,
    vusaViewModel: VusaViewModel,
    onEditRequest: (VusaTransaction) -> Unit,
    onDeleteRequest: (VusaTransaction) -> Unit
) {
    val transactions by vusaViewModel.getTransactionsForPortfolio(portfolioTitle)
        .collectAsState(initial = emptyList())

    val currentMarketPrice = 100.0
    var totalPortfolioCost = 0.0
    var totalPortfolioValue = 0.0
    var portfolioCurrency: String? = null

    transactions.forEach { transaction ->
        totalPortfolioCost += transaction.amount * transaction.buyPrice
        totalPortfolioValue += transaction.amount * currentMarketPrice
        if (portfolioCurrency == null) {
            portfolioCurrency = transaction.currency
        }
    }

    val totalPortfolioProfitLoss = totalPortfolioValue - totalPortfolioCost
    val profitLossColor = when {
        totalPortfolioProfitLoss > 0 -> Color(0xFF4CAF50)
        totalPortfolioProfitLoss < 0 -> Color(0xFFF44336)
        else -> Color.LightGray
    }

    // Use the modifier on the root layout element within this composable
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (transactions.isNotEmpty()) {
            Text(
                text = "Total P/L: ${formatCurrencyFixed(totalPortfolioProfitLoss, portfolioCurrency ?: "€")} " +
                       (if (totalPortfolioCost != 0.0) {
                           val percent = (totalPortfolioProfitLoss / totalPortfolioCost) * 100
                           "(${formatCurrencyFixed(percent, "%")}%)"
                       } else {""}),
                style = MaterialTheme.typography.titleMedium,
                color = profitLossColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Total Value: ${formatCurrencyFixed(totalPortfolioValue, portfolioCurrency ?: "€")}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No transactions yet. Add one using the button below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(transactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        currentMarketPrice = currentMarketPrice,
                        onEditClick = onEditRequest,
                        onDeleteClick = onDeleteRequest
                    )
                    HorizontalDivider(color = Color.DarkGray)
                }
            }
        }
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
                    // Replace YOUR_API_KEY with your actual Alpha Vantage API key
                    val result = fetchSearchResultsFromApi("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=${searchText.text}&apikey=YOUR_API_KEY")
                    isLoading = false
                    if (result.containsKey("error")) {
                        apiErrorMessage = result["error"] as String
                        searchResultJson = ""
                    } else {
                        searchResultJson = result["content"] as String
                        onSearchResultClick() 
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

        if (searchResultJson.isNotBlank() && !isLoading) {
            Text(
                text = searchResultJson, 
                color = Color.White,
                modifier = Modifier.clickable { onSearchResultClick() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheetContent(
    onDismiss: () -> Unit,
    vusaViewModel: VusaViewModel,
    portfolioName: String 
) {
    var amountInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var selectedBuyDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf("€") } 
    val context = LocalContext.current
    // val snackbarHostState = remember { SnackbarHostState() } // Not used locally
    val coroutineScope = rememberCoroutineScope()
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedBuyDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedBuyDateMillis = millis
                    }
                    showDatePickerDialog = false
                }) { Text("OK") }
                            },
            dismissButton = { Button(onClick = { showDatePickerDialog = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Add New Transaction", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = formatDecimalInput(it) },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = priceInput,
            onValueChange = { priceInput = formatDecimalInput(it) },
            label = { Text("Buy Price") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = dateFormatter.format(Date(selectedBuyDateMillis)),
            onValueChange = { /* Read-only */ },
            label = { Text("Buy Date") },
            readOnly = true,
            trailingIcon = { Icon(Icons.Filled.DateRange, "Select Date") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePickerDialog = true }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            listOf("€", "$", "Other").forEach { currency -> 
                OutlinedButton(
                    onClick = { selectedCurrency = currency },
                    border = BorderStroke(
                        1.dp,
                        if (selectedCurrency == currency) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                ) {
                    Text(currency)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val amount = amountInput.toDoubleOrNull()
                val price = priceInput.toDoubleOrNull()

                if (amount != null && price != null && amount > 0 && price > 0) {
                    vusaViewModel.insertTransaction(
                        amount = amount,
                        buyPrice = price,
                        transactionTimestamp = selectedBuyDateMillis,
                        currency = selectedCurrency,
                        portfolioName = portfolioName
                    )
                    Toast.makeText(context, "Transaction Added", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else {
                    Toast.makeText(context, "Invalid input. Please check amount and price.", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Transaction")
        }
    }
}
