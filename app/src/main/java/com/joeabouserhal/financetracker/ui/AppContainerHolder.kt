package com.joeabouserhal.financetracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.joeabouserhal.financetracker.FinanceTrackerApplication
import com.joeabouserhal.financetracker.di.AppContainer

@Composable
fun rememberAppContainer(): AppContainer =
  (LocalContext.current.applicationContext as FinanceTrackerApplication).container
