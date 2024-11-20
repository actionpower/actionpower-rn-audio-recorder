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

class RNAudioRecorderModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), PermissionListener {
    private var audioFileURL = ""
    private var subsDurationMillis = 500L
    private var pausedRecordTime = 0L
    private var totalPausedRecordTime = 0L
    private var isInterrupted = false
    private var isUserInterrupted = false
    private var amplitudeZeroCount = 0

    private var mediaRecorder: MediaRecorder? = null
    private var recorderRunnable: Runnable? = null
    private var audioManager: AudioManager? = null
    private var recordHandler: Handler = Handler(Looper.getMainLooper())

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

    @ReactMethod
    fun startRecorder(path: String, audioSet: ReadableMap?, meteringEnabled: Boolean, promise: Promise) {

        val permission = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // TIRAMISU (33)
                // https://github.com/hyochan/react-native-audio-recorder-player/issues/503
                if (Build.VERSION.SDK_INT < 33 &&
                    (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED))  {
                    ActivityCompat.requestPermissions((currentActivity)!!, permission, 0)
                    promise.reject("No permission granted.", "Try again after adding permission.")
                    return
                } else if (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((currentActivity)!!, permission, 0)
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

        mediaRecorder = MediaRecorder().apply {
            if(audioSet == null) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(16000)
            } else {
                setAudioSource(if (audioSet.hasKey("AudioSourceAndroid")) audioSet.getInt("AudioSourceAndroid") else MediaRecorder.AudioSource.MIC)
                setOutputFormat(if (audioSet.hasKey("OutputFormatAndroid")) audioSet.getInt("OutputFormatAndroid") else MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(if (audioSet.hasKey("AudioEncoderAndroid")) audioSet.getInt("AudioEncoderAndroid") else MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(if (audioSet.hasKey("AudioEncodingBitRateAndroid")) audioSet.getInt("AudioEncodingBitRateAndroid") else 128000)
                setAudioSamplingRate(if (audioSet.hasKey("AudioSamplingRateAndroid")) audioSet.getInt("AudioSamplingRateAndroid") else 16000)
                
                if (audioSet.hasKey("AudioChannelsAndroid")) {
                    setAudioChannels(audioSet.getInt("AudioChannelsAndroid"))
                }
            }
            setOutputFile(audioFileURL)
        }

        

        audioManager = reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            totalPausedRecordTime = 0L

            isUserInterrupted = false
            isInterrupted = false

            mediaRecorder?.prepare()
            mediaRecorder?.start()

            recordingTask()

            val serviceIntent = Intent(reactContext, ForegroundService::class.java)
            serviceIntent.putExtra(ForegroundService.STOPWATCH_ACTION, ForegroundService.START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                reactContext.startForegroundService(serviceIntent)
            }else{
                reactContext.startService(serviceIntent)
            }

            promise.resolve("file:///$audioFileURL")
        } catch (e: Exception) {
            Log.e(tag, "Exception: ", e)
            promise.reject("startRecord", e.message)
        }
    }

    private fun recordingTask() {
        val systemTime = SystemClock.elapsedRealtime()
        recorderRunnable = object : Runnable {
            override fun run() {

                kotlin.runCatching {
                    if(isUserInterrupted) return@runCatching

                    val time = SystemClock.elapsedRealtime() - systemTime - totalPausedRecordTime
                    val obj = Arguments.createMap().apply {
                        putDouble("currentPosition", time.toDouble())
                    }

                    val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                    val dB = if(maxAmplitude != null && maxAmplitude > 0) { 20 * log10(maxAmplitude / 32767.0) } else -160.0

                    var isSilenced = false

                    if(Build.VERSION.SDK_INT >= 29) {
                        isSilenced = mediaRecorder?.activeRecordingConfiguration?.isClientSilenced ?: false        
                    } else {
                        if (dB <= -160.0) {
                            if (amplitudeZeroCount > 10) isSilenced = true
                            else amplitudeZeroCount++
                        } else {
                            amplitudeZeroCount = 0
                            isSilenced = false
                        }    

                        obj.putInt("currentMetering", dB.toInt())
                        sendEvent(reactContext, "rn-recordback", obj)
                    }

                    if(isSilenced) {
                        if (!isInterrupted) {
                            obj.putString("status", "pausedByNative")
                            sendEvent(reactContext, "rn-recordback", obj)
                            isInterrupted = true
                            pauseTask()
                        }
                    } else {
                        if(isInterrupted) {
                            isInterrupted = false
                            obj.putString("status", "resumeByNative")
                            resumeTask()
                        }
                        obj.putInt("currentMetering", dB.toInt())
                        sendEvent(reactContext, "rn-recordback", obj)
                    }
                }

                recordHandler.postDelayed(this, subsDurationMillis)
            }
        }
        (recorderRunnable as Runnable).run()
    }

    @ReactMethod
    fun pauseRecorder(promise: Promise?) {
        if (mediaRecorder == null) {
            promise?.reject("pauseRecorder", "Recorder is null.")
            return
        }

        isUserInterrupted = true

        kotlin.runCatching {
            pauseTask()
            promise?.resolve("Recorder paused.")
        }.onFailure { e ->
            Log.e(tag, "pauseRecorder exception: " + e.message)
            promise?.reject("pauseRecorder", e.message)
        }
    }

    private fun pauseTask() {
        mediaRecorder?.pause()
        pausedRecordTime = SystemClock.elapsedRealtime()
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

        isUserInterrupted = false

        kotlin.runCatching {
            resumeTask()
            promise?.resolve("Recorder resumed.")
        }.onFailure { e ->
            Log.e(tag, "Recorder resume: " + e.message)
            promise?.reject("resumeRecorder", e.message)
        }
    }

    private fun resumeTask() {
        mediaRecorder?.resume()
        totalPausedRecordTime += SystemClock.elapsedRealtime() - pausedRecordTime
    }

    @ReactMethod
    fun stopRecorder(promise: Promise) {
        recorderRunnable?.let { recordHandler.removeCallbacks(it) }

        if (mediaRecorder == null) {
            promise.reject("stopRecord", "recorder is null.")
            return
        }

        try {
            mediaRecorder?.stop()
            isUserInterrupted = false
            isInterrupted = false
        } catch (stopException: RuntimeException) {
            stopException.message?.let { Log.d(tag,"" + it) }
            promise.reject("stopRecord", stopException.message)
        }

        mediaRecorder?.release()
        mediaRecorder = null

        val serviceIntent = Intent(reactContext, ForegroundService::class.java)
        reactContext.stopService(serviceIntent)
        promise.resolve("file:///$audioFileURL")
    }

    @ReactMethod
    fun setSubscriptionDuration(sec: Double, promise: Promise) {
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