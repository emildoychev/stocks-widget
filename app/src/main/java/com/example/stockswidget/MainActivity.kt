package com.example.stockswidget

import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Clear // Will be replaced
import androidx.compose.material.icons.filled.DateRange // Added import
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Added import
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource // Added import
import com.example.stockswidget.R // Added import
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.stockswidget.data.VusaTransaction
import com.example.stockswidget.data.VusaTransactionDao
import com.example.stockswidget.data.VusaViewModel
import com.example.stockswidget.data.VusaViewModelFactory
import com.example.stockswidget.ui.theme.StocksWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var showVusaScreen by remember { mutableStateOf(false) }
                    var vusaData by remember { mutableStateOf<VusaData?>(null) }
                    var isLoading by remember { mutableStateOf(false) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    val coroutineScope = rememberCoroutineScope()

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
                            modifier = Modifier.padding(innerPadding),
                            vusaViewModel = vusaViewModel,
                            vusaData = vusaData,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onRefresh = { fetchVusaData() },
                            onBack = { showVusaScreen = false }
                        )
                        LaunchedEffect(Unit) {
                            if (vusaData == null && !isLoading) {
                                fetchVusaData()
                            }
                        }
                    } else {
                        MainScreen(
                            modifier = Modifier.padding(innerPadding),
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

                val formattedPrice = if (closePrice.isNaN()) "N/A" else "€${String.format(Locale.GERMANY, "%.2f", closePrice)}"
                val rawPrice = if (closePrice.isNaN()) 0.0 else closePrice

                val formattedTime = if (lastBarUpdateTime == -1L) {
                    "N/A"
                } else {
                    try {
                        val date = Date(lastBarUpdateTime * 1000L)
                        val sdf = SimpleDateFormat("h:mm a", Locale.US)
                        sdf.timeZone = TimeZone.getDefault() // Use device's default timezone
                        sdf.format(date)
                    } catch (e: Exception) {
                        "Time Format Error"
                    }
                }
                VusaData(closePrice = formattedPrice, rawClosePrice = rawPrice, lastUpdateTime = formattedTime)
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
    onBack: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var selectedBuyDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var selectedCurrency by remember { mutableStateOf("€") }

    val context = LocalContext.current
    var calculatedTotal by remember { mutableStateOf<String?>(null) }
    // showSaveConfirmation is no longer needed

    val transactions by vusaViewModel.allTransactions.collectAsState(initial = emptyList())

    var showDeleteConfirmationDialog by remember { mutableStateOf<VusaTransaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<VusaTransaction?>(null) }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(transactions) {
        Log.d("VusaScreen", "Transactions list updated. Size: ${transactions.size}")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
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

            // Row for Amount and Price input fields
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
                    textStyle = MaterialTheme.typography.bodyMedium, // Smaller font
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
                    textStyle = MaterialTheme.typography.bodyMedium, // Smaller font
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
            Spacer(modifier = Modifier.height(8.dp)) // Spacer between Amount/Price row and Buy Date/Currency row

            // Row for Buy Date and Currency Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min), // Ensures Row height is based on tallest child
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

                // Row for Currency Buttons
                Row(
                    modifier = Modifier
                        .weight(1f) 
                        .fillMaxHeight() 
                        .padding(top = 8.dp),
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
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = MaterialTheme.shapes.medium
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
                            transactionTimestamp = selectedBuyDateMillis
                        )
                        amountInput = ""; priceInput = ""
                        // selectedBuyDateMillis = System.currentTimeMillis() // Optionally reset date
                        Toast.makeText(context, "Transaction Saved!", Toast.LENGTH_SHORT).show()
                        // calculatedTotal is no longer set here for successful save
                    } else {
                        calculatedTotal = "Invalid input."
                    }
                },
                modifier = Modifier.fillMaxWidth(), 
                enabled = amountInput.isNotBlank() && priceInput.isNotBlank() && !isLoading,
                shape = MaterialTheme.shapes.large
            ) { Text("Save") }
            
            Spacer(modifier = Modifier.height(8.dp)) 

            // Removed the if (showSaveConfirmation) block
            // Display only error message for calculatedTotal if it's not null (i.e., "Invalid input.")
            if (calculatedTotal != null) {
                Text(calculatedTotal!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (transactions.isNotEmpty()) {
                Text("Saved Transactions", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            currentMarketPrice = vusaData?.rawClosePrice, 
                            onEditClick = { transactionToEdit = it },
                            onDeleteClick = { showDeleteConfirmationDialog = it }
                        )
                        Divider()
                    }
                }
            } else {
                Text("No transactions saved yet.", style = MaterialTheme.typography.bodySmall)
            }
            // Removed the "Refresh Market Data" button that was here
            // Spacer(modifier = Modifier.weight(0.1f)) // This spacer might need adjustment or removal
        } else {
            Text("Tap 'Refresh Data' to load market information.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                focusManager.clearFocus()
                onRefresh()
            }) { Text("Refresh Data") }
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

    transactionToEdit?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
            onSave = { updatedTransaction ->
                vusaViewModel.updateTransaction(updatedTransaction)
                transactionToEdit = null
            },
            onDismiss = { transactionToEdit = null }
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
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = false, // Disable the TextField itself
            modifier = Modifier.fillMaxWidth().fillMaxHeight(), // Added fillMaxHeight() here
            shape = MaterialTheme.shapes.large,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium, // Smaller font
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors( // Customize disabled colors
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun TransactionItem(
    transaction: VusaTransaction,
    currentMarketPrice: Double?,
    onEditClick: (VusaTransaction) -> Unit,
    onDeleteClick: (VusaTransaction) -> Unit
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Amount: ${transaction.amount}", style = MaterialTheme.typography.bodyMedium)
            Text("Buy Price: €${String.format(Locale.GERMANY, "%.2f", transaction.buyPrice)}", style = MaterialTheme.typography.bodyMedium)
            Text("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(transaction.transactionTimestamp))}", style = MaterialTheme.typography.bodySmall)
            currentMarketPrice?.let { marketPrice ->
                val currentValue = transaction.amount * marketPrice
                Text(
                    "Current Value: ${NumberFormat.getCurrencyInstance(Locale.GERMANY).format(currentValue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Row {
            IconButton(onClick = { onEditClick(transaction) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit Transaction")
            }
            IconButton(onClick = { onDeleteClick(transaction) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete Transaction")
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
        text = { Text("Are you sure you want to delete this transaction?\nAmount: ${transaction.amount}, Price: €${String.format(Locale.GERMANY, "%.2f", transaction.buyPrice)}") },
        confirmButton = {
            Button(onClick = {
                onConfirmDelete()
            }) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditTransactionDialog(
    transaction: VusaTransaction,
    onSave: (VusaTransaction) -> Unit,
    onDismiss: () -> Unit
) {
    var editAmount by remember { mutableStateOf(transaction.amount.toString()) }
    var editPrice by remember { mutableStateOf(String.format(Locale.US, "%.4f", transaction.buyPrice)) }

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
                            else if ((char == '.' || char == ',') && !acc.contains('.') && !acc.contains(',')) acc + char.toString().replace(',', '.')
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
                    textStyle = MaterialTheme.typography.bodyMedium, // Smaller font
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editPrice,
                    onValueChange = { newValue ->
                        val filtered = newValue.foldIndexed("") { _, acc, char ->
                            if (char.isDigit()) acc + char
                            else if ((char == '.' || char == ',') && !acc.contains('.') && !acc.contains(',')) acc + char.toString().replace(',', '.')
                           
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
                    label = { Text("Price (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium, // Smaller font
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val newAmount = editAmount.toDoubleOrNull()
                val newPrice = editPrice.toDoubleOrNull()
                if (newAmount != null && newPrice != null) {
                    onSave(transaction.copy(amount = newAmount, buyPrice = newPrice, transactionTimestamp = transaction.transactionTimestamp)) // Keep original timestamp
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = "Stocks $name")
    }
}

class FakeVusaTransactionDao : VusaTransactionDao {
    private val _transactions = mutableListOf<VusaTransaction>()
    private val transactionsFlow = MutableStateFlow<List<VusaTransaction>>(emptyList())
    private var nextId = 1

    init {
        val sampleData = listOf(
            VusaTransaction(id=nextId++, amount = 10.0, buyPrice = 80.50, transactionTimestamp = System.currentTimeMillis() - 200000),
            VusaTransaction(id=nextId++, amount = 5.0, buyPrice = 82.30, transactionTimestamp = System.currentTimeMillis() - 100000)
        )
        _transactions.addAll(sampleData)
        transactionsFlow.value = _transactions.toList()
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
            transactionsFlow.value = _transactions.toList()
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
        VusaScreen(
            vusaViewModel = getPreviewVusaViewModel(),
            vusaData = VusaData("€85,50", 85.50, "10:30 AM"),
            isLoading = false, errorMessage = null, onRefresh = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "Vusa Screen - Edit Dialog")
@Composable
fun VusaScreenPreviewEditDialog() {
    StocksWidgetTheme {
        val sampleTransaction = remember { VusaTransaction(id = 1, amount = 10.0, buyPrice = 80.5, transactionTimestamp = System.currentTimeMillis()) }
        EditTransactionDialog(transaction = sampleTransaction, onSave = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, name = "Vusa Screen - Delete Dialog")
@Composable
fun VusaScreenPreviewDeleteDialog() {
    StocksWidgetTheme {
        val sampleTransaction = remember { VusaTransaction(id = 1, amount = 10.0, buyPrice = 80.5, transactionTimestamp = System.currentTimeMillis()) }
        DeleteConfirmationDialog(transaction = sampleTransaction, onConfirmDelete = {}, onDismiss = {})
    }
}
