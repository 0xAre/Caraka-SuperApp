package com.example.caraka.ui.scanner

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.caraka.R
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class CarakaQrCaptureActivity : CaptureActivity() {
    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var torchButton: TextView
    private var torchOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
    }

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_caraka_qr_capture)

        barcodeView = findViewById(R.id.zxing_barcode_scanner)
        torchButton = findViewById(R.id.qr_scanner_torch)

        findViewById<View>(R.id.qr_scanner_close).setOnClickListener {
            finish()
        }

        torchButton.setOnClickListener {
            torchOn = !torchOn
            if (torchOn) {
                barcodeView.setTorchOn()
                torchButton.text = getString(R.string.qr_flash_on)
                torchButton.contentDescription = getString(R.string.qr_flash_turn_off_cd)
                torchButton.isSelected = true
            } else {
                barcodeView.setTorchOff()
                torchButton.text = getString(R.string.qr_flash_off)
                torchButton.contentDescription = getString(R.string.qr_flash_turn_on_cd)
                torchButton.isSelected = false
            }
        }

        return barcodeView
    }
}
