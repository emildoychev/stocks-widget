package com.example.stockswidget

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.DateRange // Added import
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Added import
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
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
import java.math.BigDecimal // Added import
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
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
                // Main scaffold for the activity
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var showVusaScreen by remember { mutableStateOf(false) }
                    var vusaData by remember { mutableStateOf<VusaData?>(null) }
                    var isLoading by remember { mutableStateOf(false) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    val coroutineScope = rememberCoroutineScope()
                    val snackbarHostState = remember { SnackbarHostState() } // SnackbarHostState for VusaScreen

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

                    if (showVusaScreen) {
                        VusaScreen(
                            modifier = Modifier.padding(innerPadding), // Apply padding from main scaffold
                            vusaViewModel = vusaViewModel,
                            vusaData = vusaData,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onRefresh = { fetchVusaData() },
                            onBack = { showVusaScreen = false },
                            snackbarHostState = snackbarHostState, // Pass SnackbarHostState
                            coroutineScope = coroutineScope // Pass CoroutineScope
                        )
                        LaunchedEffect(Unit) {
                            if (vusaData == null && !isLoading) {
                                fetchVusaData()
                            }
                        }
                    } else {
                        MainScreen(
                            modifier = Modifier.padding(innerPadding), // Apply padding from main scaffold
                            onShowVusaClick = { showVusaScreen = true }
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

                val formattedPriceDisplay = if (closePrice.isNaN()) "N/A" else formatCurrencyFixed(closePrice, "€") // Use formatCurrencyFixed for display
                val rawPrice = if (closePrice.isNaN()) 0.0 else closePrice

                val formattedTime = if (lastBarUpdateTime == -1L) {
                    "N/A"
                } else {
                    try {
                        val date = Date(lastBarUpdateTime * 1000L)
                        val sdf = SimpleDateFormat("h:mm a", Locale.US)
                        sdf.timeZone = TimeZone.getDefault() // Use device\'s default timezone
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
fun MainScreen(modifier: Modifier = Modifier, onShowVusaClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Greeting(name = "Widget")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onShowVusaClick) { Text("VUSA") }
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
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState, // Added
    coroutineScope: CoroutineScope // Added
) {
    var amountInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var selectedBuyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var selectedCurrency by remember { mutableStateOf("€") } // Default to Euro

    var calculatedTotal by remember { mutableStateOf<String?>(null) } // For errors or other messages

    val transactions by vusaViewModel.allTransactions.collectAsState(initial = emptyList())

    var showDeleteConfirmationDialog by remember { mutableStateOf<VusaTransaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<VusaTransaction?>(null) }

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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp) // Adjusted padding
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = onBack) { Text("Back") }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRefresh) { Text("Retry") }
            } else if (vusaData != null) {
                Text("VUSA Data", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Market Close Price: ${vusaData.closePrice}", style = MaterialTheme.typography.bodyLarge)
                Text("Last Update: ${vusaData.lastUpdateTime}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // Input fields Row
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
                            val fractionalPart = if (parts.size > 1) parts[1].take(4) else null // Keep existing input logic
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
                            val fractionalPart = if (parts.size > 1) parts[1].take(4) else null // Keep existing input logic
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

                // Date and Currency Row
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
                        trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Select Date", modifier = Modifier.size(18.dp).offset(x = 4.dp)) }
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 8.dp), // Align with TextField baseline
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
                                    .fillMaxHeight(), // Make buttons fill height
                                contentPadding = PaddingValues(all = 8.dp),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                shape = MaterialTheme.shapes.medium // Consistent shape
                            ) {
                                Text(currency, style = MaterialTheme.typography.bodyMedium)
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
                            vusaViewModel.insertTransaction(
                                amount = amount,
                                buyPrice = buyPrice,
                                transactionTimestamp = selectedBuyDateMillis,
                                currency = selectedCurrency // Pass selected currency
                            )
                            amountInput = ""; priceInput = "" // Clear inputs
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Transaction Saved!",
                                    duration = SnackbarDuration.Short
                                )
                            }
                            calculatedTotal = null // Clear any previous error message
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
                    Text("VUSA Transactions", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(transactions, key = { it.id }) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                currentMarketPrice = vusaData?.rawClosePrice, 
                                onEditClick = { transactionToEdit = it },
                            )
                            Divider()
                        }
                    }
                } else {
                    Text("No transactions saved yet.", style = MaterialTheme.typography.bodyMedium)
                }

            } else { 
                Text("Tap \'Refresh Data\' to load market information.")
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

    showDeleteConfirmationDialog?.let { transactionToDelete ->
        DeleteConfirmationDialog(
            transaction = transactionToDelete,
            onConfirmDelete = {
                vusaViewModel.deleteTransactionById(transactionToDelete.id)
                showDeleteConfirmationDialog = null
            },
            onDismiss = { showDeleteConfirmationDialog = null }
        )
    }

    // When transactionToEdit is not null:
    transactionToEdit?.let { currentTransactionToEdit ->
        EditTransactionDialog(
            transaction = currentTransactionToEdit,
            onSave = { updatedTransaction ->
                vusaViewModel.updateTransaction(updatedTransaction.copy(currency = currentTransactionToEdit.currency))
                transactionToEdit = null // Close Edit dialog
            },
            onDeleteConfirm = { // Add this new callback
                showDeleteConfirmationDialog = currentTransactionToEdit // Trigger delete confirmation
                transactionToEdit = null // Close Edit dialog
            },
            onDismiss = {
                transactionToEdit = null // Close Edit dialog
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
            indication = null, // Disable ripple effect
            onClick = onClick
        )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { /* Read-only */ },
            label = { Text(label) },
            readOnly = true,
            enabled = false, 
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
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
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .fillMaxWidth()
            .clickable { onEditClick(transaction) }, // Make the whole row clickable for editing
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Child 1: Column for Amount, Buy Price, Date
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "AMS:VUSA", // Updated
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                "Amount: ${formatDynamicDecimal(transaction.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
            Text(
                "Buy Price: ${formatCurrency(transaction.buyPrice, transaction.currency)}", // Updated
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
            Text(
                "Buy Date: ${dateFormat.format(Date(transaction.transactionTimestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
        }

        // Child 2: Column for Current Value and Profit/Loss
        currentMarketPrice?.let { marketPrice ->
            val totalBuyValue = transaction.amount * transaction.buyPrice
            val currentValue = transaction.amount * marketPrice
            val profitOrLoss = currentValue - totalBuyValue
            val profitLossText = formatCurrencyFixed(profitOrLoss, transaction.currency) // Use formatCurrencyFixed
            
            val percentageString = if (totalBuyValue != 0.0) {
                val percentage = (profitOrLoss / totalBuyValue) * 100
                val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
                " (${numberFormat.format(percentage)}%)"
            } else {
                "" // No percentage if initial value was zero
            }

            val profitLossColor = when {
                profitOrLoss > 0 -> Color(0xFF4CAF50) // Green
                profitOrLoss < 0 -> Color(0xFFF44336) // Red
                else -> MaterialTheme.colorScheme.onSurface
            }
            val (icon, iconColor) = when {
                profitOrLoss > 0 -> Icons.Filled.ArrowUpward to Color(0xFF4CAF50) // Green
                profitOrLoss < 0 -> Icons.Filled.ArrowDownward to Color(0xFFF44336) // Red
                else -> null to profitLossColor // No icon if neutral
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "${formatCurrencyFixed(currentValue, transaction.currency)}", // Use formatCurrencyFixed
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White // Keeping original color for current value
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = if (profitOrLoss > 0) "Profit" else "Loss",
                            tint = iconColor,
                            modifier = Modifier.size(20.dp) // Adjust size as needed
                        )
                        Spacer(modifier = Modifier.width(4.dp)) // Space between icon and text
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
        text = { Text("Are you sure you want to delete this transaction? Amount: ${formatDynamicDecimal(transaction.amount)} Price: ${formatCurrency(transaction.buyPrice, transaction.currency)}") }, // Updated
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
    onDeleteConfirm: () -> Unit, // New callback for delete
    onDismiss: () -> Unit
) {
    var editAmount by remember { mutableStateOf(formatDynamicDecimal(transaction.amount)) } // Updated
    var editPrice by remember(transaction.buyPrice) { mutableStateOf(formatDynamicDecimal(transaction.buyPrice)) } // Updated
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
                        val fractionalPart = if (parts.size > 1) parts[1].take(4) else null // Keep existing input logic
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
                        val fractionalPart = if (parts.size > 1) parts[1].take(4) else null // Keep existing input logic
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
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Select Date", modifier = Modifier.size(18.dp).offset(x = 4.dp)) }
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(
                    onClick = onDeleteConfirm, // Call the new delete confirm callback
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = onDismiss // Standard dismiss action
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
        dismissButton = null // All actions are in the confirmButton Row
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


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = "Stocks $name")
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

@Preview(showBackground = true, name = "Main Screen Preview")
@Composable
fun MainScreenPreview() {
    StocksWidgetTheme { MainScreen(onShowVusaClick = {}) }
}

@Preview(showBackground = true, name = "Vusa Screen - Data & List")
@Composable
fun VusaScreenPreviewDataLoadedWithList() {
    StocksWidgetTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope() 
        VusaScreen(
            vusaViewModel = getPreviewVusaViewModel(), 
            vusaData = VusaData("€85.50", 85.50, "10:30 AM"), 
            isLoading = false,
            errorMessage = null,
            onRefresh = {},
            onBack = {},
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope
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
