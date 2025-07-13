
package com.example.stockswidget

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stockswidget.ui.theme.StocksWidgetTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ViewModel is not directly used in MainActivity anymore for transactions
        // val application = application as StocksWidgetApplication
        // val vusaTransactionDao = application.vusaTransactionDao
        // val viewModelFactory = VusaViewModelFactory(vusaTransactionDao)
        // val vusaViewModel = ViewModelProvider(this, viewModelFactory)[VusaViewModel::class.java]

        setContent {
            StocksWidgetTheme {
                var showAddPortfolioDialog by remember { mutableStateOf(false) }
                var newPortfolioTitle by remember { mutableStateOf("") }

                var showDeletePortfolioConfirmDialog by remember { mutableStateOf(false) }
                var portfolioTitleToDelete by remember { mutableStateOf<String?>(null) }

                val context = LocalContext.current
                val sharedPreferences = remember {
                    context.getSharedPreferences("portfolio_prefs", Context.MODE_PRIVATE)
                }
                val savedPortfolios = remember {
                    sharedPreferences.getStringSet("custom_portfolio_titles", emptySet())?.toList() ?: emptyList()
                }
                val customPortfolios = remember { mutableStateListOf<String>().apply { addAll(savedPortfolios) } }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("My Portfolios") }, // Updated title
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = Color.White
                            )
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddPortfolioDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Text("  Add Portfolio  ", color = Color.White) // Added padding for better look
                        }
                    },
                    floatingActionButtonPosition = FabPosition.Center
                ) { innerPadding ->
                    PortfolioListScreen(
                        modifier = Modifier.padding(innerPadding),
                        portfolios = customPortfolios,
                        onPortfolioClick = { portfolioTitle ->
                            val intent = PortfolioDetailActivity.newIntent(context, portfolioTitle)
                            context.startActivity(intent)
                        },
                        onDeletePortfolioRequest = { portfolioTitle ->
                            portfolioTitleToDelete = portfolioTitle
                            showDeletePortfolioConfirmDialog = true
                        }
                    )

                    if (showAddPortfolioDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showAddPortfolioDialog = false
                                newPortfolioTitle = ""
                            },
                            title = { Text("Add New Portfolio") },
                            text = {
                                OutlinedTextField(
                                    value = newPortfolioTitle,
                                    onValueChange = { newPortfolioTitle = it },
                                    label = { Text("Portfolio Title") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (newPortfolioTitle.isNotBlank()) {
                                            val portfolioTitle = newPortfolioTitle.trim()
                                            if (!customPortfolios.contains(portfolioTitle)) { // Prevent duplicates
                                                customPortfolios.add(portfolioTitle)
                                                with(sharedPreferences.edit()) {
                                                    putStringSet("custom_portfolio_titles", customPortfolios.toSet())
                                                    apply()
                                                }
                                            }
                                            newPortfolioTitle = ""
                                            showAddPortfolioDialog = false
                                            // Navigate to the newly created portfolio
                                            val intent = PortfolioDetailActivity.newIntent(context, portfolioTitle)
                                            context.startActivity(intent)
                                        }
                                    },
                                    enabled = newPortfolioTitle.isNotBlank()
                                ) {
                                    Text("Save & Open")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showAddPortfolioDialog = false
                                    newPortfolioTitle = ""
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (showDeletePortfolioConfirmDialog && portfolioTitleToDelete != null) {
                        AlertDialog(
                            onDismissRequest = {
                                showDeletePortfolioConfirmDialog = false
                                portfolioTitleToDelete = null
                            },
                            title = { Text("Confirm Deletion") },
                            text = { Text("Are you sure you want to delete the portfolio \"$portfolioTitleToDelete\" and all its transactions? This action cannot be undone.") }, // Updated warning
                            confirmButton = {
                                Button(
                                    onClick = {
                                        portfolioTitleToDelete?.let { title ->
                                            customPortfolios.remove(title)
                                            with(sharedPreferences.edit()) {
                                                putStringSet("custom_portfolio_titles", customPortfolios.toSet())
                                                apply()
                                            }
                                            // TODO: Add logic here to delete all transactions associated with this portfolio from the database
                                            // For now, only removing from SharedPreferences
                                        }
                                        showDeletePortfolioConfirmDialog = false
                                        portfolioTitleToDelete = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDeletePortfolioConfirmDialog = false
                                    portfolioTitleToDelete = null
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PortfolioListScreen(
    modifier: Modifier = Modifier,
    portfolios: SnapshotStateList<String>,
    onPortfolioClick: (String) -> Unit,
    onDeletePortfolioRequest: (String) -> Unit
) {
    if (portfolios.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No portfolios yet. Click 'Add Portfolio' to create one.",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Gray
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(8.dp),
            contentPadding = PaddingValues(bottom = 72.dp) // Ensure FAB doesn't overlap last item
        ) {
            items(portfolios) { portfolioTitle ->
                PortfolioListItem(
                    portfolioTitle = portfolioTitle,
                    onClick = { onPortfolioClick(portfolioTitle) },
                    onDelete = { onDeletePortfolioRequest(portfolioTitle) }
                )
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun PortfolioListItem(
    portfolioTitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = portfolioTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete $portfolioTitle",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Main Activity - Empty List")
@Composable
fun MainActivityEmptyPreview() {
    StocksWidgetTheme {
        PortfolioListScreen(
            portfolios = remember { mutableStateListOf() },
            onPortfolioClick = {},
            onDeletePortfolioRequest = {}
        )
    }
}

@Preview(showBackground = true, name = "Main Activity - With Portfolios")
@Composable
fun MainActivityWithPortfoliosPreview() {
    StocksWidgetTheme {
        PortfolioListScreen(
            portfolios = remember { mutableStateListOf("Tech Stocks", "Retirement Fund", "Crypto") },
            onPortfolioClick = {},
            onDeletePortfolioRequest = {}
        )
    }
}
