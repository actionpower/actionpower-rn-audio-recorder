package com.dooboolab.audiorecorderplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver(private val audioModule: RNAudioRecorderPlayerModule): BroadcastReceiver() {
    var isPaused = false
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val extras = intent.extras
            if (extras != null) {
                val state = extras.getString(TelephonyManager.EXTRA_STATE)
                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                    // 전화 벨이 울릴 때 실행할 동작을 여기에 작성하세요.
                } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                    // 전화가 끝나고 아무 동작도 하지 않을 때 실행할 동작을 여기에 작성하세요.
                    if (isPaused) {
                        audioModule.resumeRecorder(null)
                        isPaused = false
                    }
                } else if (state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    // 전화를 받거나 걸었을 때 실행할 동작을 여기에 작성하세요.
                    audioModule.pauseRecorder(null)
                    isPaused = true
                }
            }
        }
    }
}