
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
import androidx.compose.ui.res.stringResource
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
                            title = { Text(stringResource(R.string.main_activity_title)) },
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
                            Text(stringResource(R.string.add_portfolio_button), color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
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
                            title = { Text(stringResource(R.string.add_new_portfolio_dialog_title)) },
                            text = {
                                OutlinedTextField(
                                    value = newPortfolioTitle,
                                    onValueChange = { newPortfolioTitle = it },
                                    label = { Text(stringResource(R.string.portfolio_title_label)) },
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
                                    Text(stringResource(R.string.save_and_open_button))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showAddPortfolioDialog = false
                                    newPortfolioTitle = ""
                                }) {
                                    Text(stringResource(R.string.cancel_button))
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
                            title = { Text(stringResource(R.string.confirm_deletion_dialog_title)) },
                            text = { Text(stringResource(R.string.confirm_delete_portfolio_message, portfolioTitleToDelete ?: "")) },
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
                                    Text(stringResource(R.string.delete_button))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDeletePortfolioConfirmDialog = false
                                    portfolioTitleToDelete = null
                                }) {
                                    Text(stringResource(R.string.cancel_button))
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
                stringResource(R.string.no_portfolios_message),
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
                    contentDescription = stringResource(R.string.delete_portfolio_content_description, portfolioTitle),
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
