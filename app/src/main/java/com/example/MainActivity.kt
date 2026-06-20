package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.data.AppDatabase
import com.example.data.StockRepository
import com.example.ui.StockEntryScreen
import com.example.ui.StockViewModel
import com.example.ui.StockViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = StockRepository(applicationContext, database.stockEntryDao())
        
        val viewModel: StockViewModel by viewModels {
            StockViewModelFactory(repository, applicationContext)
        }
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                StockEntryScreen(viewModel)
            }
        }
    }
}
