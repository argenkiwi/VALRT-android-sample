/*
 * @author Rajiv M.
 * @copyright Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vsnconnect.adapter

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.vsnmobil.vsnconnect.R

/**
 * ScannedDeviceAdapter.java
 *
 * This adapter is used to load the scanned puck in list view.
 */
class ScannedDeviceAdapter(
    context: Context,
    private val resId: Int,
    objects: MutableList<ScannedDevice>
) : ArrayAdapter<ScannedDevice>(context, resId, objects) {

    private val list: MutableList<ScannedDevice> = objects

    private val inflater: LayoutInflater = context
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View = (convertView ?: inflater.inflate(resId, null)).apply {
        val item = getItem(position)
        findViewById<TextView>(R.id.device_name).text = item?.displayName
        findViewById<TextView>(R.id.device_address).text = item?.device?.address
    }

    /**
     * Add or update BluetoothDevice.
     */
    fun update(newDevice: BluetoothDevice?, rssi: Int) {

        if (newDevice == null || newDevice.address == null) {
            return
        }

        var contains = false
        for (device in list) {
            if (newDevice.address == device.device.address) {
                contains = true
                device.rssi = rssi // update
                break
            }
        }

        if (!contains) {
            // add new BluetoothDevice into the adapter.
            list.add(ScannedDevice(newDevice, rssi))
        }

        // Refresh the list view.
        notifyDataSetChanged()
    }
}
