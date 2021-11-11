/*
  @version 1.0
 * @author Rajiv M.
 * @copyright Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vsnconnect

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import android.widget.Toast
import com.vsnmobil.vsnconnect.adapter.ScannedDeviceAdapter
import java.util.*

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
class DeviceScanActivity : Activity() {

    private var scannedDeviceAdapter: ScannedDeviceAdapter? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothLeScanner: BluetoothLeScanner? = null
    private var mScanning = false
    private var mHandler: Handler? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devicescan)
        actionBar?.setTitle(R.string.app_name)
        mHandler = Handler(Looper.getMainLooper())
        val scanningDeviceListView =
            findViewById<ListView>(R.id.managedevices_scanning_device_listview)

        // Use this check to determine whether BLE is supported on the device.
        // Then you can selectively disable BLE-related features.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }

        // Initializes a Bluetooth adapter. For API level 18 and above, get a
        // reference to
        // BluetoothAdapter through BluetoothManager.
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        mBluetoothLeScanner = mBluetoothAdapter?.bluetoothLeScanner

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initializes list view adapter.
        scannedDeviceAdapter = ScannedDeviceAdapter(this, R.layout.listitem_device, ArrayList())
        scanningDeviceListView.adapter = scannedDeviceAdapter

        // Scanned Device item click listener
        scanningDeviceListView.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            scannedDeviceAdapter?.getItem(position)?.let { item ->

                val intent = Intent(this@DeviceScanActivity, DeviceControlActivity::class.java)
                    .putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, item.displayName)
                    .putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, item.deviceMac)

                if (mScanning) {
                    mBluetoothLeScanner?.stopScan(mLeScanCallback)
                    mScanning = false
                }

                startActivity(intent)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        with(menu) {
            findItem(R.id.menu_stop).isVisible = mScanning
            findItem(R.id.menu_scan).isVisible = !mScanning

            when {
                !mScanning -> findItem(R.id.menu_refresh).actionView = null
                else -> findItem(R.id.menu_refresh)
                    .setActionView(R.layout.actionbar_indeterminate_progress)
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> {
                scannedDeviceAdapter?.clear()
                scanLeDevice(true)
            }
            R.id.menu_stop -> scanLeDevice(false)
        }
        return true
    }

    override fun onResume() {
        super.onResume()

        // Ensures Bluetooth is enabled on the device. If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (mBluetoothAdapter?.isEnabled != true) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        scanLeDevice(true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {

        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            finish()
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPause() {
        super.onPause()
        scanLeDevice(false)
        scannedDeviceAdapter?.clear()
    }

    private fun scanLeDevice(enable: Boolean) {
        mScanning = enable

        if (enable) {

            // Stops scanning after a pre-defined scan period.
            mHandler?.postDelayed({
                mScanning = false
                mBluetoothLeScanner?.stopScan(mLeScanCallback)
                invalidateOptionsMenu()
            }, SCAN_PERIOD)

            mBluetoothLeScanner?.startScan(mLeScanCallback)
        } else {
            mBluetoothLeScanner?.stopScan(mLeScanCallback)
        }
        invalidateOptionsMenu()
    }

    // Device scan callback.
    private val mLeScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            runOnUiThread {
                scannedDeviceAdapter?.update(result.device, result.rssi)
                scannedDeviceAdapter?.notifyDataSetChanged()
            }
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1

        // Stops scanning after 10 seconds.
        private const val SCAN_PERIOD: Long = 10000
    }
}