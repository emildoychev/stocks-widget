
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
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
// import com.example.stockswidget.data.VusaTransactionDao // Not directly used here
import com.example.stockswidget.data.VusaViewModel
import com.example.stockswidget.data.VusaViewModelFactory
import com.example.stockswidget.ui.theme.StocksWidgetTheme
import com.example.stockswidget.util.*
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
                val addTransactionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                var transactionToEdit by remember { mutableStateOf<VusaTransaction?>(null) }
                var showDeleteTransactionConfirmDialog by remember { mutableStateOf<VusaTransaction?>(null) }
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current
                val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

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
                                    onClick = { showSearchStockSheet = true },
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
                        containerColor = Color.Black
                    ) {
                        SearchBottomSheetContent(
                            onDismiss = { showSearchStockSheet = false },
                            onSearchResultClick = {
                                showSearchStockSheet = false
                                showAddTransactionSheet = true
                            }
                        )
                    }
                }

                if (showAddTransactionSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showAddTransactionSheet = false },
                        sheetState = addTransactionSheetState,
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Black
                    ) {
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            AddTransactionSheetContent(
                                onDismiss = { showAddTransactionSheet = false },
                                vusaViewModel = vusaViewModel,
                                portfolioName = portfolioTitleFromIntent
                            )
                        }
                    }
                }

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
                        title = { Text(stringResource(R.string.edit_transaction_dialog_title)) },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = editAmountInput,
                                    onValueChange = { editAmountInput = formatDecimalInput(it) },
                                    label = { Text(stringResource(R.string.amount_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                OutlinedTextField(
                                    value = editPriceInput,
                                    onValueChange = { editPriceInput = formatDecimalInput(it) },
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
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        stringResource(R.string.currency_euro),
                                        stringResource(R.string.currency_dollar),
                                        stringResource(R.string.currency_other)
                                    ).forEach { currency ->
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
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Transaction Updated!") } // TODO: String resource
                                }
                            }) { Text(stringResource(R.string.save_button)) }
                        },
                        dismissButton = { Button(onClick = { transactionToEdit = null }) { Text(stringResource(R.string.cancel_button)) } }
                    )
                }

                showDeleteTransactionConfirmDialog?.let { transaction ->
                    AlertDialog(
                        onDismissRequest = { showDeleteTransactionConfirmDialog = null },
                        title = { Text(stringResource(R.string.confirm_deletion_dialog_title)) },
                        text = { Text(stringResource(R.string.confirm_delete_transaction_message)) },
                        confirmButton = {
                            Button(onClick = {
                                vusaViewModel.deleteTransactionById(transaction.id)
                                showDeleteTransactionConfirmDialog = null
                                coroutineScope.launch { snackbarHostState.showSnackbar("Transaction Deleted!") } // TODO: String resource
                            }) { Text(stringResource(R.string.delete_button)) }
                        },
                        dismissButton = { Button(onClick = { showDeleteTransactionConfirmDialog = null }) { Text(stringResource(R.string.cancel_button)) } }
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

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (transactions.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                if (totalPortfolioProfitLoss != 0.0) {
                    Icon(
                        imageVector = if (totalPortfolioProfitLoss > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = if (totalPortfolioProfitLoss > 0) "Trending Up" else "Trending Down", // TODO: String resource
                        tint = profitLossColor,
                        modifier = Modifier.size(24.dp).padding(end = 4.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.total_pl_prefix) + formatCurrencyFixed(totalPortfolioProfitLoss, portfolioCurrency ?: stringResource(R.string.currency_euro)) + " " +
                           (if (totalPortfolioCost != 0.0) {
                               val percent = (totalPortfolioProfitLoss / totalPortfolioCost) * 100
                               "(${formatCurrencyFixed(percent, "%")})"
                           } else {""}),
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
        }

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
                        onEditClick = onEditRequest,
                        onDeleteClick = onDeleteRequest
                    )
                    Divider(color = Color.DarkGray)
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
            textStyle = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    apiErrorMessage = null
                    val result = fetchSearchResultsFromApi("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=${searchText.text}&apikey=YOUR_API_KEY") // TODO: API Key and string parts
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
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp)) else Text(stringResource(R.string.search_button)) // TODO: String Resource "Search"
        }

        if (apiErrorMessage != null) {
            Text(
                text = stringResource(R.string.search_stocks_error_prefix) + apiErrorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (searchResultJson.isNotBlank() && !isLoading) {
            Text(
                text = searchResultJson, // This is JSON data, not a user-facing string
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
    var selectedBuyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val initialSelectedCurrency = stringResource(R.string.currency_euro)
    var selectedCurrency by remember { mutableStateOf(initialSelectedCurrency) }
    var calculatedTotal by remember { mutableStateOf<String?>(null) } // This is for error messages
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val localSnackbarHostState = remember { SnackbarHostState() }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current

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

    Scaffold(
        snackbarHost = { SnackbarHost(localSnackbarHostState) },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.add_transaction_sheet_title) + " to '$portfolioName'", style = MaterialTheme.typography.titleLarge, color = Color.White) // TODO: '$portfolioName' not ideal for localization
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_button), tint = Color.White)
                }
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = { newText -> amountInput = formatDecimalInput(newText) },
                label = { Text(stringResource(R.string.quantity_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (amountInput.isNotEmpty()) {
                    { IconButton(onClick = { amountInput = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_button_description)) } } // TODO: new string
                } else null
            )

            OutlinedTextField(
                value = priceInput,
                onValueChange = { newText -> priceInput = formatDecimalInput(newText) },
                label = { Text(stringResource(R.string.buy_price_label_add_tx)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (priceInput.isNotEmpty()) {
                    { IconButton(onClick = { priceInput = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_button_description)) } } // TODO: new string
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
                ).forEach { currency ->
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
                    if (currency != stringResource(R.string.currency_other)) Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    val amount = amountInput.toDoubleOrNull()
                    val buyPrice = priceInput.toDoubleOrNull()

                    if (amount == null || buyPrice == null || amount <= 0 || buyPrice <= 0) {
                        // calculatedTotal = "Invalid amount or price." // TODO: String resource
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Invalid input!") } // TODO: String resource
                    } else {
                        calculatedTotal = null
                        isLoading = true
                        val calendar = Calendar.getInstance().apply {
                            timeInMillis = selectedBuyDateMillis
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val adjustedTimestamp = calendar.timeInMillis

                        vusaViewModel.insertTransaction(amount, buyPrice, adjustedTimestamp, selectedCurrency, portfolioName)

                        coroutineScope.launch {
                            localSnackbarHostState.showSnackbar("Transaction Saved!") // TODO: String resource
                            amountInput = ""
                            priceInput = ""
                            isLoading = false
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amountInput.isNotBlank() && priceInput.isNotBlank() && !isLoading,
                shape = MaterialTheme.shapes.large
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White) else Text(stringResource(R.string.add_transaction_button))
            }

            calculatedTotal?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


@Composable
fun TransactionItem(
    transaction: VusaTransaction,
    currentMarketPrice: Double?,
    onEditClick: (VusaTransaction) -> Unit,
    onDeleteClick: (VusaTransaction) -> Unit
) {
    val totalValue = currentMarketPrice?.let { transaction.amount * it } ?: 0.0
    val totalCost = transaction.amount * transaction.buyPrice
    val profitLoss = totalValue - totalCost
    val profitLossPercent = if (totalCost != 0.0) (profitLoss / totalCost) * 100 else 0.0

    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }
    val profitLossItemColor = when {
        profitLoss > 0 -> Color(0xFF4CAF50)
        profitLoss < 0 -> Color(0xFFF44336)
        else -> Color.LightGray
    }

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
                // TODO: "Bought: " string and " on " string
                text = "Bought: ${formatDynamicDecimal(transaction.amount)} @ ${formatCurrency(transaction.buyPrice, transaction.currency)} on ${dateFormat.format(Date(transaction.transactionTimestamp))}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Row {
                IconButton(onClick = { onEditClick(transaction) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_button_description), tint = Color.LightGray) // TODO: new string
                }
                IconButton(onClick = { onDeleteClick(transaction) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_button_description), tint = Color.LightGray) // TODO: new string
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.transaction_cost_prefix) + formatCurrencyFixed(totalCost, transaction.currency),
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray
        )
        if (currentMarketPrice != null) {
            Text(
                // TODO: "Mkt Val: " string and " (@ " string and ")" string
                text = "Mkt Val: ${formatCurrencyFixed(totalValue, transaction.currency)} (@ ${formatCurrency(currentMarketPrice, transaction.currency)})",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profitLoss != 0.0) {
                    Icon(
                        imageVector = if (profitLoss > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = if (profitLoss > 0) "Trending Up" else "Trending Down", // TODO: String resource
                        tint = profitLossItemColor,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.transaction_pl_prefix) + formatCurrencyFixed(profitLoss, transaction.currency) + " (${formatCurrencyFixed(profitLossPercent, "%")})",
                    style = MaterialTheme.typography.bodySmall,
                    color = profitLossItemColor
                )
            }
        } else {
            Text(
                text = "Market Value: Data N/A", // TODO: String resource
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
        }
    }
}
