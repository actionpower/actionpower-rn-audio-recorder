package com.actionpower.audiorecorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.media.AudioManager;
import androidx.core.app.ActivityCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.facebook.react.modules.core.PermissionListener
import kotlin.math.log10
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RNAudioRecorderModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), PermissionListener {
    //AudioManager.OnAudioFocusChangeListener

    private var audioFileURL = ""
    private var pausedRecordTime = 0L
    private var totalPausedRecordTime = 0L
    
    private var mediaRecorder: MediaRecorder? = null
    
    private var isPausedByUser = false
    //private var isPausedByInterrupt = false
    private var isInterrupted = false

    private var recordJob: Job? = null


    
    override fun getName(): String {
        return tag
    }

    private fun sendEvent(
        reactContext: ReactContext,
        eventName: String,
        params: WritableMap?
    ) {
        reactContext
                .getJSModule<RCTDeviceEventEmitter>(RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
    }
/*
    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isInterrupted = true
                if (!isPausedByUser) {
                    isPausedByInterrupt = true
                    pauseRecorder(null)
                    val obj = Arguments.createMap()
                    obj.putString("status", "paused")
                    sendEvent(reactContext, "rn-recordback", obj)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                isInterrupted = false
                if (isPausedByInterrupt) {
                    isPausedByInterrupt = false
                    resumeRecorder(null);
                    val obj = Arguments.createMap()
                    obj.putString("status", "resume")
                    sendEvent(reactContext, "rn-recordback", obj)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val audioManager = reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocus(this)
    }
*/
    private fun permissionCheck() : Boolean {
        try {
            val permission = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT < 33 &&
                        (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                                ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED))  {
                    ActivityCompat.requestPermissions((currentActivity)!!, permission, 0)
                    promise.reject("No permission granted.", "Try again after adding permission.")
                    return false
                } else if (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((currentActivity)!!, permission, 0)
                    promise.reject("No permission granted.", "Try again after adding permission.")
                    return false
                }
            }
        } catch (ne: NullPointerException) {
            Log.w(tag, ne.toString())
            promise.reject("No permission granted.", "Try again after adding permission.")
            return false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        var requestRecordAudioPermission: Int = 200

        when (requestCode) {
            requestRecordAudioPermission -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) return true
        }

        return false
    }


    private fun readyRecorder() {
        cancelAll()
        audioFileURL = if (((path == "DEFAULT"))) "${reactContext.cacheDir}/$defaultFileName" else path
        totalPausedRecordTime = 0L
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(48000)
            setOutputFile(audioFileURL)
        }
    }

    private fun cancelAll(promise: Promise?) {
        runCatching { 
            recordJob?.cancle()
            recordJob = null

            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        }.onFailure {
            promise?.reject("cancelAll", it.message)
        }
    }


    @ReactMethod
    fun startRecorder(path: String, audioSet: ReadableMap?, meteringEnabled: Boolean, promise: Promise) {
        
        if(!permissionCheck()) return
        if(mediaRecorder == null) readyRecorder()
        
        try {
            mediaRecorder?.prepare()            
            mediaRecorder?.start()

            val serviceIntent = Intent(reactContext, ForegroundService::class.java).apply {
                putExtra(ForegroundService.STOPWATCH_ACTION, ForegroundService.START)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                reactContext.startForegroundService(serviceIntent)
            }else{
                reactContext.startService(serviceIntent)
            }

            val systemTime = SystemClock.elapsedRealtime()
            recordJob = null
            recordJob = CoroutineScope(Dispatchers.Default).launchPeriodicAsync(500L) {
                runCatching {
                    val time = SystemClock.elapsedRealtime() - systemTime - totalPausedRecordTime
                    val obj = Arguments.createMap().apply {
                       putDouble("currentPosition", time.toDouble())     
                    }

                    val maxAmplitude = mediaRecorder?.maxAmplitude
                    val dB = if(maxAmplitude != null && maxAmplitude > 0) { dB = 20 * log10(maxAmplitude / 32767.0) } else -160

                    if(Build.VERSION.SDK_INT >= 29) {
                        val isSilenced = mediaRecorder?.activeRecordingConfiguration?.isClientSilenced
                        if(isSlienced) {
                            obj.putString("status", "paused")
                            sendEvent(reactContext, "rn-recordback", obj)
                        } else {
                            obj.putString("status", "resume")
                            obj.putInt("currentMetering", dB.toInt())
                            sendEvent(reactContext, "rn-recordback", obj)
                        }
                    } else {
                        obj.putInt("currentMetering", dB.toInt())
                        sendEvent(reactContext, "rn-recordback", obj)
                }
            }
            promise.resolve("file:///$audioFileURL")
        } catch (e: Exception) {
            Log.e(tag, "Exception: ", e)
            promise.reject("startRecord", e.message)
        }
    }

    @ReactMethod
    fun pauseRecorder(promise: Promise?) {
        if (mediaRecorder == null) {
            promise?.reject("pauseRecorder", "Recorder is null.")
            return
        }

        try {
            mediaRecorder?.pause()

            isPausedByUser = true
            pausedRecordTime = SystemClock.elapsedRealtime()
            
            promise?.resolve("Recorder paused.")
        } catch (e: Exception) {
            Log.e(tag, "pauseRecorder exception: " + e.message)
            promise?.reject("pauseRecorder", e.message)
        }
    }

    @ReactMethod
    fun resumeRecorder(promise: Promise?) {
        if (mediaRecorder == null) {
            promise?.reject("resumeReocrder", "Recorder is null.")
            return
        }

        /*if (isInterrupted) {
            promise?.reject("resumeReocrder", "Recorder is null.")
            return
        }*/

        try {
            mediaRecorder?.resume()

            isPausedByUser = false            
            totalPausedRecordTime += SystemClock.elapsedRealtime() - pausedRecordTime;
            
            promise?.resolve("Recorder resumed.")

        } catch (e: Exception) {
            Log.e(tag, "Recorder resume: " + e.message)
            promise?.reject("resumeRecorder", e.message)
        }
    }

    @ReactMethod
    fun stopRecorder(promise: Promise) {
        if (mediaRecorder == null) {
            cancelAll()
            promise.reject("stopRecord", "recorder is null.")
            return
        }

        try {
            mediaRecorder?.stop()
            cancelAll()
        } catch (stopException: RuntimeException) {
            stopException.message?.let { Log.d(tag,"" + it) }
            promise.reject("stopRecord", stopException.message)
        }

        val serviceIntent = Intent(reactContext, ForegroundService::class.java)
        reactContext.stopService(serviceIntent)
        promise.resolve("file:///$audioFileURL")
    }

    @ReactMethod
    fun setSubscriptionDuration(sec: Double, promise: Promise) {
        promise.resolve("setSubscriptionDuration: $subsDurationMillis")
    }

    companion object {
        private var tag = "RNAudioRecorder"
        private var defaultFileName = "sound.mp4"
    }
}

fun CoroutineScope.launchPeriodicAsync(repeatMillis: Long, action: () -> Unit) = this.async {
    while (isActive) {
        action()
        delay(repeatMillis)
    }
}
