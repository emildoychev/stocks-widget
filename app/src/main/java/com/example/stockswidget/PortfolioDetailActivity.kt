package com.example.stockswidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.stockswidget.ui.theme.StocksWidgetTheme

class PortfolioDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PORTFOLIO_TITLE = "extra_portfolio_title"

        fun newIntent(context: Context, portfolioTitle: String): Intent {
            return Intent(context, PortfolioDetailActivity::class.java).apply {
                putExtra(EXTRA_PORTFOLIO_TITLE, portfolioTitle)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val portfolioTitle = intent.getStringExtra(EXTRA_PORTFOLIO_TITLE) ?: "Portfolio Details"

        setContent {
            StocksWidgetTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(portfolioTitle) },
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
                                containerColor = Color.Black, // Assuming black theme
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            )
                        )
                    }
                    // Scaffold containerColor will be taken from the theme (black)
                ) { innerPadding ->
                    PortfolioDetailScreenContent( // Renamed for clarity from previous interactions
                        modifier = Modifier.padding(innerPadding),
                        portfolioTitle = portfolioTitle
                    )
                }
            }
        }
    }
}

@Composable
fun PortfolioDetailScreenContent(modifier: Modifier = Modifier, portfolioTitle: String) {
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
