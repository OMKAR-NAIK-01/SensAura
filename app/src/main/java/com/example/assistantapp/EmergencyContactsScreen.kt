package com.example.assistantapp

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun EmergencyContactsScreen(
    fallDetection: FallDetection,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(fallDetection.getEmergencyContacts()) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var newContactNumber by remember { mutableStateOf("") }
    
    // Permission launcher for contacts
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Handle permission granted
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Emergency Contacts",
                style = MaterialTheme.typography.headlineMedium
            )
            
            IconButton(onClick = { showAddContactDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
        
        // Contacts List
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(contacts) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = contact)
                        IconButton(
                            onClick = {
                                fallDetection.removeEmergencyContact(contact)
                                contacts = fallDetection.getEmergencyContacts()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Contact")
                        }
                    }
                }
            }
        }
    }
    
    // Add Contact Dialog
    if (showAddContactDialog) {
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Add Emergency Contact") },
            text = {
                OutlinedTextField(
                    value = newContactNumber,
                    onValueChange = { newContactNumber = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newContactNumber.isNotEmpty()) {
                            fallDetection.addEmergencyContact(newContactNumber)
                            contacts = fallDetection.getEmergencyContacts()
                            newContactNumber = ""
                            showAddContactDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
