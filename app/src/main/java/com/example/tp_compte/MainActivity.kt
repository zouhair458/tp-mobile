package com.example.tp_compte

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tp_compte.beans.Compte
import com.example.tp_compte.beans.TypeCompte
import com.example.tp_compte.Config.RetrofitClient
import com.example.tp_compte.api.ApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompteApp()
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun CompteApp() {
    // State variables
    var comptes by remember { mutableStateOf(listOf<Compte>()) }
    var showDialog by remember { mutableStateOf(false) }
    var selectedCompte by remember { mutableStateOf<Compte?>(null) }
    val apiService = RetrofitClient.getJsonApiService()

    // Fetch accounts when the app starts
    LaunchedEffect(Unit) {
        fetchComptes(apiService) { fetchedComptes ->
            comptes = fetchedComptes
        }
    }

    // Scaffold structure
    Scaffold(
        topBar = { TopBar() },
        floatingActionButton = {
            AddCompteFab(onClick = {
                showDialog = true
                selectedCompte = null
            })
        }
    ) {
        CompteList(
            comptes = comptes,
            onDelete = { compte ->
                deleteCompte(apiService, compte, comptes) { updatedComptes ->
                    comptes = updatedComptes
                }
            },
            onEdit = { compte ->
                selectedCompte = compte
                showDialog = true
            }
        )
    }

    // Dialog for adding/editing accounts
    if (showDialog) {
        AccountDialog(
            compte = selectedCompte,
            onSave = { solde, type ->
                if (selectedCompte == null) {
                    addCompte(apiService, comptes, solde, type) { updatedComptes ->
                        comptes = updatedComptes
                    }
                } else {
                    updateCompte(apiService, selectedCompte!!, comptes, solde, type) { updatedComptes ->
                        comptes = updatedComptes
                    }
                }
                showDialog = false
            },
            onCancel = {
                showDialog = false
            }
        )
    }
}

// TopBar component
@Composable
fun TopBar() {
    TopAppBar(title = { Text("Liste des Comptes") })
}

// FloatingActionButton for adding a new account
@Composable
fun AddCompteFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Text("+")
    }
}

// List of accounts
@Composable
fun CompteList(
    comptes: List<Compte>,
    onDelete: (Compte) -> Unit,
    onEdit: (Compte) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(comptes) { compte ->
            CompteCard(compte = compte, onDelete = onDelete, onEdit = onEdit)
        }
    }
}

// Single account card
@Composable
fun CompteCard(
    compte: Compte,
    onDelete: (Compte) -> Unit,
    onEdit: (Compte) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Solde: ${compte.solde}")
            Text(text = "Type: ${compte.type.name}")
            Text(text = "Date de création: ${compte.dateCreation ?: "N/A"}")
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = { onDelete(compte) }) {
                    Text("Supprimer")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onEdit(compte) }) {
                    Text("Modifier")
                }
            }
        }
    }
}

// Dialog for adding or editing accounts
@Composable
fun AccountDialog(
    compte: Compte?,
    onSave: (Double, TypeCompte) -> Unit,
    onCancel: () -> Unit
) {
    var solde by remember { mutableStateOf(compte?.solde?.toString() ?: "") }
    var type by remember { mutableStateOf(compte?.type ?: TypeCompte.COURANT) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (compte == null) "Ajouter un compte" else "Modifier le compte") },
        text = {
            Column {
                OutlinedTextField(
                    value = solde,
                    onValueChange = { solde = it },
                    label = { Text("Solde") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Type de compte:")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = type == TypeCompte.COURANT,
                        onClick = { type = TypeCompte.COURANT }
                    )
                    Text("Courant")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = type == TypeCompte.EPARGNE,
                        onClick = { type = TypeCompte.EPARGNE }
                    )
                    Text("Épargne")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val soldeValue = solde.toDoubleOrNull()
                if (soldeValue != null) {
                    onSave(soldeValue, type)
                }
            }) {
                Text(if (compte == null) "Ajouter" else "Modifier")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Annuler")
            }
        }
    )
}

// Fetch accounts from API
private fun fetchComptes(apiService: ApiService, onResult: (List<Compte>) -> Unit) {
    apiService.getAllComptes().enqueue(object : Callback<List<Compte>> {
        override fun onResponse(call: Call<List<Compte>>, response: Response<List<Compte>>) {
            if (response.isSuccessful) {
                onResult(response.body() ?: emptyList())
            } else {
                Log.e("CompteApp", "Failed to fetch comptes: ${response.code()} - ${response.message()}")
            }
        }

        override fun onFailure(call: Call<List<Compte>>, t: Throwable) {
            Log.e("CompteApp", "Network error during fetch", t)
        }
    })
}

// Add new account
private fun addCompte(
    apiService: ApiService,
    currentComptes: List<Compte>,
    solde: Double,
    type: TypeCompte,
    onResult: (List<Compte>) -> Unit
) {
    val newCompte = Compte(null, solde, java.util.Date(), type)
    apiService.createCompte(newCompte).enqueue(object : Callback<Compte> {
        override fun onResponse(call: Call<Compte>, response: Response<Compte>) {
            if (response.isSuccessful) {
                onResult(currentComptes + (response.body() ?: return))
            } else {
                Log.e("CompteApp", "Failed to add compte: ${response.code()} - ${response.message()}")
            }
        }

        override fun onFailure(call: Call<Compte>, t: Throwable) {
            Log.e("CompteApp", "Network error during add", t)
        }
    })
}

// Update account
private fun updateCompte(
    apiService: ApiService,
    compte: Compte,
    currentComptes: List<Compte>,
    solde: Double,
    type: TypeCompte,
    onResult: (List<Compte>) -> Unit
) {
    val updatedCompte = Compte(compte.id, solde, compte.dateCreation, type)
    apiService.updateCompte(compte.id!!, updatedCompte).enqueue(object : Callback<Compte> {
        override fun onResponse(call: Call<Compte>, response: Response<Compte>) {
            if (response.isSuccessful) {
                onResult(currentComptes.map {
                    if (it.id == compte.id) response.body() ?: it else it
                })
            } else {
                Log.e("CompteApp", "Failed to update compte: ${response.code()} - ${response.message()}")
            }
        }

        override fun onFailure(call: Call<Compte>, t: Throwable) {
            Log.e("CompteApp", "Network error during update", t)
        }
    })
}

// Delete account
private fun deleteCompte(
    apiService: ApiService,
    compteToDelete: Compte,
    currentComptes: List<Compte>,
    onResult: (List<Compte>) -> Unit
) {
    apiService.deleteCompte(compteToDelete.id!!).enqueue(object : Callback<Void> {
        override fun onResponse(call: Call<Void>, response: Response<Void>) {
            if (response.isSuccessful) {
                onResult(currentComptes.filter { it.id != compteToDelete.id })
            } else {
                Log.e("CompteApp", "Failed to delete compte: ${response.code()} - ${response.message()}")
            }
        }

        override fun onFailure(call: Call<Void>, t: Throwable) {
            Log.e("CompteApp", "Network error during delete", t)
        }
    })
}
