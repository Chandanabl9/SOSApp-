package com.example.sosapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sosapp.ui.theme.SOSAppTheme
import com.google.android.gms.location.LocationServices
import com.example.sosapp.voice.VoiceDetectionService
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import com.example.sosapp.HandGestureActivity
import com.example.sosapp.FaceDetectionActivity

data class EmergencyContact(val name: String, val phoneNumber: String)

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            // Optional: log permission results
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllRequiredPermissions()

        setContent {
            SOSAppTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var emergencyContacts by remember { mutableStateOf(listOf<EmergencyContact>()) }

                // Load saved contacts
                LaunchedEffect(Unit) {
                    ContactDataStore.getContacts(context).collect { saved ->
                        emergencyContacts = saved
                    }
                }

                MainScreen(
                    contacts = emergencyContacts,
                    onAddContact = { new: EmergencyContact ->
                        emergencyContacts = emergencyContacts + new
                        scope.launch { ContactDataStore.saveContacts(context, emergencyContacts) }
                    },
                    onDeleteContact = { del: EmergencyContact ->
                        emergencyContacts = emergencyContacts - del
                        scope.launch { ContactDataStore.saveContacts(context, emergencyContacts) }
                    },
                    onTriggerEmergency = { triggerSOS(emergencyContacts) }
                )
            }
        }
    }

    private fun requestAllRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun triggerSOS(contacts: List<EmergencyContact>) {
        if (contacts.isEmpty()) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permissions missing!", Toast.LENGTH_SHORT).show()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                val message = "HELP! My location: https://maps.google.com/?q=$lat,$lon"
                val sms = SmsManager.getDefault()

                contacts.forEachIndexed { index, contact ->
                    try {
                        if (index == 0) {
                            val callIntent = Intent(Intent.ACTION_CALL).apply {
                                data = Uri.parse("tel:${contact.phoneNumber}")
                            }
                            startActivity(callIntent)
                            sms.sendTextMessage(contact.phoneNumber, null, message, null, null)
                        } else {
                            sms.sendTextMessage(contact.phoneNumber, null, message, null, null)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to contact ${contact.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Failed to get current location!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun MainScreen(
    contacts: List<EmergencyContact>,
    onAddContact: (EmergencyContact) -> Unit,
    onDeleteContact: (EmergencyContact) -> Unit,
    onTriggerEmergency: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "kAlI(Women Safety App)",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (name.isNotBlank() && phone.isNotBlank()) {
                    onAddContact(EmergencyContact(name, phone))
                    name = ""
                    phone = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Add Contact")
        }

        Text(
            text = "Saved Contacts",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = contacts, key = { it.phoneNumber }) { contact ->
                ContactCard(contact, onDeleteContact)
            }
        }

        VoiceToggleButton()

        Button(
            onClick = onTriggerEmergency,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("SEND SOS ALERT", color = Color.White)
        }

        Button(
            onClick = { context.startActivity(Intent(context, HandGestureActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Enable Hand Gesture Detection")
        }

        Button(
            onClick = { context.startActivity(Intent(context, FaceDetectionActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Enable Face Detection")
        }
    }
}

@Composable
fun ContactCard(contact: EmergencyContact, onDelete: (EmergencyContact) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F1FF))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(contact.name, fontWeight = FontWeight.Bold)
                Text(contact.phoneNumber)
            }
            TextButton(onClick = { onDelete(contact) }) {
                Text("Delete", color = Color.Red)
            }
        }
    }
}

@Composable
fun VoiceToggleButton() {
    val context = LocalContext.current
    var isOn by remember { mutableStateOf(Prefs.isVoiceEnabled(context)) } // <-- read Prefs

    val bgColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFF00C853) else Color(0xFF6200EE),
        animationSpec = tween(durationMillis = 400)
    )

    Button(
        onClick = {
            isOn = !isOn
            if (isOn) VoiceDetectionService.start(context)
            else VoiceDetectionService.stop(context)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = if (isOn) "Voice Detection ON" else "Voice Detection OFF",
            color = Color.White
        )
    }
}


@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    val sampleContacts = listOf(
        EmergencyContact("Alice", "1234567890"),
        EmergencyContact("Bob", "9876543210")
    )
    MainScreen(
        contacts = sampleContacts,
        onAddContact = {},
        onDeleteContact = {},
        onTriggerEmergency = {}
    )
}
