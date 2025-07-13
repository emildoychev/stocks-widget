
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
    private lateinit var portfolioTitleFromIntent: String // Made accessible for AddTransactionSheetContent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        portfolioTitleFromIntent = intent.getStringExtra(EXTRA_PORTFOLIO_TITLE) ?: "Default Portfolio" // Ensure a default

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
                                    Text("Add New Stock Transaction")
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    PortfolioDetailScreenMainContent(
                        modifier = Modifier.padding(innerPadding),
                        portfolioTitle = portfolioTitleFromIntent, // Use the activity-level variable
                        vusaViewModel = vusaViewModel,
                        onEditRequest = { transactionToEdit = it },
                        onDeleteRequest = { showDeleteTransactionConfirmDialog = it }
                    )
                }

                if (showSearchStockSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSearchStockSheet = false },
                        sheetState = searchSheetState,
                        containerColor = Color.Black // Assuming dark theme preference
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
                                portfolioName = portfolioTitleFromIntent // Pass portfolio name
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
                                        portfolioName = currentTransaction.portfolioName // Preserve portfolioName
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
    modifier: Modifier = Modifier,
    portfolioTitle: String,
    vusaViewModel: VusaViewModel,
    onEditRequest: (VusaTransaction) -> Unit,
    onDeleteRequest: (VusaTransaction) -> Unit
) {
    // Observe transactions for the specific portfolio
    val transactions by vusaViewModel.getTransactionsForPortfolio(portfolioTitle)
        .collectAsState(initial = emptyList())

    // Calculate total profit/loss
    val currentMarketPrice = 100.0 // Consistent with TransactionItem's currentMarketPrice
    var totalPortfolioCost = 0.0
    var totalPortfolioValue = 0.0
    var portfolioCurrency: String? = null

    transactions.forEach { transaction ->
        totalPortfolioCost += transaction.amount * transaction.buyPrice
        totalPortfolioValue += transaction.amount * currentMarketPrice // Using the fixed market price
        if (portfolioCurrency == null) {
            portfolioCurrency = transaction.currency
        }
    }

    val totalPortfolioProfitLoss = totalPortfolioValue - totalPortfolioCost
    val profitLossColor = when {
        totalPortfolioProfitLoss > 0 -> Color(0xFF4CAF50) // Green
        totalPortfolioProfitLoss < 0 -> Color(0xFFF44336) // Red
        else -> Color.LightGray
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        // Display Total Profit/Loss Summary
        if (transactions.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                if (totalPortfolioProfitLoss != 0.0) {
                    Icon(
                        imageVector = if (totalPortfolioProfitLoss > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = if (totalPortfolioProfitLoss > 0) "Trending Up" else "Trending Down",
                        tint = profitLossColor,
                        modifier = Modifier.size(24.dp).padding(end = 4.dp)
                    )
                }
                Text(
                    text = "Total P/L: ${formatCurrencyFixed(totalPortfolioProfitLoss, portfolioCurrency ?: "€")} " +
                           (if (totalPortfolioCost != 0.0) {
                               val percent = (totalPortfolioProfitLoss / totalPortfolioCost) * 100
                               "(${formatCurrencyFixed(percent, "%")})"
                           } else {""}),
                    style = MaterialTheme.typography.titleMedium,
                    color = profitLossColor
                )
            }
            Text( // Added to display total portfolio value
                text = "Total Value: ${formatCurrencyFixed(totalPortfolioValue, portfolioCurrency ?: "€")}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White, // Or any color you prefer
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
                        currentMarketPrice = currentMarketPrice, // Pass the same market price
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
    portfolioName: String // Added portfolioName parameter
) {
    var amountInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var selectedBuyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var selectedCurrency by remember { mutableStateOf("€") }
    var calculatedTotal by remember { mutableStateOf<String?>(null) }
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
                Text("Add New Transaction to '$portfolioName'", style = MaterialTheme.typography.titleLarge, color = Color.White)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Sheet", tint = Color.White)
                }
            }

            OutlinedTextField(
                value = amountInput,
                onValueChange = { newText -> amountInput = formatDecimalInput(newText) },
                label = { Text("Amount (e.g., 10.5)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (amountInput.isNotEmpty()) {
                    { IconButton(onClick = { amountInput = "" }) { Icon(Icons.Filled.Clear, "Clear") } }
                } else null
            )

            OutlinedTextField(
                value = priceInput,
                onValueChange = { newText -> priceInput = formatDecimalInput(newText) },
                label = { Text("Price per Unit (e.g., 75.50)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (priceInput.isNotEmpty()) {
                    { IconButton(onClick = { priceInput = "" }) { Icon(Icons.Filled.Clear, "Clear") } }
                } else null
            )

            OutlinedTextField(
                value = dateFormatter.format(Date(selectedBuyDateMillis)),
                onValueChange = {},
                label = { Text("Buy Date") },
                readOnly = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePickerDialog = true },
                trailingIcon = { Icon(Icons.Filled.DateRange, "Select Date") }
            )

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

            Button(
                onClick = {
                    focusManager.clearFocus()
                    val amount = amountInput.toDoubleOrNull()
                    val buyPrice = priceInput.toDoubleOrNull()

                    if (amount == null || buyPrice == null || amount <= 0 || buyPrice <= 0) {
                        calculatedTotal = "Invalid amount or price."
                        coroutineScope.launch { localSnackbarHostState.showSnackbar("Invalid input!") }
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

                        // Use portfolioName when inserting transaction
                        vusaViewModel.insertTransaction(amount, buyPrice, adjustedTimestamp, selectedCurrency, portfolioName)

                        coroutineScope.launch {
                            localSnackbarHostState.showSnackbar("Transaction Saved!")
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
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White) else Text("Save Transaction")
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
        profitLoss > 0 -> Color(0xFF4CAF50) // Green
        profitLoss < 0 -> Color(0xFFF44336) // Red
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profitLoss != 0.0) {
                    Icon(
                        imageVector = if (profitLoss > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                        contentDescription = if (profitLoss > 0) "Trending Up" else "Trending Down",
                        tint = profitLossItemColor,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp) // Adjusted size for item
                    )
                }
                Text(
                    text = "P/L: ${formatCurrencyFixed(profitLoss, transaction.currency)} (${formatCurrencyFixed(profitLossPercent, "%")})",
                    style = MaterialTheme.typography.bodySmall,
                    color = profitLossItemColor
                )
            }
        } else {
            Text(
                text = "Market Value: Data N/A",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
        }
    }
}
