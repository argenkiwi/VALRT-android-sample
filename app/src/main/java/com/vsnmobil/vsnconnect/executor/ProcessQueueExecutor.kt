/*
 *  @author Rajiv M.
 * @copyright Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vsnconnect.executor

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import java.util.*

/**
 * ProcessQueueExecutor.java
 *
 * This class is used to execute the read,write and write descriptor request one by one
 * in 1.1 seconds delay.
 *
 */
class ProcessQueueExecutor : Thread() {

    private var processQueueTimer: Timer? = Timer()

    fun executeProcess() {
        if (processList.isNotEmpty()) {
            val readWriteCharacteristic = processList[0]
            val type = readWriteCharacteristic.requestType
            val bluetoothGatt = readWriteCharacteristic.bluetoothGatt
            val parseObject = readWriteCharacteristic.`object`
            when (type) {
                REQUEST_TYPE_READ_CHAR -> try {
                    val characteristic = parseObject as BluetoothGattCharacteristic
                    bluetoothGatt.readCharacteristic(characteristic)
                } catch (e: Exception) {
                    Log.e(TAG, "bluetooth exception:" + e.message)
                }
                REQUEST_TYPE_WRITE_CHAR -> try {
                    val characteristic = parseObject as BluetoothGattCharacteristic
                    bluetoothGatt.writeCharacteristic(characteristic)
                } catch (e: Exception) {
                    Log.e(TAG, "bluetooth exception:" + e.message)
                }
                REQUEST_TYPE_WRITE_DESCRIPTOR -> try {
                    val clientConfig = parseObject as BluetoothGattDescriptor
                    bluetoothGatt.writeDescriptor(clientConfig)
                } catch (e: Exception) {
                    Log.e(TAG, "bluetooth exception:" + e.message)
                }
            }
            removeProcess(readWriteCharacteristic)
        }
    }

    override fun interrupt() {
        super.interrupt()
        processQueueTimer?.cancel()
    }

    override fun run() {
        super.run()
        processQueueTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                executeProcess()
            }
        }, 0, EXECUTE_DELAY)
    }

    companion object {
        private val TAG = ProcessQueueExecutor::class.java.simpleName
        const val REQUEST_TYPE_READ_CHAR = 1
        const val REQUEST_TYPE_WRITE_CHAR = 2
        const val REQUEST_TYPE_WRITE_DESCRIPTOR = 3
        const val EXECUTE_DELAY: Long = 1100 // delay in execution
        private val processList: MutableList<ReadWriteCharacteristic> = ArrayList()

        /**
         * Adds the request to ProcessQueueExecutor
         * @param readWriteCharacteristic
         */
        fun addProcess(readWriteCharacteristic: ReadWriteCharacteristic) {
            processList.add(readWriteCharacteristic)
        }

        /**
         * Removes the request from ProcessQueueExecutor
         * @param readWriteCharacteristic
         */
        fun removeProcess(readWriteCharacteristic: ReadWriteCharacteristic) {
            processList.remove(readWriteCharacteristic)
        }
    }
}