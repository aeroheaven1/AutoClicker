package com.autoclicker.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OCR 引擎
 *
 * 功能:
 * - 模型应用内下载 (Tesseract LSTM 中文+英文)
 * - 通过 shell 截屏 (screencap)
 * - 离线识别截图文字, 返回带坐标的词列表
 * - 找字: 根据文字定位坐标, 用于"找字点击"
 */
class OcrEngine(
    private val context: Context,
    private val runner: ICommandRunner
) {

    companion object {
        private const val TAG = "OcrEngine"
        private const val LANG = "chi_sim+eng"
        private const val SCREEN_TMP = "/data/local/tmp/autoclicker_screen.png"

        // 模型下载地址 (按顺序尝试)
        private val MODEL_URLS = listOf(
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/{lang}.traineddata",
            "https://gh-proxy.com/https://github.com/tesseract-ocr/tessdata_fast/raw/main/{lang}.traineddata",
            "https://ghproxy.net/https://github.com/tesseract-ocr/tessdata_fast/raw/main/{lang}.traineddata"
        )

        val MODELS = arrayOf("chi_sim", "eng")
    }

    /** 模型下载进度回调 */
    interface ModelDownloadCallback {
        fun onProgress(percent: Int, currentFile: String)
        fun onComplete()
        fun onError(message: String)
    }

    @Volatile
    private var tessAPI: TessBaseAPI? = null

    @Volatile
    private var isInit = false

    private fun dataPath(): String = context.filesDir.absolutePath + "/tessdata"

    /** 是否已下载模型 */
    fun isModelReady(): Boolean {
        val dir = File(dataPath())
        return MODELS.all { File(dir, "$it.traineddata").exists() }
    }

    /**
     * 确保模型就绪 (应用内下载)
     * @return 是否成功
     */
    suspend fun ensureModel(callback: ModelDownloadCallback? = null): Boolean {
        if (isModelReady()) {
            callback?.onComplete()
            return true
        }

        val dir = File(dataPath())
        if (!dir.exists()) dir.mkdirs()

        return withContext(Dispatchers.IO) {
            var success = true
            for (lang in MODELS) {
                val target = File(dir, "$lang.traineddata")
                if (target.exists() && target.length() > 100_000) continue

                var downloaded = false
                for (urlTemplate in MODEL_URLS) {
                    try {
                        val url = urlTemplate.replace("{lang}", lang)
                        Log.d(TAG, "Downloading $lang from $url")
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.connectTimeout = 15_000
                        conn.readTimeout = 30_000
                        conn.instanceFollowRedirects = true
                        conn.setRequestProperty("User-Agent", "AutoClicker/1.0")

                        val code = conn.responseCode
                        if (code in 200..299) {
                            val total = conn.contentLength
                            conn.inputStream.use { input ->
                                target.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var count: Int
                                    var downloadedBytes = 0L
                                    while (input.read(buffer).also { count = it } != -1) {
                                        output.write(buffer, 0, count)
                                        downloadedBytes += count
                                        if (total > 0) {
                                            val percent = (downloadedBytes * 100 / total).toInt()
                                            callback?.onProgress(percent, lang)
                                        }
                                    }
                                }
                            }
                            callback?.onProgress(100, lang)
                            downloaded = true
                            break
                        } else {
                            conn.disconnect()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Download $lang failed: ${e.message}")
                    }
                }

                if (!downloaded || target.length() < 100_000) {
                    success = false
                    callback?.onError("模型 $lang 下载失败, 请检查网络")
                    break
                }
            }

            if (success) {
                callback?.onComplete()
            }
            success
        }
    }

    /** 初始化 Tesseract */
    fun init(): Boolean {
        if (isInit && tessAPI != null) return true
        if (!isModelReady()) return false
        return try {
            val api = TessBaseAPI()
            if (api.init(context.filesDir.absolutePath, LANG)) {
                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
                tessAPI = api
                isInit = true
                Log.d(TAG, "Tesseract initialized")
                true
            } else {
                Log.e(TAG, "Tesseract init failed")
                api.end()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "init error: ${e.message}")
            false
        }
    }

    /**
     * 截屏并返回 Bitmap
     */
    fun captureScreen(): Bitmap? {
        return try {
            runner.exec("rm -f $SCREEN_TMP")
            runner.exec("screencap -p $SCREEN_TMP")
            val bytes = runner.readFile(SCREEN_TMP) ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "captureScreen error: ${e.message}")
            null
        }
    }

    /**
     * OCR 识别截图, 返回带坐标的文字列表
     */
    fun recognizeWords(bitmap: Bitmap): List<OcrWord> {
        if (!init()) return emptyList()
        val api = tessAPI ?: return emptyList()
        return try {
            api.setImage(bitmap)
            val result = mutableListOf<OcrWord>()
            val iterator = api.resultIterator
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim() ?: ""
                if (text.isNotEmpty()) {
                    val box = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                    if (box != null && box.size >= 4) {
                        val left = box[0]
                        val top = box[1]
                        val right = box[2]
                        val bottom = box[3]
                        result.add(
                            OcrWord(
                                text = text,
                                x = left,
                                y = top,
                                width = right - left,
                                height = bottom - top,
                                centerX = (left + right) / 2,
                                centerY = (top + bottom) / 2
                            )
                        )
                    }
                }
            } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
            iterator.delete()
            result
        } catch (e: Exception) {
            Log.e(TAG, "recognize error: ${e.message}")
            emptyList()
        }
    }

    /**
     * 一键: 截屏 + 识别
     */
    fun captureAndRecognize(): Pair<Bitmap?, List<OcrWord>> {
        val bmp = captureScreen() ?: return null to emptyList()
        val words = recognizeWords(bmp)
        return bmp to words
    }

    /**
     * 查找包含指定文字的词 (精确->包含->反向包含)
     */
    fun findWord(words: List<OcrWord>, text: String): OcrWord? {
        val target = text.trim()
        if (target.isEmpty()) return null
        return words.firstOrNull { it.text == target }
            ?: words.firstOrNull { it.text.contains(target, ignoreCase = true) }
            ?: words.firstOrNull { target.contains(it.text, ignoreCase = true) }
    }

    fun release() {
        tessAPI?.end()
        tessAPI = null
        isInit = false
    }
}

/**
 * OCR 识别出的文字及其坐标
 */
data class OcrWord(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val centerX: Int,
    val centerY: Int
)
