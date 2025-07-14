
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
                var selectedStockLastPriceForNewTransaction by remember { mutableStateOf<String?>(null) } // Added state for last price

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
                                        selectedStockLastPriceForNewTransaction = null // Clear previous price
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
                            onStockSelected = { stockSymbol, companyName, lastPrice -> // Updated callback to include lastPrice
                                selectedStockSymbolForNewTransaction = stockSymbol
                                selectedStockNameForNewTransaction = companyName
                                selectedStockLastPriceForNewTransaction = lastPrice // Store the last price
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
                                initialStockLastPrice = selectedStockLastPriceForNewTransaction, // Pass the last price
                                snackbarHostState = snackbarHostState
                            )
                        }
                    }
                }

                transactionToEdit?.let { currentTransaction ->
                    var editAmountInputTfv by remember { mutableStateOf(TextFieldValue(currentTransaction.amount.toString())) }
                    var editPriceInputTfv by remember { mutableStateOf(TextFieldValue(currentTransaction.buyPrice.toString())) }
                    var editSelectedBuyDateMillis by remember { mutableStateOf(currentTransaction.transactionTimestamp) }
                    var editShowDatePickerDialog by remember { mutableStateOf(false) }

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
                        ).show()
                    }

                    AlertDialog(
                        onDismissRequest = { transactionToEdit = null },
                        title = { Text(stringResource(R.string.edit_transaction_dialog_title)) },
                        text = {
                            Column {
                                // Amount
                                OutlinedTextField(
                                    value = editAmountInputTfv,
                                    onValueChange = { editAmountInputTfv = formatDecimalInputTfv(editAmountInputTfv, it.text, 4) },
                                    label = { Text(stringResource(R.string.amount_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                // Price
                                OutlinedTextField(
                                    value = editPriceInputTfv,
                                    onValueChange = { editPriceInputTfv = formatDecimalInputTfv(editPriceInputTfv, it.text, 4) },
                                    label = { Text(stringResource(R.string.price_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                // Buy Date
                                OutlinedTextField(
                                    value = dateFormatter.format(Date(editSelectedBuyDateMillis)),
                                    onValueChange = { /* Read-only, changed by dialog */ },
                                    label = { Text(stringResource(R.string.buy_date_label)) },
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().clickable { editShowDatePickerDialog = true }, // Added clickable
                                    trailingIcon = {
                                        IconButton(onClick = { editShowDatePickerDialog = true }) {
                                            Icon(Icons.Filled.DateRange, stringResource(R.string.select_date_description))
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val amount = editAmountInputTfv.text.toDoubleOrNull()
                                    val price = editPriceInputTfv.text.toDoubleOrNull()
                                    if (amount != null && price != null && amount > 0 && price > 0) {
                                        val updatedTransaction = currentTransaction.copy(
                                            amount = amount,
                                            buyPrice = price,
                                            transactionTimestamp = editSelectedBuyDateMillis
                                        )
                                        vusaViewModel.updateTransaction(updatedTransaction)
                                        transactionToEdit = null
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_transaction_updated))
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_invalid_input))
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.save_button))
                            }
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
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.snackbar_transaction_deleted))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error )
                            ) {
                                Text(stringResource(R.string.delete_button))
                            }
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

    @Composable
    internal fun PortfolioDetailScreenMainContent(
        modifier: Modifier = Modifier,
        portfolioTitle: String,
        vusaViewModel: VusaViewModel,
        onEditRequest: (VusaTransaction) -> Unit,
        onDeleteRequest: (VusaTransaction) -> Unit
    ) {
        val context = LocalContext.current
        val transactionsState by vusaViewModel.getTransactionsForPortfolio(portfolioTitle).collectAsState(initial = emptyList())
        val marketDataState by vusaViewModel.marketData.collectAsState(initial = emptyMap())
        val isLoadingTransactions by vusaViewModel.isLoading.collectAsState()
        val errorState by vusaViewModel.error.collectAsState()

        LaunchedEffect(portfolioTitle) {
            vusaViewModel.refreshMarketDataForPortfolio(portfolioTitle)
        }


        Column(modifier = modifier.padding(8.dp)) {
            if (isLoadingTransactions && transactionsState.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorState != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.error_fetching_transactions_prefix) + errorState,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (transactionsState.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_transactions_message),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 72.dp)) {
                    items(transactionsState) { transaction ->
                        TransactionCard(
                            transaction = transaction,
                            marketData = marketDataState[transaction.stockSymbol],
                            onEdit = { onEditRequest(transaction) },
                            onDelete = { onDeleteRequest(transaction) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    @Composable
    fun TransactionCard(
        transaction: VusaTransaction,
        marketData: VusaViewModel.MarketQuote?,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        val context = LocalContext.current
        val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

        // Determine the most relevant price for profit/loss and general calculations
        val priceForCalculations = marketData?.price ?: transaction.marketPriceAtPurchase ?: transaction.buyPrice

        // Constants for calculations
        val costBasis = transaction.amount * transaction.buyPrice
        val currentMarketValueBasedOnBestPrice = transaction.amount * priceForCalculations
        val profitOrLoss = currentMarketValueBasedOnBestPrice - costBasis
        val profitOrLossPercent = if (costBasis != 0.0) (profitOrLoss / costBasis) * 100 else 0.0

        // Determine what to display in the "Market Value details" section
        val (priceToDisplayInMarketSection: Double?, totalValueToDisplayInMarketSection: Double?, isMarketDataActuallyLive: Boolean) = when {
            marketData != null -> Triple(marketData.price, transaction.amount * marketData.price, true)
            transaction.marketPriceAtPurchase != null -> Triple(transaction.marketPriceAtPurchase, transaction.amount * transaction.marketPriceAtPurchase!!, false) // Added !! for safety, assuming marketPriceAtPurchase is non-null if this branch is taken
            else -> Triple(null, null, false) // No live market data or recorded market price at purchase
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.stockSymbol,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.edit_button_description))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete_button_description), tint = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bought details
                Text(
                    text = stringResource(R.string.bought_transaction_details,
                        formatDynamicDecimal(transaction.amount),
                        formatDynamicDecimal(transaction.buyPrice),
                        dateFormatter.format(Date(transaction.transactionTimestamp))
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(4.dp))

                // Market Value details - MODIFIED
                if (priceToDisplayInMarketSection != null && totalValueToDisplayInMarketSection != null) {
                    Text(
                        text = stringResource(R.string.market_value_details,
                            formatDynamicDecimal(totalValueToDisplayInMarketSection),
                            formatDynamicDecimal(priceToDisplayInMarketSection)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMarketDataActuallyLive) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                } else {
                    Text(
                        stringResource(R.string.market_value_na),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Profit/Loss details - uses updated profitOrLoss and profitOrLossPercent
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (profitOrLoss >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = if (profitOrLoss >= 0) stringResource(R.string.trending_up_description) else stringResource(R.string.trending_down_description),
                        tint = if (profitOrLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336) // Green/Red
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${formatDynamicDecimal(profitOrLoss)} (${formatDynamicDecimal(profitOrLossPercent)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (profitOrLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
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
        initialStockName: String?,
        initialStockLastPrice: String?, // Added parameter for last price
        snackbarHostState: SnackbarHostState
    ) {
        val context = LocalContext.current
        var quantityInputTfv by remember { mutableStateOf(TextFieldValue("")) }
        var priceInputTfv by remember { mutableStateOf(TextFieldValue("")) }
        var selectedBuyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
        var showDatePickerDialog by remember { mutableStateOf(false) }
        val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
        val coroutineScope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        if (showDatePickerDialog) {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedBuyDateMillis }
            val datePickerDialog = DatePickerDialog(
                context,
                { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                    calendar.set(year, month, dayOfMonth)
                    selectedBuyDateMillis = calendar.timeInMillis
                    showDatePickerDialog = false // This handles date selection
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            // Crucial addition: Set an OnDismissListener
            datePickerDialog.setOnDismissListener {
                showDatePickerDialog = false // This handles dismissal without selection
            }
            datePickerDialog.show()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.add_transaction_to_portfolio_title, portfolioName),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Display Stock Symbol and Name (not editable)
            initialStockSymbol?.let { symbol ->
                Text(
                    text = stringResource(R.string.stock_symbol_label) + ": $symbol",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            initialStockName?.let { name ->
                Text(
                    text = "$name", // Assuming you have a R.string.stock_name_label
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp)) // Increased spacing after symbol/name

            OutlinedTextField(
                value = quantityInputTfv,
                onValueChange = { quantityInputTfv = formatDecimalInputTfv(quantityInputTfv, it.text, 4) }, // Allow decimals for quantity
                label = { Text(stringResource(R.string.quantity_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = priceInputTfv,
                onValueChange = { priceInputTfv = formatDecimalInputTfv(priceInputTfv, it.text, 4) }, // Allow up to 4 decimal places for price
                label = { Text(stringResource(R.string.buy_price_label_add_tx)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { showDatePickerDialog = true }) // This Box handles the click
            ) {
                OutlinedTextField(
                    value = dateFormatter.format(Date(selectedBuyDateMillis)),
                    onValueChange = { /* No-op as it's disabled */ },
                    label = { Text(stringResource(R.string.buy_date_label)) },
                    enabled = false,   // Crucial change: Disable the TextField
                    readOnly = true,   // Keep for semantic clarity
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon( // Just the Icon, as the Box handles the click
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = stringResource(R.string.select_date_description)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = Color.Transparent, // Important for OutlinedTextField
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledIndicatorColor = MaterialTheme.colorScheme.outline, // Border color
                        cursorColor = Color.Transparent // No cursor when disabled
                    ),
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() } // To prevent ripple on text field
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    focusManager.clearFocus() // Dismiss keyboard
                    val stockSymbol = initialStockSymbol // Use initialStockSymbol
                    val quantity = quantityInputTfv.text.toDoubleOrNull()
                    val price = priceInputTfv.text.toDoubleOrNull()
                    val marketPriceAtPurchase = initialStockLastPrice?.toDoubleOrNull() // Parse the last price

                    if (stockSymbol != null && stockSymbol.isNotBlank() && quantity != null && quantity > 0 && price != null && price > 0) {
                        val transaction = VusaTransaction(
                            stockSymbol = stockSymbol,
                            amount = quantity,
                            buyPrice = price,
                            transactionTimestamp = selectedBuyDateMillis,
                            portfolioName = portfolioName,
                        )
                        vusaViewModel.addVusaTransaction(transaction)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_transaction_saved))
                        }
                        onDismiss() // Dismiss the bottom sheet
                    } else {
                        coroutineScope.launch {
                            if (stockSymbol == null || stockSymbol.isBlank()){
                                snackbarHostState.showSnackbar(context.getString(R.string.error_invalid_stock_symbol)) // You'll need to add this string resource
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.error_invalid_amount_price))
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_transaction_button))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    }

    @Composable
    internal fun StockResultItem(stockInfo: Map<String, String>, onClick: () -> Unit) {
        val context = LocalContext.current // For string resources
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Text(
                text = stockInfo["stockSymbol"] ?: context.getString(R.string.search_stocks_na_placeholder),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Name: ${stockInfo["companyName"] ?: context.getString(R.string.search_stocks_na_placeholder)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            // Display Last, Change, Change %
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Last: ${stockInfo["last"] ?: context.getString(R.string.search_stocks_na_placeholder)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Text(
                    text = "Change: ${stockInfo["change"] ?: context.getString(R.string.search_stocks_na_placeholder)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((stockInfo["change"]?.toDoubleOrNull() ?: 0.0) >= 0) Color.Green else Color.Red
                )
                Text(
                    text = "Change %: ${stockInfo["change_pct"] ?: context.getString(R.string.search_stocks_na_placeholder)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((stockInfo["change_pct"]?.toDoubleOrNull() ?: 0.0) >= 0) Color.Green else Color.Red
                )
            }
            Text(
                text = "Exchange: ${stockInfo["Exchange"] ?: context.getString(R.string.search_stocks_na_placeholder)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "Country: ${stockInfo["Country"] ?: context.getString(R.string.search_stocks_na_placeholder)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchBottomSheetContent(
        onDismiss: () -> Unit,
        onStockSelected: (stockSymbol: String, companyName: String, lastPrice: String) -> Unit // Updated signature
    ) {
        var searchText by remember { mutableStateOf(TextFieldValue("")) }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        var parsedStockResults by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        var apiErrorMessage by remember { mutableStateOf<String?>(null) }
        var noResultsFound by remember { mutableStateOf(false) }
        var symbolNamesFound by remember { mutableStateOf<List<String>>(emptyList()) }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        // Effect for the first API call (Symbol Lookup)
        LaunchedEffect(searchText.text) {
            val currentSearchText = searchText.text.trim()
            if (currentSearchText.length < 2) {
                if (isActive) {
                    parsedStockResults = emptyList()
                    apiErrorMessage = null
                    isLoading = false
                    noResultsFound = false
                    symbolNamesFound = emptyList()
                }
                return@LaunchedEffect
            }

            if (isActive) {
                isLoading = true
                apiErrorMessage = null
                noResultsFound = false
                parsedStockResults = emptyList() // Clear previous results before new search
                symbolNamesFound = emptyList()
            }
            delay(350L) // Debounce
            if (!isActive) return@LaunchedEffect

            if (currentSearchText == searchText.text.trim()) {
                // First API Call: Get symbol names
                try {
                    val encodedSearchText = URLEncoder.encode(currentSearchText, "UTF-8")
                    val symLookupUrl = "https://symlookup.cnbc.com/symlookup.do?output=jsonp&prefix=${encodedSearchText}&callback=renderTickers"
                    Log.d("SearchBottomSheet", "Symbol Lookup URL: $symLookupUrl")
                    val symLookupResult = fetchSearchResultsFromApi(symLookupUrl)
                    if (!isActive) return@LaunchedEffect

                    if (symLookupResult.containsKey("error")) {
                        if (isActive) {
                            apiErrorMessage = symLookupResult["error"] as? String ?: "Symbol lookup failed"
                            noResultsFound = true
                        }
                    } else {
                        var symContent = symLookupResult["content"] as? String ?: ""
                        val callbackPrefix = "renderTickers("
                        if (symContent.startsWith(callbackPrefix) && symContent.endsWith(")")) {
                            symContent = symContent.substring(callbackPrefix.length, symContent.length - 1)
                        }

                        if (symContent.isBlank() || symContent == "[]") {
                            if (isActive) {
                                noResultsFound = true
                            }
                        } else {
                            val symJsonArray = JSONArray(symContent)
                            val currentSymbolNames = mutableListOf<String>()
                            if (symJsonArray.length() > 0) {
                                val firstSymItem = symJsonArray.getJSONObject(0)
                                if (!(firstSymItem.optString("TotalMatchAvailable", "0") == "0" && symJsonArray.length() == 1)) {
                                    val startIndex = if (firstSymItem.has("symbolName")) 0 else 1
                                    for (i in startIndex until symJsonArray.length()) {
                                        val stockObject = symJsonArray.getJSONObject(i)
                                        stockObject.optString("symbolName", null)?.let { symbolName ->
                                            if (symbolName.isNotBlank()) currentSymbolNames.add(symbolName)
                                        }
                                    }
                                }
                            }
                            if (isActive) {
                               symbolNamesFound = currentSymbolNames
                               if (currentSymbolNames.isEmpty()) {
                                   noResultsFound = true
                               }
                            }
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("SearchBottomSheet", "Symbol lookup JSON parsing error: ${e.message}")
                    if (isActive) {
                        apiErrorMessage = "Failed to parse symbol lookup results."
                        noResultsFound = true
                    }
                } catch (e: Exception) {
                    Log.e("SearchBottomSheet", "Symbol lookup search error: ${e.message}")
                    if (isActive) {
                        apiErrorMessage = "Symbol lookup failed: ${e.message}"
                        noResultsFound = true
                    }
                } finally {
                    if (isActive && (symbolNamesFound.isEmpty() || apiErrorMessage != null)) {
                        isLoading = false
                    }
                }
            }
        }

        // Effect for the second API call, triggered when symbolNamesFound changes and is not empty
        LaunchedEffect(symbolNamesFound) {
            if (symbolNamesFound.isEmpty()) {
                if (searchText.text.trim().length >= 2 && parsedStockResults.isEmpty() && apiErrorMessage == null) {
                     if(isActive) noResultsFound = true
                }
                if(isActive && apiErrorMessage == null) isLoading = false
                return@LaunchedEffect
            }
            
            if (isActive) {
                 if (apiErrorMessage == null) isLoading = true
            }

            try {
                val joinedSymbols = symbolNamesFound.map { URLEncoder.encode(it, "UTF-8") }.joinToString("%7C")
                val quoteUrl = "https://quote.cnbc.com/quote-html-webservice/quote.htm?&symbols=${joinedSymbols}&output=json"
                Log.d("SearchBottomSheet", "Quote URL: $quoteUrl")
                val quoteResult = fetchSearchResultsFromApi(quoteUrl)
                if (!isActive) return@LaunchedEffect

                if (quoteResult.containsKey("error")) {
                    if (isActive) {
                        apiErrorMessage = quoteResult["error"] as? String ?: "Fetching quotes failed"
                        parsedStockResults = emptyList()
                        noResultsFound = true
                    }
                } else {
                    val quoteContent = quoteResult["content"] as? String ?: ""
                    if (quoteContent.isBlank()) {
                            if (isActive) {
                               noResultsFound = true
                               parsedStockResults = emptyList()
                            }
                    } else {
                        val quoteJsonResponse = JSONObject(quoteContent)
                        val quickQuoteArray = quoteJsonResponse.optJSONObject("QuickQuoteResult")?.optJSONArray("QuickQuote")
                        val results = mutableListOf<Map<String, String>>()
                        if (quickQuoteArray != null && quickQuoteArray.length() > 0) {
                            for (i in 0 until quickQuoteArray.length()) {
                                val stockObject = quickQuoteArray.getJSONObject(i)
                                results.add(mapOf(
                                    "stockSymbol" to stockObject.optString("symbol", context.getString(R.string.search_stocks_na_placeholder)),
                                    "companyName" to stockObject.optString("name", context.getString(R.string.search_stocks_na_placeholder)),
                                    "Exchange" to stockObject.optString("exchange", context.getString(R.string.search_stocks_na_placeholder)),
                                    "Country" to stockObject.optString("countryCode", context.getString(R.string.search_stocks_na_placeholder)),
                                    "last" to stockObject.optString("last", context.getString(R.string.search_stocks_na_placeholder)),
                                    "change" to stockObject.optString("change", context.getString(R.string.search_stocks_na_placeholder)),
                                    "change_pct" to stockObject.optString("change_pct", context.getString(R.string.search_stocks_na_placeholder))
                                ))
                            }
                        }
                        if (isActive) {
                            parsedStockResults = results
                            if (results.isEmpty()) noResultsFound = true else noResultsFound = false
                             apiErrorMessage = null
                        }
                    }
                }
            } catch (e: JSONException) {
                Log.e("SearchBottomSheet", "Quote JSON parsing error: ${e.message}")
                if (isActive) {
                    apiErrorMessage = "Failed to parse quote results."
                    parsedStockResults = emptyList()
                    noResultsFound = true
                }
            } catch (e: Exception) {
                Log.e("SearchBottomSheet", "Quote search error: ${e.message}")
                if (isActive) {
                    apiErrorMessage = "Quote search failed: ${e.message}"
                    parsedStockResults = emptyList()
                    noResultsFound = true
                }
            } finally {
                if (isActive) isLoading = false
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
                        { IconButton(onClick = {
                            searchText = TextFieldValue("")
                            parsedStockResults = emptyList()
                            noResultsFound = false
                            apiErrorMessage = null
                            symbolNamesFound = emptyList()
                        }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_button_description)) } }
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
                noResultsFound && searchText.text.trim().length >=2 -> {
                    Text(
                        text = stringResource(R.string.search_stocks_no_results),
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                parsedStockResults.isNotEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(parsedStockResults) { stockInfo ->
                            Log.d("SearchBottomSheet", "StockInfo for item: $stockInfo")
                            StockResultItem(stockInfo = stockInfo, onClick = {
                                onStockSelected(
                                    stockInfo["stockSymbol"] ?: "",
                                    stockInfo["companyName"] ?: "",
                                    stockInfo["last"] ?: context.getString(R.string.search_stocks_na_placeholder) // Pass last price
                                )
                            })
                            Divider(color = Color.DarkGray.copy(alpha = 0.5f))
                        }
                    }
                }
                searchText.text.length < 2 && searchText.text.isNotEmpty() -> {
                     Text(
                        text = stringResource(R.string.search_stocks_enter_more_chars),
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}
