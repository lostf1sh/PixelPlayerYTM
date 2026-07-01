package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class AccountsUiState(
    val connectedAccounts: List<Nothing> = emptyList(),
    val disconnectedServices: List<Nothing> = emptyList()
)

@HiltViewModel
class AccountsViewModel @Inject constructor() : ViewModel() {
    val uiState: AccountsUiState = AccountsUiState()
}
