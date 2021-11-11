/*
 * @author Rajiv M.
 * @copyright Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vsnconnect.executor

import android.bluetooth.BluetoothGatt

/**
 * ReadWriteCharacteristic.java
 * Model class that provides details about RequestType, BluetoothGatt object and
 * Object
 */
data class ReadWriteCharacteristic(
    var requestType: Int,
    var bluetoothGatt: BluetoothGatt,
    var `object`: Any
)
