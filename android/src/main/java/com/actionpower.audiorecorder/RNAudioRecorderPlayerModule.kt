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

class RNAudioRecorderModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), PermissionListener, AudioManager.OnAudioFocusChangeListener {
    private var audioFileURL = ""
    private var subsDurationMillis = 500
    private var _meteringEnabled = false
    private var mediaRecorder: MediaRecorder? = null
    private var recorderRunnable: Runnable? = null
    private var pausedRecordTime = 0L
    private var totalPausedRecordTime = 0L
    private var isPausedByUser = false
    private var isPausedByInterrupt = false
    private var isInterrupted = false
    var recordHandler: Handler? = Handler(Looper.getMainLooper())

    override fun getName(): String {
        return tag
    }

    private fun sendEvent(reactContext: ReactContext,
                          eventName: String,
                          params: WritableMap?) {
        reactContext
                .getJSModule<RCTDeviceEventEmitter>(RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
    }

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

    @ReactMethod
    fun startRecorder(path: String, audioSet: ReadableMap?, meteringEnabled: Boolean, promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // TIRAMISU (33)
                // https://github.com/hyochan/react-native-audio-recorder-player/issues/503
                if (Build.VERSION.SDK_INT < 33 &&
                        (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                                ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED))  {
                    ActivityCompat.requestPermissions((currentActivity)!!, arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                    promise.reject("No permission granted.", "Try again after adding permission.")
                    return
                } else if (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((currentActivity)!!, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
                    promise.reject("No permission granted.", "Try again after adding permission.")
                    return
                }
            }
        } catch (ne: NullPointerException) {
            Log.w(tag, ne.toString())
            promise.reject("No permission granted.", "Try again after adding permission.")
            return
        }
        audioFileURL = if (((path == "DEFAULT"))) "${reactContext.cacheDir}/$defaultFileName" else path
        _meteringEnabled = meteringEnabled

        if (mediaRecorder == null) {
            mediaRecorder = MediaRecorder()
        }

        if (audioSet != null) {
            mediaRecorder!!.setAudioSource(if (audioSet.hasKey("AudioSourceAndroid")) audioSet.getInt("AudioSourceAndroid") else MediaRecorder.AudioSource.MIC)
            mediaRecorder!!.setOutputFormat(if (audioSet.hasKey("OutputFormatAndroid")) audioSet.getInt("OutputFormatAndroid") else MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder!!.setAudioEncoder(if (audioSet.hasKey("AudioEncoderAndroid")) audioSet.getInt("AudioEncoderAndroid") else MediaRecorder.AudioEncoder.AAC)
            mediaRecorder!!.setAudioSamplingRate(if (audioSet.hasKey("AudioSamplingRateAndroid")) audioSet.getInt("AudioSamplingRateAndroid") else 48000)
            mediaRecorder!!.setAudioEncodingBitRate(if (audioSet.hasKey("AudioEncodingBitRateAndroid")) audioSet.getInt("AudioEncodingBitRateAndroid") else 128000)
        } else {
            mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder!!.setAudioEncodingBitRate(128000)
            mediaRecorder!!.setAudioSamplingRate(48000)
        }
        mediaRecorder!!.setOutputFile(audioFileURL)

        try {
            mediaRecorder!!.prepare()
            totalPausedRecordTime = 0L
            mediaRecorder!!.start()
            requestAudioFocus()

            val serviceIntent = Intent(reactContext, ForegroundService::class.java)
            serviceIntent.putExtra(ForegroundService.STOPWATCH_ACTION, ForegroundService.START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                reactContext.startForegroundService(serviceIntent)
            }else{
                reactContext.startService(serviceIntent)
            }

            val systemTime = SystemClock.elapsedRealtime()
            recorderRunnable = object : Runnable {
                override fun run() {
                    val time = SystemClock.elapsedRealtime() - systemTime - totalPausedRecordTime
                    val obj = Arguments.createMap()
                    obj.putDouble("currentPosition", time.toDouble())
                    if (_meteringEnabled) {
                        var maxAmplitude = 0
                        if (mediaRecorder != null) {
                            maxAmplitude = mediaRecorder!!.maxAmplitude
                        }
                        var dB = -160.0
                        val maxAudioSize = 32767.0
                        if (maxAmplitude > 0) {
                            dB = 20 * log10(maxAmplitude / maxAudioSize)
                        }
                        obj.putInt("currentMetering", dB.toInt())
                    }
                    sendEvent(reactContext, "rn-recordback", obj)
                    recordHandler!!.postDelayed(this, subsDurationMillis.toLong())
                }
            }
            (recorderRunnable as Runnable).run()

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
            mediaRecorder!!.pause()
            isPausedByUser = true
            pausedRecordTime = SystemClock.elapsedRealtime();
            recorderRunnable?.let { recordHandler!!.removeCallbacks(it) };
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

        if (isInterrupted) {
            promise?.reject("resumeReocrder", "Recorder is null.")
            return
        }

        try {
            mediaRecorder!!.resume()
            isPausedByUser = false
            totalPausedRecordTime += SystemClock.elapsedRealtime() - pausedRecordTime;
            recorderRunnable?.let { recordHandler!!.postDelayed(it, subsDurationMillis.toLong()) }
            promise?.resolve("Recorder resumed.")

        } catch (e: Exception) {
            Log.e(tag, "Recorder resume: " + e.message)
            promise?.reject("resumeRecorder", e.message)
        }
    }

    @ReactMethod
    fun stopRecorder(promise: Promise) {
        if (recordHandler != null) {
            recorderRunnable?.let { recordHandler!!.removeCallbacks(it) }
        }

        if (mediaRecorder == null) {
            promise.reject("stopRecord", "recorder is null.")
            return
        }

        try {
            mediaRecorder!!.stop()
            abandonAudioFocus()
        } catch (stopException: RuntimeException) {
            stopException.message?.let { Log.d(tag,"" + it) }
            promise.reject("stopRecord", stopException.message)
        }

        mediaRecorder!!.release()
        mediaRecorder = null
        val serviceIntent = Intent(reactContext, ForegroundService::class.java)
        reactContext.stopService(serviceIntent)
        promise.resolve("file:///$audioFileURL")
    }

    @ReactMethod
    fun setSubscriptionDuration(sec: Double, promise: Promise) {
        subsDurationMillis = (sec * 1000).toInt()
        promise.resolve("setSubscriptionDuration: $subsDurationMillis")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        var requestRecordAudioPermission: Int = 200

        when (requestCode) {
            requestRecordAudioPermission -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) return true
        }

        return false
    }

    companion object {
        private var tag = "RNAudioRecorder"
        private var defaultFileName = "sound.mp4"
    }
}