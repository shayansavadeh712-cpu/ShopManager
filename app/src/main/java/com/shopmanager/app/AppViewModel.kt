package com.shopmanager.app

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

val IncomeColor = Color(0xFF34D399)
val ExpenseColor = Color(0xFFF87171)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings = AppSettings(app)
    val auth = AuthStore(app)
    val woo = WooState(app, viewModelScope)
}
