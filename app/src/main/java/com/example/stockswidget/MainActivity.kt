package com.example.stockswidget

import android.os.Bundle
import android.util.Log // Added import
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
// ... other existing imports
import androidx.compose.foundation.lazy.LazyColumn // Ensuring this is imported
import androidx.compose.foundation.lazy.items // Ensuring this is imported
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// Define a simple data class to hold the VUSA data
data class VusaData(
    val closePrice: String = "Loading...", // Formatted as "€X.XX"
    val rawClosePrice: Double = 0.0, // Unformatted close price
    val lastUpdateTime: String = "Loading..."
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Get DAO from Application
        val application = application as StocksWidgetApplication
        val vusaTransactionDao = application.vusaTransactionDao

        // Create ViewModel using Factory
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
                                val fetchedData = fetchVusaPriceData()
                                vusaData = fetchedData
                            } catch (e: Exception) {
                                errorMessage = "Error: ${e.message}"
                                vusaData = VusaData("N/A", 0.0, "N/A") // Show N/A on error
                            } finally {
                                isLoading = false
                            }
                        }
                    }

                    if (showVusaScreen) {
                        VusaScreen(
                            modifier = Modifier.padding(innerPadding),
                            vusaViewModel = vusaViewModel, // Pass ViewModel
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
                            onShowVusaClick = {
                                showVusaScreen = true
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

                val formattedPrice = if (closePrice.isNaN()) "N/A" else "€${String.format(Locale.GERMANY, "%.2f", closePrice)}"
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
                VusaData(closePrice = formattedPrice, rawClosePrice = rawPrice, lastUpdateTime = formattedTime)
            } else {
                throw Exception("HTTP error code: $responseCode")
            }
        } catch (e: Exception) {
            throw e
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
        Button(onClick = onShowVusaClick) {
            Text("VUSA")
        }
    }
}

@Composable
fun VusaScreen(
    modifier: Modifier = Modifier,
    vusaViewModel: VusaViewModel, // Added ViewModel parameter
    vusaData: VusaData?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") } // User's buy price
    var calculatedTotal by remember { mutableStateOf<String?>(null) }
    var showSaveConfirmation by remember { mutableStateOf(false) }

    // Collect transactions from ViewModel
    val transactions by vusaViewModel.allTransactions.collectAsState(initial = emptyList())

    // Log transactions whenever they change
    LaunchedEffect(transactions) {
        Log.d("VusaScreen", "Transactions list updated. Size: ${transactions.size}")
        if (transactions.isNotEmpty()) {
            transactions.forEachIndexed { index, transaction ->
                Log.d("VusaScreen", "Transaction $index: Amount=${transaction.amount}, Price=${transaction.buyPrice}, Timestamp=${transaction.transactionTimestamp}")
            }
        } else {
            Log.d("VusaScreen", "Transactions list is currently empty after update.")
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { newText ->
                        val filtered = newText.foldIndexed("") { _: Int, acc: String, char: Char ->
                            if (char.isDigit()) {
                                acc + char
                            } else if ((char == '.' || char == ',') && !acc.contains('.')) {
                                acc + '.' // Normalize to period
                            } else {
                                acc
                            }
                        }
                        val parts = filtered.split('.', limit = 2)
                        val integerPart = parts[0]
                        val fractionalPart = if (parts.size > 1) parts[1].take(4) else null

                        amountInput = when {
                            fractionalPart != null -> {
                                if (integerPart.isEmpty()) "0.$fractionalPart"
                                else "$integerPart.$fractionalPart"
                            }
                            integerPart.isEmpty() && filtered.contains('.') -> "0."
                            else -> integerPart
                        }
                    },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        if (amountInput.isNotEmpty()) {
                            IconButton(onClick = { amountInput = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { newText ->
                        val filtered = newText.foldIndexed("") { _: Int, acc: String, char: Char ->
                            if (char.isDigit()) {
                                acc + char
                            } else if ((char == '.' || char == ',') && !acc.contains('.')) {
                                acc + '.' // Normalize to period
                            } else {
                                acc
                            }
                        }
                        val parts = filtered.split('.', limit = 2)
                        val integerPart = parts[0]
                        val fractionalPart = if (parts.size > 1) parts[1].take(4) else null

                        priceInput = when {
                            fractionalPart != null -> {
                                if (integerPart.isEmpty()) "0.$fractionalPart"
                                else "$integerPart.$fractionalPart"
                            }
                            integerPart.isEmpty() && filtered.contains('.') -> "0."
                            else -> integerPart
                        }
                    },
                    label = { Text("Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        if (priceInput.isNotEmpty()) {
                            IconButton(onClick = { priceInput = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
                Button(
                    onClick = {
                        val amount = amountInput.toDoubleOrNull()
                        val buyPrice = priceInput.toDoubleOrNull()

                        if (amount != null && buyPrice != null) {
                            vusaViewModel.insertTransaction(amount = amount, buyPrice = buyPrice)
                            amountInput = "" // Clear input
                            priceInput = ""  // Clear input
                            showSaveConfirmation = true

                            // Calculate current value based on market price
                            val currentClosePrice = vusaData.rawClosePrice
                            if (currentClosePrice != 0.0) {
                                val totalValue = amount * currentClosePrice
                                val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
                                calculatedTotal = currencyFormat.format(totalValue)
                            } else {
                                calculatedTotal = "Market data unavailable for current value."
                            }
                        } else {
                            calculatedTotal = "Invalid input."
                            showSaveConfirmation = false // Don't show "Transaction Saved!" if input is invalid
                        }
                    },
                    modifier = Modifier.wrapContentWidth(),
                    enabled = amountInput.isNotBlank() && priceInput.isNotBlank() && !isLoading
                ) {
                    Text("Save")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (showSaveConfirmation) {
                Text("Transaction Saved!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                calculatedTotal?.let {
                     Text("Calculated Current Value (based on saved amount): $it", style = MaterialTheme.typography.titleMedium)
                }
            }
            // Removed the 'else if (calculatedTotal != null)' block here as it could overwrite the save confirmation message
            // if calculatedTotal was already set to "Invalid input." and then inputs become valid & saved.
            // The "Invalid input" case for the Button's onClick has its own showSaveConfirmation = false.

            // Display Saved Transactions
            Spacer(modifier = Modifier.height(16.dp)) // Add some space before the list

            if (transactions.isNotEmpty()) {
                Text("Saved Transactions", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) { // Give weight to allow scrolling if list is long
                    items(transactions) { transaction ->
                        TransactionItem(transaction = transaction) // Assuming TransactionItem is defined
                        Divider()
                    }
                }
            } else {
                // This will be shown if transactions is empty (initially or if logging shows it becomes empty)
                 Log.d("VusaScreen", "UI: Transactions list is empty, not showing LazyColumn.")
                 Text("No transactions saved yet.", style = MaterialTheme.typography.bodySmall)
            }

        } else { // This else is for 'if (vusaData != null)'
            Text("Tap 'Refresh Data' to load market information.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRefresh) { Text("Refresh Data") }
        }

        if (vusaData != null && !isLoading) { // Show refresh button if data is loaded or was loaded (even if error occurred after)
            Spacer(modifier = Modifier.weight(if (transactions.isEmpty()) 0f else 1f)) // Adjust weight based on list presence
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Refresh Market Data")
            }
        }
    }
}


@Composable
fun TransactionItem(transaction: VusaTransaction) { // Basic example
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Amount: ${transaction.amount}", style = MaterialTheme.typography.bodyMedium)
            Text("Buy Price: €${String.format(Locale.GERMANY, "%.2f", transaction.buyPrice)}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            SimpleDateFormat("dd MMM yy HH:mm", Locale.getDefault()).format(Date(transaction.transactionTimestamp)),
            style = MaterialTheme.typography.bodySmall
        )
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = "Stocks $name")
    }
}

// --- Previews with Fake ViewModel ---
class FakeVusaTransactionDao : VusaTransactionDao {
    private val _transactions = mutableListOf<VusaTransaction>()
    private val transactionsFlow = MutableStateFlow<List<VusaTransaction>>(emptyList())
    private var nextId = 1 // For faking autoGenerate

    override suspend fun insertTransaction(transaction: VusaTransaction) {
        Log.d("FakeVusaTransactionDao", "insertTransaction called with: $transaction")
        // Simulate autoGenerate = true by assigning an ID if it's the default (0 for Int)
        val newTransaction = if (transaction.id == 0) transaction.copy(id = nextId++) else transaction
        _transactions.add(0, newTransaction) // Add to top like real DAO query
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

@Preview(showBackground = true, name = "Vusa Screen - Initial Loading")
@Composable
fun VusaScreenPreviewInitialLoading() {
    StocksWidgetTheme {
        VusaScreen(
            vusaViewModel = getPreviewVusaViewModel(),
            vusaData = null,
            isLoading = true,
            errorMessage = null,
            onRefresh = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "Vusa Screen - Data Loaded, No Input")
@Composable
fun VusaScreenPreviewDataLoadedNoInput() {
    StocksWidgetTheme {
        VusaScreen(
            vusaViewModel = getPreviewVusaViewModel(),
            vusaData = VusaData("€85,50", 85.50, "10:30 AM"),
            isLoading = false,
            errorMessage = null,
            onRefresh = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "Vusa Screen - Data Loaded, With Input & Calculation")
@Composable
fun VusaScreenPreviewDataLoadedWithInputAndCalculation() {
    StocksWidgetTheme {
        val vusaData = VusaData("€85,75", 85.75, "10:35 AM")
        VusaScreen(
            vusaViewModel = getPreviewVusaViewModel(),
            vusaData = vusaData,
            isLoading = false,
            errorMessage = null,
            onRefresh = {},
            onBack = {}
        )
    }
}


@Preview(showBackground = true, name = "Vusa Screen - Error")
@Composable
fun VusaScreenPreviewError() {
    StocksWidgetTheme {
        VusaScreen(
            vusaViewModel = getPreviewVusaViewModel(),
            vusaData = VusaData("N/A", 0.0, "N/A"),
            isLoading = false,
            errorMessage = "Network failed. Tap Retry.",
            onRefresh = {},
            onBack = {}
        )
    }
}

