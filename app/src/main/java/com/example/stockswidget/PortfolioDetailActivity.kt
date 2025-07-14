
package com.example.stockswidget

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.DatePicker // Ensure this import is present
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
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.stockswidget.data.VusaTransaction
import com.example.stockswidget.data.VusaViewModel
import com.example.stockswidget.data.VusaViewModelFactory
import com.example.stockswidget.ui.theme.StocksWidgetTheme
import com.example.stockswidget.util.fetchSearchResultsFromApi
import com.example.stockswidget.util.formatCurrency
import com.example.stockswidget.util.formatCurrencyFixed
import com.example.stockswidget.util.formatDynamicDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Helper to format decimal input and return TextFieldValue for better cursor control
fun formatDecimalInputTfv(currentValue: TextFieldValue, newText: String, maxDecimalPlaces: Int = 4): TextFieldValue {
    val filtered = newText.foldIndexed("") { index, acc, char ->
        if (char.isDigit()) acc + char
        else if ((char == '.' || char == ',') && !acc.contains('.')) acc + '.'
        else acc
    }
    val parts = filtered.split('.', limit = 2)
    val integerPart = parts[0]
    val fractionalPart = if (parts.size > 1) parts[1].take(maxDecimalPlaces) else null

    val newFormattedText = when {
        fractionalPart != null -> if (integerPart.isEmpty()) "0.$fractionalPart" else "$integerPart.$fractionalPart"
        integerPart.isEmpty() && filtered.contains('.') -> "0."
        else -> integerPart
    }
    // Try to maintain cursor position
    val newSelectionStart = currentValue.selection.start + (newFormattedText.length - currentValue.text.length)
    return TextFieldValue(newFormattedText, selection = androidx.compose.ui.text.TextRange(newSelectionStart.coerceIn(0, newFormattedText.length)))
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
    private lateinit var portfolioTitleFromIntent: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        portfolioTitleFromIntent = intent.getStringExtra(EXTRA_PORTFOLIO_TITLE) ?: getString(R.string.default_portfolio_title)

        val application = application as StocksWidgetApplication
        val vusaTransactionDao = application.vusaTransactionDao
        val viewModelFactory = VusaViewModelFactory(vusaTransactionDao)
        vusaViewModel = ViewModelProvider(this, viewModelFactory)[VusaViewModel::class.java]

        setContent {
            StocksWidgetTheme {
                var showSearchStockSheet by remember { mutableStateOf(false) }
                var showAddTransactionSheet by remember { mutableStateOf(false) }
                val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val addTransactionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = {true})

                var transactionToEdit by remember { mutableStateOf<VusaTransaction?>(null) }
                var showDeleteTransactionConfirmDialog by remember { mutableStateOf<VusaTransaction?>(null) }
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current
                val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

                var selectedStockSymbolForNewTransaction by remember { mutableStateOf<String?>(null) }
                var selectedStockNameForNewTransaction by remember { mutableStateOf<String?>(null) }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text(portfolioTitleFromIntent) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back_button_description)
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
                                    onClick = {
                                        selectedStockSymbolForNewTransaction = null // Clear previous selection
                                        selectedStockNameForNewTransaction = null
                                        showSearchStockSheet = true
                                     },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.add_new_stock_transaction_button))
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    PortfolioDetailScreenMainContent(
                        modifier = Modifier.padding(innerPadding),
                        portfolioTitle = portfolioTitleFromIntent,
                        vusaViewModel = vusaViewModel,
                        onEditRequest = { transactionToEdit = it },
                        onDeleteRequest = { showDeleteTransactionConfirmDialog = it }
                    )
                }

                if (showSearchStockSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSearchStockSheet = false },
                        sheetState = searchSheetState,
                        containerColor = Color.Black // Consider MaterialTheme.colorScheme.surface
                    ) {
                        SearchBottomSheetContent(
                            onDismiss = { showSearchStockSheet = false },
                            onStockSelected = { stockSymbol, companyName ->
                                selectedStockSymbolForNewTransaction = stockSymbol
                                selectedStockNameForNewTransaction = companyName
                                coroutineScope.launch {
                                    searchSheetState.hide()
                                    showSearchStockSheet = false
                                    showAddTransactionSheet = true
                                }
                            }
                        )
                    }
                }

                if (showAddTransactionSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showAddTransactionSheet = false },
                        sheetState = addTransactionSheetState,
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Black // Consider MaterialTheme.colorScheme.surface
                    ) {
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            AddTransactionSheetContent(
                                onDismiss = { showAddTransactionSheet = false },
                                vusaViewModel = vusaViewModel,
                                portfolioName = portfolioTitleFromIntent,
                                initialStockSymbol = selectedStockSymbolForNewTransaction,
                                initialStockName = selectedStockNameForNewTransaction,
                                snackbarHostState = snackbarHostState // Pass activity's snackbarHostState
                            )
                        }
                    }
                }

                transactionToEdit?.let { currentTransaction ->
                    var editAmountInputTfv by remember { mutableStateOf(TextFieldValue(currentTransaction.amount.toString())) }
                    var editPriceInputTfv by remember { mutableStateOf(TextFieldValue(currentTransaction.buyPrice.toString())) }
                    var editSelectedBuyDateMillis by remember { mutableStateOf(currentTransaction.transactionTimestamp) }
                    var editShowDatePickerDialog by remember { mutableStateOf(false) }
                    var editSelectedCurrency by remember { mutableStateOf(currentTransaction.currency) }

                    if (editShowDatePickerDialog) {
                        val calendar = Calendar.getInstance().apply { timeInMillis = editSelectedBuyDateMillis }
                        DatePickerDialog(
                            context,
                            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int -> // Explicit types
                                calendar.set(year, month, dayOfMonth)
                                editSelectedBuyDateMillis = calendar.timeInMillis
                                editShowDatePickerDialog = false
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show() // Added .show()
                    }

                    AlertDialog(
                        onDismissRequest = { transactionToEdit = null },
                        title = { Text(stringResource(R.string.edit_transaction_dialog_title)) },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = editAmountInputTfv,
                                    onValueChange = { editAmountInputTfv = formatDecimalInputTfv(editAmountInputTfv, it.text) },
                                    label = { Text(stringResource(R.string.amount_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                OutlinedTextField(
                                    value = editPriceInputTfv,
                                    onValueChange = { editPriceInputTfv = formatDecimalInputTfv(editPriceInputTfv, it.text) },
                                    label = { Text(stringResource(R.string.price_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                OutlinedTextField(
                                    value = dateFormatter.format(Date(editSelectedBuyDateMillis)),
                                    onValueChange = {},
                                    label = { Text(stringResource(R.string.buy_date_label)) },
                                    readOnly = true,
                                    trailingIcon = { Icon(Icons.Filled.DateRange, stringResource(R.string.select_date_description)) },
                                    modifier = Modifier.clickable { editShowDatePickerDialog = true }
                                )
                                // TODO: Implement currency selection for editing if necessary.
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val amount = editAmountInputTfv.text.toDoubleOrNull()
                                val price = editPriceInputTfv.text.toDoubleOrNull()
                                if (amount != null && price != null && amount > 0 && price > 0) {
                                    val updatedTransaction = currentTransaction.copy(
                                        amount = amount,
                                        buyPrice = price,
                                        transactionTimestamp = editSelectedBuyDateMillis,
                                        currency = editSelectedCurrency
                                    )
                                    vusaViewModel.updateTransaction(updatedTransaction)
                                    transactionToEdit = null
                                    coroutineScope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_transaction_updated)) }
                                } else {
                                     coroutineScope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_invalid_input)) }
                                }
                            }) { Text(stringResource(R.string.save_button)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { transactionToEdit = null }) {
                                Text(stringResource(R.string.cancel_button))
                            }
                        }
                    )
                }

                showDeleteTransactionConfirmDialog?.let { transactionToDelete ->
                    AlertDialog(
                        onDismissRequest = { showDeleteTransactionConfirmDialog = null },
                        title = { Text(stringResource(R.string.confirm_deletion_dialog_title)) },
                        text = { Text(stringResource(R.string.confirm_delete_transaction_message)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    vusaViewModel.deleteTransactionById(transactionToDelete.id)
                                    showDeleteTransactionConfirmDialog = null
                                     coroutineScope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_transaction_deleted)) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.delete_button)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteTransactionConfirmDialog = null }) {
                                Text(stringResource(R.string.cancel_button))
                            }
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun PortfolioDetailScreenMainContent(
    modifier: Modifier = Modifier,
    portfolioTitle: String,
    vusaViewModel: VusaViewModel,
    onEditRequest: (VusaTransaction) -> Unit,
    onDeleteRequest: (VusaTransaction) -> Unit
) {
    val transactions by vusaViewModel.getTransactionsForPortfolio(portfolioTitle).collectAsState(initial = emptyList())
    val currentMarketPrice by remember { mutableStateOf(100.0) } // Placeholder
    val portfolioCurrency by remember { mutableStateOf<String?>(null) } // Determine from transactions or settings

    var totalPortfolioValue = 0.0
    var totalPortfolioCost = 0.0
    var totalPortfolioProfitLoss = 0.0

    transactions.forEach { transaction ->
        totalPortfolioCost += transaction.amount * transaction.buyPrice
        totalPortfolioValue += transaction.amount * currentMarketPrice // Assuming currentMarketPrice is per share
    }
    totalPortfolioProfitLoss = totalPortfolioValue - totalPortfolioCost
    val profitLossPercent = if (totalPortfolioCost > 0) (totalPortfolioProfitLoss / totalPortfolioCost) * 100 else 0.0
    val profitLossColor = if (totalPortfolioProfitLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336) // Green or Red

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.total_pl_prefix),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
            if (totalPortfolioProfitLoss != 0.0) {
                Icon(
                    imageVector = if (totalPortfolioProfitLoss > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    contentDescription = if (totalPortfolioProfitLoss > 0) stringResource(R.string.trending_up_description) else stringResource(R.string.trending_down_description),
                    tint = profitLossColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = formatCurrencyFixed(totalPortfolioProfitLoss, portfolioCurrency ?: stringResource(R.string.currency_euro)) +
                       (if (totalPortfolioCost > 0) " (${formatCurrencyFixed(profitLossPercent, "%")})" else ""),
                style = MaterialTheme.typography.titleMedium,
                color = profitLossColor
            )
        }
        Text(
            text = stringResource(R.string.total_value_prefix) + formatCurrencyFixed(totalPortfolioValue, portfolioCurrency ?: stringResource(R.string.currency_euro)),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_transactions_message),
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
                        onEditClick = { onEditRequest(transaction) },
                        onDeleteClick = { onDeleteRequest(transaction) }
                    )
                    Divider(color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: VusaTransaction,
    currentMarketPrice: Double, 
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val transactionCost = transaction.amount * transaction.buyPrice
    val currentTransactionValue = transaction.amount * currentMarketPrice
    val profitLoss = currentTransactionValue - transactionCost
    val profitLossPercent = if (transactionCost > 0) (profitLoss / transactionCost) * 100 else 0.0
    val profitLossColor = if (profitLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stock Symbol: ${transaction.stockSymbol}", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_button_description), tint = Color.LightGray)
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_button_description), tint = Color.LightGray)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.bought_transaction_details, formatDynamicDecimal(transaction.amount), formatCurrency(transaction.buyPrice, transaction.currency), dateFormatter.format(Date(transaction.transactionTimestamp))),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
         if (currentMarketPrice > 0) { 
            Text(
                text = stringResource(R.string.market_value_details, formatCurrency(currentTransactionValue, transaction.currency), formatCurrency(currentMarketPrice, transaction.currency)),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        } else {
            Text(
                text = stringResource(R.string.market_value_na),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.transaction_pl_prefix),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
            if (profitLoss != 0.0) {
                 Icon(
                    imageVector = if (profitLoss > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    contentDescription = if (profitLoss > 0) stringResource(R.string.trending_up_description) else stringResource(R.string.trending_down_description),
                    tint = profitLossColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "${formatCurrency(profitLoss, transaction.currency)} (${formatCurrencyFixed(profitLossPercent, "%")})",
                style = MaterialTheme.typography.bodyMedium,
                color = profitLossColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun StockResultItem(stockInfo: Map<String, String>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Text(
            text = stockInfo["stockSymbol"] ?: "N/A",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Name: ${stockInfo["Name"] ?: "N/A"}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Text(
            text = "Exchange: ${stockInfo["Exchange"] ?: "N/A"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            text = "Country: ${stockInfo["Country"] ?: "N/A"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheetContent(
    onDismiss: () -> Unit, 
    onStockSelected: (stockSymbol: String, companyName: String) -> Unit
) {
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var parsedStockResults by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var apiErrorMessage by remember { mutableStateOf<String?>(null) }
    var noResultsFound by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(searchText.text) {
        val currentSearchText = searchText.text.trim()
        if (currentSearchText.length < 2) { // Minimum 2 chars to search
            if (isActive) {
                parsedStockResults = emptyList()
                apiErrorMessage = null
                isLoading = false
                noResultsFound = false
            }
            return@LaunchedEffect
        }

        if (isActive) {
            isLoading = true
            apiErrorMessage = null
            noResultsFound = false
        }
        delay(350L) // Debounce
        if (!isActive) return@LaunchedEffect

        if (currentSearchText == searchText.text.trim()) {
            try {
                val encodedSearchText = URLEncoder.encode(currentSearchText, "UTF-8")
                val url = "https://symlookup.cnbc.com/symlookup.do?output=jsonp&prefix=${encodedSearchText}&callback=renderTickers"
                val result = fetchSearchResultsFromApi(url)
                if (!isActive) return@LaunchedEffect

                if (result.containsKey("error")) {
                    if (isActive) {
                        apiErrorMessage = result["error"] as String
                        parsedStockResults = emptyList()
                        isLoading = false
                    }
                } else {
                    var content = result["content"] as? String ?: ""
                    val callbackPrefix = "renderTickers("
                    if (content.startsWith(callbackPrefix) && content.endsWith(")")) {
                        content = content.substring(callbackPrefix.length, content.length - 1)
                    }
                    
                    if (content.isBlank() || content == "[]") {
                        if (isActive) {
                            noResultsFound = true
                            parsedStockResults = emptyList()
                            isLoading = false
                        }
                        return@LaunchedEffect
                    }

                    val jsonArray = JSONArray(content)
                    if (jsonArray.length() > 0) {
                        val firstItem = jsonArray.getJSONObject(0)
                        if (firstItem.optString("TotalMatchAvailable", "0") == "0" && jsonArray.length() == 1) {
                            if (isActive) {
                                noResultsFound = true
                                parsedStockResults = emptyList()
                            }
                        } else {
                            val results = mutableListOf<Map<String, String>>()
                            val startIndex = if(firstItem.has("symbolName")) 0 else 1 
                            for (i in startIndex until jsonArray.length()) {
                                val stockObject = jsonArray.getJSONObject(i)
                                results.add(mapOf(
                                    "stockSymbol" to stockObject.optString("symbolName", "N/A"),
                                    "Name" to stockObject.optString("companyName", "N/A"),
                                    "Exchange" to stockObject.optString("exchangeName", "N/A"),
                                    "Country" to stockObject.optString("countryCode", "N/A")
                                ))
                            }
                            if (isActive) {
                                parsedStockResults = results
                                if(results.isEmpty() && !firstItem.has("symbolName")) noResultsFound = true else noResultsFound = false 
                            }
                        }
                    } else {
                        if (isActive) {
                            noResultsFound = true
                            parsedStockResults = emptyList()
                        }
                    }
                    if (isActive) isLoading = false // Ensure isLoading is set to false after processing
                }
            } catch (e: JSONException) {
                Log.e("SearchBottomSheet", "JSON parsing error: ${e.message}")
                if (isActive) {
                    isLoading = false
                    apiErrorMessage = "Failed to parse search results."
                    parsedStockResults = emptyList()
                }
            } catch (e: Exception) {
                Log.e("SearchBottomSheet", "Search error: ${e.message}")
                if (isActive) {
                    isLoading = false
                    apiErrorMessage = "Search failed: ${e.message}"
                    parsedStockResults = emptyList()
                }
            }
        }
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
                stringResource(R.string.search_stocks_sheet_title),
                 style = MaterialTheme.typography.headlineSmall,
                 color = Color.White
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close_button),
                    tint = Color.White
                )
            }
        }
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text(stringResource(R.string.search_stocks_textfield_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = MaterialTheme.shapes.large,
            trailingIcon = if (searchText.text.isNotEmpty()) {
                    { IconButton(onClick = { searchText = TextFieldValue("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_button_description)) } }
                } else null
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            apiErrorMessage != null -> {
                Text(
                    text = stringResource(R.string.search_stocks_error_prefix) + apiErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            noResultsFound && searchText.text.length >=2 -> {
                Text(
                    text = stringResource(R.string.search_stocks_no_results),
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            parsedStockResults.isNotEmpty() -> {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(parsedStockResults) { stockInfo ->
                        StockResultItem(stockInfo = stockInfo, onClick = {
                            onStockSelected(stockInfo["stockSymbol"] ?: "", stockInfo["Name"] ?: "")
                        })
                        Divider(color = Color.DarkGray.copy(alpha = 0.5f))
                    }
                }
            }
            searchText.text.length < 2 && searchText.text.isNotEmpty() -> {
                 Text(
                    text = "Keep typing to search...", // TODO: Add to strings.xml
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheetContent(
    onDismiss: () -> Unit,
    vusaViewModel: VusaViewModel,
    portfolioName: String,
    initialStockSymbol: String?,
    initialStockName: String?, // You might want to display this
    snackbarHostState: SnackbarHostState // Use the activity's snackbar for global messages
) {
    var stockSymbolInputTfv by remember { mutableStateOf(TextFieldValue(initialStockSymbol ?: "")) }
    // TODO: Add a TextField in the UI for stockNameInput if you want to display/edit it.
    var stockNameInput by remember { mutableStateOf(TextFieldValue(initialStockName ?: "")) } 
    var amountInputTfv by remember { mutableStateOf(TextFieldValue("")) }
    var priceInputTfv by remember { mutableStateOf(TextFieldValue("")) } // Corrected mutableStateOf
    var selectedBuyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val initialSelectedCurrencyValue = stringResource(R.string.currency_euro)
    var selectedCurrency by remember { mutableStateOf(initialSelectedCurrencyValue) }

    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    if (showDatePickerDialog) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = selectedBuyDateMillis
        }
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int -> // Explicit types
                calendar.set(year, month, dayOfMonth)
                selectedBuyDateMillis = calendar.timeInMillis
                showDatePickerDialog = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show() // Added .show()
    }

    Scaffold(
        // snackbarHost = { SnackbarHost(localSnackbarHostState) }, // Use global snackbarHostState passed in
        containerColor = Color.Black 
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues) 
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp) // Increased spacing
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.add_transaction_to_portfolio_title, portfolioName),
                    style = MaterialTheme.typography.titleLarge, 
                    color = Color.White 
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_button), tint = Color.White)
                }
            }
            
            // Optional: Display Stock Name (if you added a field for it)
            // if (stockNameInput.text.isNotEmpty()) {
            //     OutlinedTextField(
            //         value = stockNameInput,
            //         onValueChange = { stockNameInput = it }, 
            //         label = { Text("Stock Name (Optional)") }, // Add to strings.xml
            //         singleLine = true,
            //         shape = MaterialTheme.shapes.large,
            //         modifier = Modifier.fillMaxWidth(),
            //         readOnly = true, // Or allow editing
            //         colors = OutlinedTextFieldDefaults.colors( // Make it look less interactive if readOnly
            //             disabledTextColor = LocalContentColor.current.copy(LocalContentAlpha.current),
            //             disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = ContentAlpha.disabled),
            //             disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(ContentAlpha.medium)
            //         )
            //     )
            // }

            OutlinedTextField(
                value = stockSymbolInputTfv,
                onValueChange = { stockSymbolInputTfv = TextFieldValue(it.text.uppercase(Locale.getDefault()), it.selection) },
                label = { Text(stringResource(R.string.stock_symbol_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                 trailingIcon = if (stockSymbolInputTfv.text.isNotEmpty()) {
                    { IconButton(onClick = { stockSymbolInputTfv = TextFieldValue("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_button_description)) } }
                } else null
            )

            OutlinedTextField(
                value = amountInputTfv,
                onValueChange = { amountInputTfv = formatDecimalInputTfv(amountInputTfv, it.text) },
                label = { Text(stringResource(R.string.quantity_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (amountInputTfv.text.isNotEmpty()) {
                    { IconButton(onClick = { amountInputTfv = TextFieldValue("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_button_description)) } }
                } else null
            )

            OutlinedTextField(
                value = priceInputTfv,
                onValueChange = { priceInputTfv = formatDecimalInputTfv(priceInputTfv, it.text) },
                label = { Text(stringResource(R.string.buy_price_label_add_tx)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (priceInputTfv.text.isNotEmpty()) {
                    { IconButton(onClick = { priceInputTfv = TextFieldValue("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_button_description)) } }
                } else null
            )

            OutlinedTextField(
                value = dateFormatter.format(Date(selectedBuyDateMillis)),
                onValueChange = {}, 
                label = { Text(stringResource(R.string.buy_date_label)) },
                readOnly = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePickerDialog = true },
                trailingIcon = { Icon(Icons.Filled.DateRange, stringResource(R.string.select_date_description)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    stringResource(R.string.currency_euro),
                    stringResource(R.string.currency_dollar),
                    stringResource(R.string.currency_other)
                ).forEachIndexed { index, currency ->
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
                    if (index < 2) Spacer(modifier = Modifier.width(8.dp)) // Add spacer except for the last item
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    val symbol = stockSymbolInputTfv.text.trim()
                    val amount = amountInputTfv.text.toDoubleOrNull()
                    val buyPrice = priceInputTfv.text.toDoubleOrNull()

                    if (symbol.isBlank() || amount == null || buyPrice == null || amount <= 0 || buyPrice <= 0) {
                        coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_invalid_input)) }
                    } else {
                        val newTransaction = VusaTransaction(
                            portfolioName = portfolioName,
                            stockSymbol = symbol,
                            // companyName = stockNameInput.text.trim(), // TODO: Uncomment if you add companyName to VusaTransaction
                            amount = amount,
                            buyPrice = buyPrice,
                            currency = selectedCurrency,
                            transactionTimestamp = selectedBuyDateMillis
                        )
                        vusaViewModel.insertTransaction(newTransaction)
                        coroutineScope.launch { 
                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_transaction_saved))
                            onDismiss() // Close sheet after saving and showing snackbar
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), // Added padding top
                enabled = stockSymbolInputTfv.text.isNotBlank() && amountInputTfv.text.isNotBlank() && priceInputTfv.text.isNotBlank()
            ) {
                Text(stringResource(R.string.add_transaction_button))
            }
        }
    }
}

