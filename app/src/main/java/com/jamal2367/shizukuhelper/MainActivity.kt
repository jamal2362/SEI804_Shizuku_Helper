package com.jamal2367.shizukuhelper

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.jamal2367.shizukuhelper.MainActivity.ThreadUtil.runOnUiThread
import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import com.tananaev.adblib.AdbStream
import java.io.File
import java.lang.ref.WeakReference
import java.net.Socket


class MainActivity : AccessibilityService() {

    private var connection: AdbConnection? = null
    private var stream: AdbStream? = null
    private var myAsyncTask: MyAsyncTask? = null
    private val ipAddress = "0.0.0.0"
    private val publicKeyName: String = "public.key"
    private val privateKeyName: String = "private.key"

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("TAG", "onServiceConnected")

        if (!isUsbDebuggingEnabled()) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.enable_usb_debugging_first), Toast.LENGTH_LONG).show()
            }
        }

        onKeyCE()
    }

    private fun onKeyCE() {
        connection = null
        stream = null

        myAsyncTask?.cancel()
        myAsyncTask = MyAsyncTask(this)
        myAsyncTask?.execute(ipAddress)
    }

    fun adbCommander(ip: String?) {
        val socket = Socket(ip, 5555)
        val crypto = readCryptoConfig(filesDir) ?: writeNewCryptoConfig(filesDir)

        if (crypto == null) {
            runOnUiThread {
                Toast.makeText(this, "Failed to generate/load RSA key pair", Toast.LENGTH_LONG).show()
            }
            return
        }

        try {
            if (stream == null || connection == null) {
                connection = AdbConnection.create(socket, crypto)
                connection?.connect()
            }

            startShizukuCommand()

        } catch (e: InterruptedException) {
            e.printStackTrace()
            Thread.currentThread().interrupt()
        }
    }

    private fun startShizukuCommand() {
        try {
            Thread.sleep(15000)
            connection?.open("shell:sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")

            runOnUiThread {
                Toast.makeText(baseContext, getString(R.string.shizuku_started), Toast.LENGTH_SHORT).show()
            }

            Log.d("TAG", getString(R.string.shizuku_started))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isUsbDebuggingEnabled(): Boolean {
        return Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    }

    private fun readCryptoConfig(dataDir: File?): AdbCrypto? {
        val pubKey = File(dataDir, publicKeyName)
        val privKey = File(dataDir, privateKeyName)

        var crypto: AdbCrypto? = null
        if (pubKey.exists() && privKey.exists()) {
            crypto = try {
                AdbCrypto.loadAdbKeyPair(AndroidBase64(), privKey, pubKey)
            } catch (e: Exception) {
                null
            }
        }

        return crypto
    }

    private fun writeNewCryptoConfig(dataDir: File?): AdbCrypto? {
        val pubKey = File(dataDir, publicKeyName)
        val privKey = File(dataDir, privateKeyName)

        var crypto: AdbCrypto?

        try {
            crypto = AdbCrypto.generateAdbKeyPair(AndroidBase64())
            crypto.saveAdbKeyPair(privKey, pubKey)
        } catch (e: Exception) {
            crypto = null
        }

        return crypto
    }

    class MyAsyncTask internal constructor(context: MainActivity) {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)
        private var thread: Thread? = null

        fun execute(ip: String?) {
            thread = Thread {
                val activity = activityReference.get()
                activity?.adbCommander(ip)

                if (Thread.interrupted()) {
                    return@Thread
                }
            }
            thread?.start()
        }

        fun cancel() {
            thread?.interrupt()
        }
    }

    class AndroidBase64 : AdbBase64 {
        override fun encodeToString(bArr: ByteArray): String {
            return Base64.encodeToString(bArr, Base64.NO_WRAP)
        }
    }

    object ThreadUtil {
        private val handler = Handler(Looper.getMainLooper())

        fun runOnUiThread(action: () -> Unit) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                handler.post(action)
            } else {
                action.invoke()
            }
        }
    }
}
