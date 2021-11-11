/*
  @author Rajiv M.
 * @copyright Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vsnconnect.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vsnmobil.vsnconnect.Constants
import com.vsnmobil.vsnconnect.DeviceControlActivity
import com.vsnmobil.vsnconnect.R
import com.vsnmobil.vsnconnect.executor.ProcessQueueExecutor
import com.vsnmobil.vsnconnect.executor.ReadWriteCharacteristic
import com.vsnmobil.vsnconnect.utils.LogUtils.LOGI
import com.vsnmobil.vsnconnect.utils.LogUtils.makeLogTag
import java.util.*

/**
 * BluetoothLeService.java
 *
 * The communication between the Bluetooth Low Energy device will be communicated through this
 * service class only. The initial connect request and disconnect request will be executed in this
 * class. Also, all the status from the Bluetooth device will be notified in the corresponding
 * callback methods.
 */
class BluetoothLeService : Service() {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    var mCharIdentify: BluetoothGattCharacteristic? = null
    var mCharBlock: BluetoothGattCharacteristic? = null
    var mCharVerification: BluetoothGattCharacteristic? = null

    private var processQueueExecutor = ProcessQueueExecutor()

    var bluetoothGattMap: HashMap<String, BluetoothGatt>? = null

    override fun onCreate() {
        super.onCreate()

        // If blue tooth adapter is not initialized stop the service.
        if (!isBluetoothEnabled(this)) {
            stopForeground(false)
            stopSelf()
        }

        // To add and maintain the BluetoothGatt object of each BLE device.
        bluetoothGattMap = HashMap()

        // To execute the read and write operation in a queue.
        if (!processQueueExecutor.isAlive) {
            processQueueExecutor.start()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        // If blue tooth adapter is not initialized stop the service.
        if (!isBluetoothEnabled(this)) {
            stopForeground(false)
            stopSelf()
        }

        /* Creates an explicit intent for an Activity in your app */
        val resultIntent = Intent(this, DeviceControlActivity::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val notificationCode = System.currentTimeMillis().toInt()
        val resultPendingIntent = PendingIntent.getActivity(
            this,
            notificationCode,
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Invoking the default notification service
        val notifyBuilder = NotificationCompat.Builder(this)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_launcher)
            .setAutoCancel(false)
            .setContentIntent(resultPendingIntent)

        notifyBuilder.priority = Notification.PRIORITY_MIN

        val notification = notifyBuilder.build()

        // To keep running the service always in background.
        startForeground(notificationCode, notification)

        return START_STICKY
    }

    override fun onDestroy() {

        // To stop the foreground service.
        stopForeground(false)

        // Stop the read / write operation queue.
        processQueueExecutor.interrupt()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Manage the BLE service
     */
    private val binder: IBinder = LocalBinder()

    //Local binder to bind the service and communicate with this BluetoothLeService class.
    inner class LocalBinder : Binder() {
        val service: BluetoothLeService
            get() = this@BluetoothLeService
    }

    override fun onUnbind(intent: Intent): Boolean {
        // In this particular example,close() is invoked when the UI is disconnected from the Service.
        return super.onUnbind(intent)
    }

    override fun onBind(arg0: Intent): IBinder = binder

    /**
     * Initializes a reference to the local Blue tooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    fun initialize(): Boolean {
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        return bluetoothManager != null && bluetoothAdapter != null
    }

    /**
     * To read the value from the BLE Device
     *
     * @param mGatt          BluetoothGatt object of the device.
     * @param characteristic BluetoothGattCharacteristic of the device.
     */
    fun readCharacteristic(mGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {

        if (!checkConnectionState(mGatt)) {
            return
        }

        val readWriteCharacteristic = ReadWriteCharacteristic(
            ProcessQueueExecutor.REQUEST_TYPE_READ_CHAR,
            mGatt,
            characteristic
        )

        ProcessQueueExecutor.addProcess(readWriteCharacteristic)
    }

    /**
     * To write the value to BLE Device
     *
     * @param mGatt          BluetoothGatt object of the device.
     * @param characteristic BluetoothGattCharacteristic of the device.
     * @param b              byte value to write on to the BLE device.
     */
    private fun writeCharacteristic(
        mGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        b: ByteArray?
    ) {

        if (!checkConnectionState(mGatt)) {
            return
        }

        characteristic.value = b

        val readWriteCharacteristic = ReadWriteCharacteristic(
            ProcessQueueExecutor.REQUEST_TYPE_WRITE_CHAR,
            mGatt,
            characteristic
        )

        ProcessQueueExecutor.addProcess(readWriteCharacteristic)
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param mGatt Characteristic to act on.
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification. False otherwise.
     */
    fun setCharacteristicNotification(
        mGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean
    ) {

        if (!checkConnectionState(mGatt)) {
            return
        }

        if (!mGatt.setCharacteristicNotification(characteristic, enabled)) {
            return
        }

        val clientConfig = characteristic.getDescriptor(Constants.CLIENT_CHARACTERISTIC_CONFIG)
            ?: return

        clientConfig.value = when {
            enabled -> Constants.ENABLE_NOTIFICATION_VALUE
            else -> Constants.DISABLE_NOTIFICATION_VALUE
        }

        val readWriteCharacteristic = ReadWriteCharacteristic(
            ProcessQueueExecutor.REQUEST_TYPE_WRITE_DESCRIPTOR,
            mGatt,
            clientConfig
        )

        ProcessQueueExecutor.addProcess(readWriteCharacteristic)
    }

    /**
     * Connects to the GATT server hosted on the Blue tooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result is reported asynchronously through the
     * `BluetoothGattCallback# onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)` callback.
     */
    fun connect(address: String?): Boolean {

        if (bluetoothAdapter == null || address == null) {
            return false
        }

        bluetoothGattMap?.get(address)?.apply {
            disconnect()
            close()
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
            ?: return false

        return when (bluetoothManager?.getConnectionState(device, BluetoothProfile.GATT)) {
            BluetoothProfile.STATE_DISCONNECTED -> {

                // We want to directly connect to the device, so we are setting the
                // autoConnect parameter to false.
                val mBluetoothGatt = device.connectGatt(this, false, mGattCallbacks)

                // Add the each BluetoothGatt in to an array list.
                bluetoothGattMap?.remove(address)
                bluetoothGattMap?.put(address, mBluetoothGatt)
                true
            }
            else -> false
        }
    }

    /**
     * To disconnect the connected Blue tooth Low energy Device from the APP.
     *
     * @param gatt BluetoothGatt pass the GATT object of the device which need to be disconnect.
     */
    fun disconnect(gatt: BluetoothGatt?) {
        try {
            gatt?.apply {
                bluetoothGattMap?.remove(gatt.device.address)
                disconnect()
                close()
            }
        } catch (e: Exception) {
            LOGI(TAG, e.message)
        }
    }

    /**
     * To check the connection status of the GATT object.
     *
     * @param gatt BluetoothGatt pass the GATT object of the device.
     * @return If connected it will return true else false.
     */
    private fun checkConnectionState(gatt: BluetoothGatt): Boolean {

        if (bluetoothAdapter == null) {
            return false
        }

        return bluetoothManager?.getConnectionState(
            bluetoothAdapter?.getRemoteDevice(gatt.device.address),
            BluetoothProfile.GATT
        ) == BluetoothProfile.STATE_CONNECTED
    }

    /**
     * To check the connection status of the GATT object.
     *
     * @param deviceAddress String MAC address of the device
     * @return If connected it will return true else false.
     */
    fun checkConnectionState(deviceAddress: String?): Boolean {

        if (bluetoothAdapter == null) {
            return false
        }

        return bluetoothManager?.getConnectionState(
            bluetoothAdapter?.getRemoteDevice(deviceAddress),
            BluetoothProfile.GATT
        ) == BluetoothProfile.STATE_CONNECTED
    }

    // The connection status of the Blue tooth Low energy Device will be
    // notified in the below callback.
    private val mGattCallbacks: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                when (newState) {
                    // Start service discovery.
                    BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> gatt.apply {
                        try {
                            device.address
                            bluetoothGattMap?.remove(device.address)
                            disconnect()
                            close()
                        } catch (e: Exception) {
                            LOGI(TAG, e.message)
                        }

                        broadcastUpdate(ACTION_GATT_DISCONNECTED, device.address, status)
                    }
                }
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {

            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {

                    broadcastUpdate(ACTION_GATT_CONNECTED, gatt.device.address, status)

                    // Do APP verification as soon as service discovered.
                    try {
                        appVerification(
                            gatt,
                            getGattChar(
                                gatt,
                                Constants.SERVICE_VSN_SIMPLE_SERVICE,
                                Constants.CHAR_APP_VERIFICATION
                            ),
                            Constants.NEW_APP_VERIFICATION_VALUE
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "exception with app verify:" + e.message)
                    }

                    for (service in gatt.services) {

                        if (service == null || service.uuid == null) {
                            continue
                        }

                        when (service.uuid) {
                            Constants.SERVICE_VSN_SIMPLE_SERVICE -> {
                                mCharVerification =
                                    service.getCharacteristic(Constants.CHAR_APP_VERIFICATION)

                                // Write Emergency key press
                                enableForDetect(
                                    gatt,
                                    service.getCharacteristic(Constants.CHAR_DETECTION_CONFIG),
                                    Constants.ENABLE_KEY_DETECTION_VALUE
                                )

                                // Set notification for emergency key press and fall detection
                                setCharacteristicNotification(
                                    gatt,
                                    service.getCharacteristic(Constants.CHAR_DETECTION_NOTIFY),
                                    true
                                )
                            }
                            // Read the device battery percentage
                            Constants.SERVICE_BATTERY_LEVEL -> readCharacteristic(
                                gatt,
                                service.getCharacteristic(Constants.CHAR_BATTERY_LEVEL)
                            )
                        }
                    }
                }
                else -> {
                    // Service discovery failed close and disconnect the GATT object of the device.
                    gatt.disconnect()
                    gatt.close()
                }
            }
        }

        // CallBack when the response available for registered the notification( Battery Status, Fall Detect, Key Press)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {

            val keyValue =
                characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toString()
            when (characteristic.uuid) {
                Constants.CHAR_DETECTION_NOTIFY -> {

                    val pressValue: String = when (keyValue) {
                        "1" -> "single press"
                        "0" -> "single release"
                        "3" -> "2-10 second press release"
                        "4" -> "fallevent"
                        "5" -> "high g event"
                        else -> keyValue
                    }

                    broadcastUpdate(ACTION_DATA_RESPONSE, "fffffff4=$pressValue", "")
                }
                else -> broadcastUpdate(
                    ACTION_DATA_RESPONSE,
                    characteristic.uuid.toString() + "=" + keyValue,
                    ""
                )
            }

            if (keyValue.equals("1", ignoreCase = true)) {
                val intent = Intent("com.android.music.musicservicecommand.togglepause")
                    .putExtra("command", "togglepause")
                sendBroadcast(intent)
            }
        }

        // Callback when the response available for Read Characteristic Request
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                // Display received battery value.
                when (characteristic.uuid) {
                    Constants.CHAR_BATTERY_LEVEL -> {
                        val batteryValue =
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                                .toString()
                        broadcastUpdate(
                            ACTION_DATA_RESPONSE,
                            "Battery level = $batteryValue",
                            status
                        )
                    }
                    else -> Log.i(
                        TAG,
                        "received characteristic read:" + characteristic.uuid.toString()
                    )
                }
            }
        }

        // Callback when the response available for Write Characteristic Request
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            broadcastUpdate(ACTION_DATA_RESPONSE, characteristic.uuid.toString(), status)
        }

        // Callback when the response available for Read Descriptor Request
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.i(TAG, "received descriptor read:" + descriptor.uuid.toString())
        }

        // Callback when the response available for Write Descriptor Request
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            broadcastUpdate(ACTION_DATA_RESPONSE, "enabled key press event", status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val rssiValue = rssi.toString()
                Log.d(TAG, rssiValue)
            }
        }
    }

    /**
     * To write the value to BLE Device for APP verification
     *
     * @param mGatt BluetoothGatt object of the device.
     * @param ch    BluetoothGattCharacteristic of the device.
     */
    fun appVerification(mGatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray?) {
        writeCharacteristic(mGatt, ch, value)
    }

    /**
     * To write the value to BLE Device for Emergency / Fall alert
     *
     * @param mGatt BluetoothGatt object of the device.
     * @param ch    BluetoothGattCharacteristic of the device.
     * @param value byte value to write on to the BLE device.
     */
    fun enableForDetect(mGatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray?) {
        writeCharacteristic(mGatt, ch, value)
    }

    /**
     * To get the characteristic of the corresponding BluetoothGatt object and
     * service UUID and Characteristic UUID.
     *
     * @param mGatt             BluetoothGatt  object of the device.
     * @param serviceuuid       Service        UUID.
     * @param charectersticuuid Characteristic UUID.
     * @return BluetoothGattCharacteristic of the given service and Characteristic UUID.
     */
    fun getGattChar(
        mGatt: BluetoothGatt,
        serviceuuid: UUID?,
        charectersticuuid: UUID?
    ): BluetoothGattCharacteristic {
        val gattService = mGatt.getService(serviceuuid)
        return gattService.getCharacteristic(charectersticuuid)
    }

    /**
     * To get the List of BluetoothGattCharacteristic from the given GATT object for Service UUID
     *
     * @param mGatt       BluetoothGatt object of the device.
     * @param serviceuuid Service UUID.
     * @return List of BluetoothGattCharacteristic.
     */
    fun getGattCharList(
        mGatt: BluetoothGatt,
        serviceuuid: UUID?
    ): List<BluetoothGattCharacteristic> {
        val gattService = mGatt.getService(serviceuuid)
        return gattService.characteristics
    }

    /**
     * To get the BluetoothGatt of the corresponding device
     *
     * @param bGattkey String key value of hash map.
     * @return BluetoothGatt of the device from the array
     */
    fun getGatt(bGattkey: String): BluetoothGatt? {
        return bluetoothGattMap?.get(bGattkey)
    }

    /**
     * Broadcast the values to the UI if the application is in foreground.
     *
     * @param action  String intent action.
     * @param value   String value to update to the receiver.
     * @param address String address.
     */
    private fun broadcastUpdate(action: String, value: String, address: String) {

        val intent = Intent(action)
            .putExtra(EXTRA_DATA, value)
            .putExtra(EXTRA_ADDRESS, address)

        sendBroadcast(intent)
    }

    /**
     * Broadcast the values to the UI if the application is in foreground.
     *
     * @param action  String intent action.
     * @param address String address of the device.
     * @param status  int connection status of the device.
     */
    fun broadcastUpdate(action: String?, address: String?, status: Int) {

        val intent = Intent(action)
            .putExtra(EXTRA_DATA, address)
            .putExtra(EXTRA_STATUS, status)

        sendBroadcast(intent)
    }

    companion object {

        // Constants going to use in the broadcast receiver as intent action.
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.vsnmobil.vsnconnect.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_GATT_CONNECTED = "com.vsnmobil.vsnconnect.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.vsnmobil.vsnconnect.ACTION_GATT_DISCONNECTED"
        const val ACTION_DATA_RESPONSE = "com.vsnmobil.vsnconnect.ACTION_DATA_RESPONSE"
        const val EXTRA_DATA = "com.vsnmobil.vsnconnect.EXTRA_DATA"
        const val EXTRA_STATUS = "com.vsnmobil.vsnconnect.EXTRA_STATUS"
        const val EXTRA_ADDRESS = "com.vsnmobil.vsnconnect.EXTRA_ADDRESS"

        private val TAG = makeLogTag(BluetoothLeService::class.java)

        /**
         * To check the device bluetooth is enabled or not.
         *
         * @param context Context pass the context of your activity.
         * @return boolean Bluetooth is enabled / disabled.
         */
        fun isBluetoothEnabled(context: Context): Boolean {
            val bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            return bluetoothAdapter.isEnabled
        }
    }
}
