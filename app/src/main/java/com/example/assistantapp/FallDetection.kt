package com.example.assistantapp

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

class FallDetection(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    private var lastAcceleration = 0f
    private var lastGyroMagnitude = 0f
    private var fallDetected = false
    private var emergencyContacts = mutableListOf<String>()
    
    // SharedPreferences for persistent storage
    private val prefs: SharedPreferences = context.getSharedPreferences("fall_detection_prefs", Context.MODE_PRIVATE)
    private val CONTACTS_KEY = "emergency_contacts"
    
    // Thresholds for fall detection
    private val ACCELERATION_THRESHOLD = 20f // m/s²
    private val GYRO_THRESHOLD = 5f // rad/s
    private val STILL_THRESHOLD = 1f // m/s²
    
    init {
        // Initialize sensors
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        // Load contacts from SharedPreferences
        loadContacts()
    }
    
    fun addEmergencyContact(phoneNumber: String) {
        val formattedNumber = formatPhoneNumber(phoneNumber)
        if (!emergencyContacts.contains(formattedNumber)) {
            emergencyContacts.add(formattedNumber)
            saveContacts()
        }
    }
    
    fun removeEmergencyContact(phoneNumber: String) {
        val formattedNumber = formatPhoneNumber(phoneNumber)
        emergencyContacts.remove(formattedNumber)
        saveContacts()
    }
    
    fun getEmergencyContacts(): List<String> {
        return emergencyContacts.toList()
    }
    
    private fun saveContacts() {
        prefs.edit().putStringSet(CONTACTS_KEY, emergencyContacts.toSet()).apply()
    }
    
    private fun loadContacts() {
        val saved = prefs.getStringSet(CONTACTS_KEY, null)
        emergencyContacts = if (saved != null) saved.toMutableList() else mutableListOf()
    }
    
    private fun formatPhoneNumber(phoneNumber: String): String {
        var cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleaned.startsWith("+91")) return cleaned
        if (cleaned.length == 10) return "+91$cleaned"
        if (cleaned.length == 12 && cleaned.startsWith("91")) return "+$cleaned"
        return cleaned
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val acceleration = sqrt(x * x + y * y + z * z)
            
            // Detect sudden acceleration (impact)
            if (acceleration > ACCELERATION_THRESHOLD) {
                fallDetected = true
            }
            
            // Check if the device is still after impact
            if (fallDetected && acceleration < STILL_THRESHOLD) {
                handleFall()
                fallDetected = false
            }
            
            lastAcceleration = acceleration
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val gyroMagnitude = sqrt(x * x + y * y + z * z)
            
            // Detect sudden rotation
            if (gyroMagnitude > GYRO_THRESHOLD) {
                fallDetected = true
            }
            
            lastGyroMagnitude = gyroMagnitude
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
    
    private fun handleFall() {
        if (emergencyContacts.isEmpty()) {
            Toast.makeText(context, "No emergency contact set. Please add one in settings.", Toast.LENGTH_LONG).show()
            return
        }
        if (!checkSmsPermission()) {
            Toast.makeText(context, "SMS permission not granted. Cannot send emergency messages.", Toast.LENGTH_LONG).show()
            return
        }

        val location = getLastKnownLocation()
        val message = buildEmergencyMessage(location)
        
        // Send SMS to all emergency contacts
        for (contact in emergencyContacts) {
            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                
                // Split message if it's too long
                val messageParts = smsManager.divideMessage(message)
                
                // Create pending intents for delivery status
                val sentPI = android.app.PendingIntent.getBroadcast(
                    context, 0, android.content.Intent("SMS_SENT"), 
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val deliveredPI = android.app.PendingIntent.getBroadcast(
                    context, 0, android.content.Intent("SMS_DELIVERED"),
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
                
                smsManager.sendMultipartTextMessage(
                    contact,
                    null,
                    messageParts,
                    arrayListOf(sentPI),
                    arrayListOf(deliveredPI)
                )
                
                Toast.makeText(context, "SMS sent to $contact", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("permission") == true -> "SMS permission denied"
                    e.message?.contains("null") == true -> "Invalid phone number format"
                    else -> "Failed to send SMS: ${e.message}"
                }
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        
        // Vibrate to alert the user
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1000)
        }
    }
    
    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun getLastKnownLocation(): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        }
    }
    
    private fun buildEmergencyMessage(location: Location?): String {
        val locationText = if (location != null) {
            "Location: ${location.latitude}, ${location.longitude}"
        } else {
            "Location: Unknown"
        }
        
        return "EMERGENCY: Possible fall detected! $locationText"
    }
    
    fun cleanup() {
        sensorManager.unregisterListener(this)
    }
} 