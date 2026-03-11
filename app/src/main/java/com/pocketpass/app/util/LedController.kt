package com.pocketpass.app.util

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Controls the Ayn Thor joycon LEDs via the PServerBinder system service.
 * Writes color commands to sysfs paths for the SN3112 LED controllers.
 */
class LedController {

    private val pServerBinder: IBinder?

    companion object {
        private const val TAG = "LedController"
        private const val LEFT_LED_PATH = "/sys/class/sn3112l/led/brightness"
        private const val RIGHT_LED_PATH = "/sys/class/sn3112r/led/brightness"
    }

    init {
        pServerBinder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as? IBinder
        } catch (e: Exception) {
            Log.w(TAG, "PServerBinder not available (not on Ayn Thor?)", e)
            null
        }
    }

    val isAvailable: Boolean get() = pServerBinder != null

    fun setAllLeds(red: Int, green: Int, blue: Int, brightness: Int = 255) {
        val commands = listOf(
            "echo 1-$red:$green:$blue:$brightness > $LEFT_LED_PATH",
            "echo 2-$red:$green:$blue:$brightness > $LEFT_LED_PATH",
            "echo 1-$red:$green:$blue:$brightness > $RIGHT_LED_PATH",
            "echo 2-$red:$green:$blue:$brightness > $RIGHT_LED_PATH"
        )
        executeCommand(commands.joinToString(" && "))
    }

    fun turnOff() {
        setAllLeds(0, 0, 0, 0)
    }

    /**
     * Blinks all LEDs green a specified number of times.
     * Runs asynchronously on a coroutine.
     */
    fun blinkGreen(scope: CoroutineScope, times: Int = 3, onDurationMs: Long = 300, offDurationMs: Long = 200) {
        if (!isAvailable) return
        scope.launch(Dispatchers.IO) {
            repeat(times) {
                setAllLeds(0, 255, 0, 255)
                delay(onDurationMs)
                turnOff()
                if (it < times - 1) delay(offDurationMs)
            }
        }
    }

    private fun executeCommand(command: String) {
        pServerBinder?.let { binder ->
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeStringArray(arrayOf(command, "1"))
                binder.transact(0, data, reply, IBinder.FLAG_ONEWAY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute LED command", e)
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
