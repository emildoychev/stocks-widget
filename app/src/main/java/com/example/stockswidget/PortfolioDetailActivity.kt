package com.example.stockswidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
                var showBottomSheet by remember { mutableStateOf(false) }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                                    onClick = { showBottomSheet = true },
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

                if (showBottomSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = sheetState,
                    ) {
                        SearchBottomSheetContent(
                            onDismiss = { showBottomSheet = false }
                        )
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
fun SearchBottomSheetContent(onDismiss: () -> Unit) {
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
                        val apiUrl = "https://ceres-searchsuggest.debijenkorf.nl/suggest?keyword=${newSearchText.text}&locale=nl_NL&api-version=3.70"
                        try {
                            val response = fetchSearchResultsFromApi(apiUrl) // Corrected API call

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
            label = { Text("Enter stock symbol or name") }, // Hint color
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
                text = searchResultJson,
                color = Color.White, // Assuming white text on black background
                modifier = Modifier
                    .weight(1f) // Allow text to take available space and scroll
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            )
        }
    }
}
