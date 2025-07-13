
package com.example.stockswidget

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.stockswidget.ui.theme.StocksWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Function to fetch search results using HttpURLConnection
suspend fun fetchSearchResultsFromApi(urlString: String): Map<String, Any?> {
    return withContext(Dispatchers.IO) {
        val url = try {
            URL(urlString)
        } catch (e: java.net.MalformedURLException) {
            return@withContext mapOf("error" to "Invalid URL: ${e.message}")
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
                mapOf("error" to "HTTP error code: $responseCode - ${connection.responseMessage}")
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val portfolioTitleFromIntent = intent.getStringExtra(EXTRA_PORTFOLIO_TITLE) ?: "Portfolio Details"

        setContent {
            StocksWidgetTheme {
                var showSearchStockSheet by remember { mutableStateOf(false) }
                var showSecondSheet by remember { mutableStateOf(false) }
                val searchSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val secondSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(portfolioTitleFromIntent) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    finish() // Finishes this activity and returns to the previous one
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
                                    Text("Add New Transaction")
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
                        containerColor = Color.Black // Or MaterialTheme.colorScheme.surface
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
                        // modifier = Modifier.fillMaxSize(), // This makes the sheet itself full screen
                        containerColor = Color.Black // Or MaterialTheme.colorScheme.surface
                    ) {
                        // The Box ensures the content within the modal can expand
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            AddTransactionSheetContent(onDismiss = { showSecondSheet = false })
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
            .fillMaxSize() // Fill size to accommodate results
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp) // Uniform padding
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Search", // Title for the search bar section
                 style = MaterialTheme.typography.headlineSmall,
                 color = Color.White // Assuming white text on black background
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White // Assuming white icon on black background
                )
            }
        }
        OutlinedTextField(
            value = searchText,
            onValueChange = { newSearchText ->
                searchText = newSearchText
                if (newSearchText.text.isNotBlank()) {
                    isLoading = true
                    apiErrorMessage = null
                    scope.launch {
                        // Replace with your actual API endpoint and key if needed
                        val apiUrl = "https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords=${newSearchText.text}&apikey=YOUR_API_KEY"
                        // val apiUrl = "https://ceres-searchsuggest.debijenkorf.nl/suggest?keyword=${newSearchText.text}&locale=nl_NL&api-version=3.70" // Original example
                        try {
                            val response = fetchSearchResultsFromApi(apiUrl)

                            if (response.containsKey("error")) {
                                apiErrorMessage = response["error"]?.toString() ?: "Unknown tool error"
                                searchResultJson = ""
                            } else if (response.containsKey("content")) {
                                searchResultJson = response["content"]?.toString() ?: ""
                                if (searchResultJson.isEmpty()) {
                                   // apiErrorMessage = "API call succeeded but content was empty."
                                } else {
                                   apiErrorMessage = null // Clear previous error
                                }
                            } else {
                                apiErrorMessage = "API call did not return content or error."
                                searchResultJson = ""
                            }
                        } catch (e: Exception) { // Catch other exceptions
                            apiErrorMessage = e.message ?: "An unexpected error occurred during API call processing."
                            searchResultJson = ""
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    searchResultJson = ""
                    apiErrorMessage = null
                    isLoading = false
                }
            },
            label = { Text("Enter stock symbol or name") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        apiErrorMessage?.let {
            Text(
                text = "Error: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (searchResultJson.isNotBlank() && !isLoading) {
            Text(
                text = searchResultJson, // In a real app, parse this JSON and display a list
                color = Color.White,
                modifier = Modifier.clickable { onSearchResultClick() } // Example: make the whole JSON text clickable
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheetContent(onDismiss: () -> Unit) {
    var amountInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    val calendar = Calendar.getInstance()
    var selectedBuyDateMillis by remember { mutableStateOf(calendar.timeInMillis) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var selectedCurrency by remember { mutableStateOf("€") } // Default currency
    val focusManager = LocalFocusManager.current
    var calculatedTotal by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) } // For async operations
    val context = LocalContext.current

    if (showDatePickerDialog) {
        val currentCalendar = Calendar.getInstance().apply {
            timeInMillis = selectedBuyDateMillis
        }
        DatePickerDialog(
            context,
            {
                _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                calendar.set(year, month, dayOfMonth)
                selectedBuyDateMillis = calendar.timeInMillis
                showDatePickerDialog = false
            },
            currentCalendar.get(Calendar.YEAR),
            currentCalendar.get(Calendar.MONTH),
            currentCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        // horizontalAlignment = Alignment.CenterHorizontally // Removed for full-width fields
    ) {
        Text(
            "Add New Transaction",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)
        )

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
                //textStyle = MaterialTheme.typography.bodyMedium, // Default is fine
                trailingIcon = if (amountInput.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { amountInput = "" },
                            modifier = Modifier.size(24.dp) // Smaller icon button
                        ) {
                            Icon(
                                Icons.Filled.Clear,
                                "Clear",
                                Modifier.size(18.dp) // Smaller icon
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
                //textStyle = MaterialTheme.typography.bodyMedium,
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
                .height(IntrinsicSize.Min), // Ensures items in row share same height
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = dateFormatter.format(Date(selectedBuyDateMillis)),
                onValueChange = { /* Read-only */ },
                readOnly = true,
                label = { Text("Buy Date") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { showDatePickerDialog = true },
                shape = MaterialTheme.shapes.large,
                trailingIcon = {
                    Icon(
                        Icons.Filled.DateRange,
                        contentDescription = "Select Date",
                        modifier = Modifier.size(18.dp).offset(x = 4.dp) // Adjust icon position if needed
                    )
                }
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                        contentPadding = PaddingValues(all = 8.dp), // Adjust padding for smaller buttons
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF555555)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = currency,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
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
                    val transactionCalendar = Calendar.getInstance()
                    transactionCalendar.timeInMillis = selectedBuyDateMillis
                    transactionCalendar.set(Calendar.HOUR_OF_DAY, 12) // Standardize time
                    transactionCalendar.set(Calendar.MINUTE, 0)
                    transactionCalendar.set(Calendar.SECOND, 0)
                    transactionCalendar.set(Calendar.MILLISECOND, 0)
                    val adjustedTimestamp = transactionCalendar.timeInMillis

                    // Placeholder for actual save logic
                    println("Save Clicked: Amount=$amount, Price=$buyPrice, DateTimestamp=$adjustedTimestamp, Currency=$selectedCurrency")
                    // vusaViewModel.insertTransaction(amount, buyPrice, adjustedTimestamp, selectedCurrency)

                    amountInput = ""; priceInput = "" // Reset fields
                    calculatedTotal = null // Clear any previous error
                    // coroutineScope.launch { snackbarHostState.showSnackbar("Transaction Saved!") }
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
            Text(
                calculatedTotal!!, 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                )
        }
        
        Spacer(modifier = Modifier.weight(1f)) // Pushes content up if keyboard appears or if content is small

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Text("Close Sheet")
        }
    }
}

