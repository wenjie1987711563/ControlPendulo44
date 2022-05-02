package com.example.controlpendulo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


class MainActivity : AppCompatActivity() {
    private var deviceName: String? = null
    private var deviceAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI Initialization
        val buttonConnect = findViewById<Button>(R.id.buttonConnect)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.GONE
        val textViewInfo = findViewById<TextView>(R.id.textViewInfo)
        val buttonEmpezar = findViewById<Button>(R.id.buttonEmpezar)
        buttonEmpezar.isEnabled = false
        val buttonDetener = findViewById<Button>(R.id.buttonDetener)
        buttonDetener.isEnabled = false
        val buttonAleatorio = findViewById<Button>(R.id.buttonAleatorio)
        buttonAleatorio.isEnabled = false
        val buttonPerpetuo = findViewById<Button>(R.id.buttonPerpetuo)
        buttonPerpetuo.isEnabled = false

        var valAleatorio = false
        var valPerpetuo = false

        // If a bluetooth device has been selected from SelectDeviceActivity
        deviceName = intent.getStringExtra("deviceName")
        if (deviceName != null) {
            // Get the device address to make BT Connection
            deviceAddress = intent.getStringExtra("deviceAddress")
            // Show progress and connection status
            toolbar.subtitle = "Conectando a $deviceName..."
            progressBar.visibility = View.VISIBLE
            buttonConnect.isEnabled = false

            /*
            This is the most important piece of code. When "deviceName" is found
            the code will call a new thread to create a bluetooth connection to the
            selected device (see the thread code below)
             */
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            createConnectThread = CreateConnectThread(bluetoothAdapter, deviceAddress)
            createConnectThread!!.start()
        }

        /*
        Second most important piece of Code. GUI Handler
         */handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    CONNECTING_STATUS -> when (msg.arg1) {
                        1 -> {
                            toolbar.subtitle = "Conectado a $deviceName"
                            progressBar.visibility = View.GONE
                            buttonConnect.isEnabled = true
                            buttonEmpezar.isEnabled = true
                            buttonDetener.isEnabled = true
                            buttonAleatorio.isEnabled = true
                            buttonPerpetuo.isEnabled = true
                        }
                        -1 -> {
                            toolbar.subtitle = "Error al conectarse a $deviceName"
                            progressBar.visibility = View.GONE
                            buttonConnect.isEnabled = true
                        }
                    }

                }
            }
        }

        // Select Bluetooth Device
        buttonConnect.setOnClickListener { // Move to adapter list
            val intent = Intent(this@MainActivity, SelectDeviceActivity::class.java)
            startActivity(intent)
        }

        // Button that when pressed, sets itself to Green and make cmdText to A, then sets itself to default color
        buttonEmpezar.setOnClickListener {
            var cmdText: String? = null
            buttonEmpezar.setBackgroundColor(Color.GREEN)
            cmdText = "A"
            Thread.sleep(500)
            buttonEmpezar.setBackgroundColor(Color.parseColor("#673AB7"))
            // Send command to Arduino board
            connectedThread!!.write(cmdText)
        }

        buttonDetener.setOnClickListener {
            var cmdText: String? = null
            buttonDetener.setBackgroundColor(Color.GREEN)
            cmdText = "B"
            valAleatorio = false
            valPerpetuo = false
            Thread.sleep(500)
            buttonDetener.setBackgroundColor(Color.parseColor("#673AB7"))

            connectedThread!!.write(cmdText)
        }

        buttonAleatorio.setOnClickListener {
            var cmdText : String? = null
            valAleatorio = true
            valPerpetuo = false
            cmdText = "C"
            if (valAleatorio == true){
                buttonAleatorio.setBackgroundColor(Color.GREEN)
            } else {
                buttonAleatorio.setBackgroundColor(Color.parseColor("#673AB7"))
            }
            connectedThread!!.write(cmdText)
        }

        buttonPerpetuo.setOnClickListener {
            var cmdText : String? = null
            valPerpetuo = true
            valAleatorio = false
            cmdText = "D"
            if (valPerpetuo == true){
                buttonPerpetuo.setBackgroundColor(Color.GREEN)
            } else {
                buttonPerpetuo.setBackgroundColor(Color.parseColor("#673AB7"))
            }
            connectedThread!!.write(cmdText)
        }
    }

    /* ============================ Thread to Create Bluetooth Connection =================================== */
    class CreateConnectThread(bluetoothAdapter: BluetoothAdapter, address: String?) : Thread() {
        init {
            /*
            Use a temporary object that is later assigned to mmSocket
            because mmSocket is final.
             */
            val bluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
            var tmp: BluetoothSocket? = null
            val uuid = bluetoothDevice.uuids[0].uuid
            try {
                /*
                Get a BluetoothSocket to connect with the given BluetoothDevice.
                Due to Android device varieties,the method below may not work fo different devices.
                You should try using other methods i.e. :
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                 */
                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid)
            } catch (e: IOException) {
                Log.e(ContentValues.TAG, "Socket's create() method failed", e)
            }
            mmSocket = tmp
        }

        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            bluetoothAdapter.cancelDiscovery()
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket!!.connect()
                Log.e("Status", "Device connected")
                handler!!.obtainMessage(CONNECTING_STATUS, 1, -1).sendToTarget()
            } catch (connectException: IOException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket!!.close()
                    Log.e("Status", "Cannot connect to device")
                    handler!!.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget()
                } catch (closeException: IOException) {
                    Log.e(ContentValues.TAG, "Could not close the client socket", closeException)
                }
                return
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            connectedThread = ConnectedThread(mmSocket)
            connectedThread!!.run()
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(ContentValues.TAG, "Could not close the client socket", e)
            }
        }
    }

    /* =============================== Thread for Data Transfer =========================================== */
    class ConnectedThread(private val mmSocket: BluetoothSocket?) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = mmSocket!!.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024) // buffer store for the stream
            var bytes = 0 // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                    Read from the InputStream from Arduino until termination character is reached.
                    Then send the whole String message to GUI Handler.
                     */
                    buffer[bytes] = mmInStream!!.read().toByte()
                    var readMessage: String
                    if (buffer[bytes] == '\n'.code.toByte()) {
                        readMessage = String(buffer, 0, bytes)
                        Log.e("Arduino Message", readMessage)
                        handler!!.obtainMessage(MESSAGE_READ, readMessage).sendToTarget()
                        bytes = 0
                    } else {
                        bytes++
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    break
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        fun write(input: String?) {
            val bytes = input!!.toByteArray() //converts entered String into bytes
            try {
                mmOutStream!!.write(bytes)
            } catch (e: IOException) {
                Log.e("Send Error", "Unable to send message", e)
            }
        }

        /* Call this from the main activity to shutdown the connection */
        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
            }
        }
    }

    /* ============================ Terminate Connection at BackPress ====================== */
    override fun onBackPressed() {
        // Terminate Bluetooth Connection and close app
        if (createConnectThread != null) {
            createConnectThread!!.cancel()
        }
        val a = Intent(Intent.ACTION_MAIN)
        a.addCategory(Intent.CATEGORY_HOME)
        a.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(a)
    }

    companion object {
        var handler: Handler? = null
        var mmSocket: BluetoothSocket? = null
        var connectedThread: ConnectedThread? = null
        var createConnectThread: CreateConnectThread? = null
        private const val CONNECTING_STATUS = 1 // used in bluetooth handler to identify message status
        private const val MESSAGE_READ = 2 // used in bluetooth handler to identify message update
    }
}