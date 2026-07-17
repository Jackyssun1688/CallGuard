package com.callguard.audio

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.PermissionChecker
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * 通话录音器 — 通过麦克风捕获对方声音（扬声器模式下）。
 *
 * 录音时长上限 20 秒，录音完成后自动保存临时 WAV 文件供语音识别使用。
 */
class CallAudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "CallGuard.Audio"
        private const val SAMPLE_RATE = 16000
        private const val MAX_DURATION_MS = 20_000  // 最多录 20 秒
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingFile: File? = null

    /** 是否有音频录制权限 */
    private fun hasPermission(): Boolean {
        return PermissionChecker.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PermissionChecker.PERMISSION_GRANTED
    }

    /**
     * 录制对方声音的最初 20 秒。
     * @return 录音文件路径，如果失败则返回 null
     */
    fun captureFirst20Seconds(): File? {
        if (!hasPermission()) {
            Log.w(TAG, "No RECORD_AUDIO permission")
            return null
        }

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            SAMPLE_RATE * BUFFER_SIZE_MULTIPLIER
        )

        if (bufferSize <= 0) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return null
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val record = audioRecord ?: return null

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            record.release()
            audioRecord = null
            return null
        }

        // 尝试添加回声消除（不影响录音，有则更好）
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(record.audioSessionId)
                aec?.enabled = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable AEC", e)
        }

        // 准备临时文件
        val tmpFile = createTempFile()
        recordingFile = tmpFile

        val totalSamples = (SAMPLE_RATE * MAX_DURATION_MS / 1000)
        val buffer = ShortArray(bufferSize / 2)
        var samplesRead = 0

        try {
            record.startRecording()
            isRecording = true

            FileOutputStream(tmpFile).use { fos ->
                // 写入 WAV 头部（后续填充大小）
                writeWavHeader(fos, totalSamples)

                while (isRecording && samplesRead < totalSamples) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val toWrite = minOf(read, (totalSamples - samplesRead) / 2)
                        if (toWrite > 0) {
                            val bytes = ShortArray(toWrite)
                            buffer.copyInto(bytes, 0, 0, toWrite)
                            val byteBuf = ByteArray(toWrite * 2)
                            for (i in bytes.indices) {
                                val si = bytes[i].toInt() // Short → Int for bitwise ops
                                byteBuf[i * 2] = (si and 0xFF).toByte()
                                byteBuf[i * 2 + 1] = ((si shr 8) and 0xFF).toByte()
                            }
                            fos.write(byteBuf)
                            samplesRead += toWrite
                        }
                    }
                }
            }

            // 补全 WAV 头部的文件大小
            val actualSamples = samplesRead
            val dataSize = actualSamples * 2  // 16-bit mono
            val fileSize = 36 + dataSize
            RandomAccessFile(tmpFile, "rw").use { raf ->
                raf.seek(4)
                raf.writeInt(Integer.reverseBytes(fileSize))
                raf.seek(40)
                raf.writeInt(Integer.reverseBytes(dataSize))
            }

            // 尝试写入 MediaStore（调试用可查看录音文件）
            // saveToMediaStore(tmpFile)

            Log.d(TAG, "Recorded ${actualSamples / (SAMPLE_RATE / 1000)}ms of audio")
            return tmpFile

        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
            tmpFile.delete()
            return null
        } finally {
            stop()
        }
    }

    /** 停止录音 */
    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord?.release()
        audioRecord = null
    }

    private fun createTempFile(): File {
        val dir = File(context.cacheDir, "callguard_audio")
        dir.mkdirs()
        return File.createTempFile("capture_", ".wav", dir)
    }

    /** 写入 16-bit 单声道 16kHz WAV 文件头 */
    private fun writeWavHeader(fos: FileOutputStream, totalSamples: Int) {
        val dataSize = totalSamples * 2  // 16-bit mono
        val fileSize = 36 + dataSize

        // RIFF header
        fos.write("RIFF".toByteArray())
        fos.write(ByteArray(4).also { writeLEInt(it, 0, fileSize) }) // placeholder
        fos.write("WAVE".toByteArray())

        // fmt chunk
        fos.write("fmt ".toByteArray())
        fos.write(ByteArray(4).also { writeLEInt(it, 0, 16) })  // chunk size
        fos.write(ByteArray(2).also { writeLEShort(it, 0, 1) }) // PCM
        fos.write(ByteArray(2).also { writeLEShort(it, 0, 1) }) // mono
        fos.write(ByteArray(4).also { writeLEInt(it, 0, SAMPLE_RATE) })
        fos.write(ByteArray(4).also { writeLEInt(it, 0, SAMPLE_RATE * 2) }) // byte rate
        fos.write(ByteArray(2).also { writeLEShort(it, 0, 2) }) // block align
        fos.write(ByteArray(2).also { writeLEShort(it, 0, 16) }) // bits per sample

        // data chunk
        fos.write("data".toByteArray())
        fos.write(ByteArray(4).also { writeLEInt(it, 0, dataSize) }) // placeholder
    }

    private fun writeLEInt(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeLEShort(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
