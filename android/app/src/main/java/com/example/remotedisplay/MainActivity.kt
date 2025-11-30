package com.example.remotedisplay

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etIpAddress: EditText
    private lateinit var btnConnect: Button
    private lateinit var switchCameraMode: android.widget.Switch
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etIpAddress = findViewById(R.id.etIpAddress)
        btnConnect = findViewById(R.id.btnConnect)
        switchCameraMode = findViewById(R.id.switchCameraMode)

        btnConnect.setOnClickListener {
            val ip = etIpAddress.text.toString()
            if (ip.isBlank()) {
                etIpAddress.error = "Enter IP Address"
                return@setOnClickListener
            }
            
            if (switchCameraMode.isChecked) {
                if (checkCameraPermission()) {
                    launchStreamActivity(ip, true)
                } else {
                    requestCameraPermission()
                }
            } else {
                launchStreamActivity(ip, false)
            }
        }
    }
    
    private fun launchStreamActivity(ip: String, cameraMode: Boolean) {
        val intent = Intent(this, StreamActivity::class.java)
        intent.putExtra("IP_ADDRESS", ip)
        intent.putExtra("CAMERA_MODE", cameraMode)
        startActivity(intent)
    }
    
    private fun checkCameraPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        androidx.core.app.ActivityCompat.requestPermissions(
            this, arrayOf(android.Manifest.permission.CAMERA), 100
        )
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val ip = etIpAddress.text.toString()
            if (ip.isNotBlank()) {
                launchStreamActivity(ip, true)
            }
        }
    }
}
