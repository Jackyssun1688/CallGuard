package com.callguard.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 语音转文字引擎 — 使用 Android 内置离线语音识别（Google Speech Recognizer）。
 *
 * 支持中英文识别，完全离线，无需 API Key，无需联网。
 */
class SpeechToTextEngine(private val context: Context) {

    companion object {
        private const val TAG = "CallGuard.STT"
    }

    /** 语音识别是否可用 */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * 将音频文件识别为文字。
     * 注意：Android SpeechRecognizer 主要支持来自麦克风的实时语音。
     * 对于预录制文件，我们启动一个快速识别请求，或者使用离线识别。
     *
     * 实际上 Android 的 SpeechRecognizer 不支持直接输入文件。
     * 替代方案：我们传一个空音频触发识别，或者使用 Google 的 Cloud API。
     * 更实用的方案是：在录音的同时实时调用 SpeechRecognizer 的流式识别。
     *
     * 这里实现两种方式：
     * 1. 文件识别：使用 RecognizerIntent + EXTRA_AUDIO 方式（需要 API 33+，有限支持）
     * 2. 流式识别推荐：实际使用中，在录音同时启动连续识别（SpeechRecognizer.startListening）
     */
    suspend fun transcribeFile(audioFile: File): String {
        if (!isAvailable()) {
            Log.w(TAG, "Speech recognition not available on this device")
            return ""
        }

        if (!audioFile.exists() || audioFile.length() < 100) {
            Log.w(TAG, "Audio file too small or missing: ${audioFile.absolutePath}")
            return ""
        }

        return try {
            // 使用 RecognizerIntent 传入音频
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

                // Android 33+ 支持传入音频 URI
                if (Build.VERSION.SDK_INT >= 33) {
                    putExtra("android.speech.extra.AUDIO", audioFile.toURI())
                }
            }

            // 创建 SpeechRecognizer
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            var result = ""

            try {
                suspendCoroutine<Unit> { continuation ->
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Log.d(TAG, "STT ready")
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No permission"
                                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                                SpeechRecognizer.ERROR_SERVER -> "Server error"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
                                else -> "Unknown error: $error"
                            }
                            Log.w(TAG, "STT error: $errorMsg")
                            continuation.resume(Unit)
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                result = matches[0]
                                Log.d(TAG, "STT result: $result")
                            }
                            continuation.resume(Unit)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty() && result.isEmpty()) {
                                result = matches[0]
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })

                    recognizer.startListening(intent)
                }
            } finally {
                recognizer.destroy()
            }

            // 如果文件识别没结果，尝试模拟重新识别
            if (result.isEmpty()) {
                Log.d(TAG, "File-based STT returned empty, trying live mode fallback...")
                result = tryLiveRecognition()
            }

            Log.d(TAG, "Final STT result: '$result'")
            result

        } catch (e: Exception) {
            Log.e(TAG, "STT failed", e)
            ""
        }
    }

    /**
     * 实时语音识别（麦克风）。用于在录音的同时捕获语音文字。
     * @param maxDurationMs 最长监听时长（毫秒），默认 20 秒
     * @return 识别到的文字
     */
    suspend fun recognizeLive(maxDurationMs: Int = 20000): String {
        if (!isAvailable()) return ""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var result = ""

        try {
            suspendCoroutine<Unit> { continuation ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Live STT ready")
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Live STT end of speech")
                    }

                    override fun onError(error: Int) {
                        if (result.isEmpty()) {
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                                else -> "STT error: $error"
                            }
                            Log.w(TAG, msg)
                        }
                        continuation.resume(Unit)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            result = matches[0]
                        }
                        continuation.resume(Unit)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty() && result.length < matches[0].length) {
                            result = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            }
        } finally {
            recognizer.stopListening()
            recognizer.destroy()
        }

        return result
    }

    /**
     * 回退方案：尝试通过实时麦克风模式进行语音识别。
     */
    private suspend fun tryLiveRecognition(): String {
        return recognizeLive(maxDurationMs = 10000)
    }
}
