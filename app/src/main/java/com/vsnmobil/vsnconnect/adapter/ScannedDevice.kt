/*
 * @author Rajiv M.
 * @copyright Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vsnconnect.adapter

import android.bluetooth.BluetoothDevice

/**
 * ScannedDevice.java
 * Model class that provides details about device name, device MAC address and RSSI value.
 */
data class ScannedDevice(val device: BluetoothDevice, var rssi: Int) {

    val displayName: String
        get() = when {
            device.name.isNullOrBlank() -> UNKNOWN
            else -> device.name
        }

    val deviceMac: String
        get() = device.address

    companion object {
        private const val UNKNOWN = "Unknown"
    }
}
