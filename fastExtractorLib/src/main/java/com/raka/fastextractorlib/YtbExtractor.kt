package com.raka.fastextractorlib

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Matcher
import java.util.regex.Pattern

class YtbExtractor(private val context: Context, private val apiKey: String) {
    var onAsyncTaskListener: OnAsyncTaskListener? = null
    private var myHttpRequest: MyHttpRequestYoutube? = null
    private lateinit var youtubeUrl: String
    private val patYouTubePageLink =
        Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)( |\\z|&)")
    private val patYouTubeShortLink =
        Pattern.compile("(http|https)://(www\\.|)youtu.be/(.+?)( |\\z|&)")
    private val patYouTubeEmbedLink =
        Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/embed/(.+?)( |\\z|&)")
    private var videoId: String? = null
    private var videoMeta: VideoMeta? = null
    private var cacheDirPath: String? = null
    private var decipheredSignature: String? = null
    private val lock: Lock = ReentrantLock()
    private val jsExecuting = lock.newCondition()
    private val refContext: WeakReference<Context> = WeakReference(context)
    private val CACHING = true
    private val CACHE_FILE_NAME = "decipher_js_funct"
    private var decipherJsFileName: String? = null
    private var decipherFunctions: String? = null
    private var decipherFunctionName: String? = null
    private val patSigEncUrl = Pattern.compile("url=(.+?)(\\u0026|$)")
    private val patSignature = Pattern.compile("s=(.+?)(\\u0026|$)")
    private val patDecryptionJsFile = Pattern.compile("\\\\/s\\\\/player\\\\/([^\"]+?)\\.js")
    private val patDecryptionJsFileWithoutSlash = Pattern.compile("/s/player/([^\"]+?).js")
    private var streamUrl = ""
    //    private val patFunction = Pattern.compile("([{; =])([a-zA-Z$_][a-zA-Z0-9$]{0,2})\\(")
    private val patFunction = Pattern.compile("([{; =])([a-zA-Z\$_][a-zA-Z0-9$]{0,2})\\(")
    private val patVariableFunction =
        Pattern.compile("([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(")
    private val patSignatureDecFunction =
        Pattern.compile("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{1,4})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)")
    private val FORMAT_MAP = SparseArray<Format?>()

    init {
        cacheDirPath = context.cacheDir.absolutePath
        // http://en.wikipedia.org/wiki/YouTube#Quality_and_formats
        // Video and Audio
        FORMAT_MAP.put(
            17,
            Format(17, "3gp", 144, Format.VCodec.MPEG4, Format.ACodec.AAC, 24, false)
        )
        FORMAT_MAP.put(
            36,
            Format(36, "3gp", 240, Format.VCodec.MPEG4, Format.ACodec.AAC, 32, false)
        )
        FORMAT_MAP.put(
            5,
            Format(5, "flv", 240, Format.VCodec.H263, Format.ACodec.MP3, 64, false)
        )
        FORMAT_MAP.put(
            43,
            Format(43, "webm", 360, Format.VCodec.VP8, Format.ACodec.VORBIS, 128, false)
        )
        FORMAT_MAP.put(
            18,
            Format(18, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, 96, false)
        )
        FORMAT_MAP.put(
            22,
            Format(22, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, 192, false)
        )
        // Dash Video
        FORMAT_MAP.put(
            160,
            Format(160, "mp4", 144, Format.VCodec.H264, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            133,
            Format(133, "mp4", 240, Format.VCodec.H264, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            134,
            Format(134, "mp4", 360, Format.VCodec.H264, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            135,
            Format(135, "mp4", 480, Format.VCodec.H264, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            136,
            Format(136, "mp4", 720, Format.VCodec.H264, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            137,
            Format(137, "mp4", 1080, Format.VCodec.H264, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            264,
            Format(264, "mp4", 1440, Format.VCodec.H264, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            266,
            Format(266, "mp4", 2160, Format.VCodec.H264, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            298,
            Format(298, "mp4", 720, Format.VCodec.H264, 60, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            299,
            Format(299, "mp4", 1080, Format.VCodec.H264, 60, Format.ACodec.NONE, true)
        )
        // Dash Audio
        FORMAT_MAP.put(
            140,
            Format(140, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 128, true)
        )
        FORMAT_MAP.put(
            141,
            Format(141, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 256, true)
        )
        FORMAT_MAP.put(
            256,
            Format(256, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 192, true)
        )
        FORMAT_MAP.put(
            258,
            Format(258, "m4a", Format.VCodec.NONE, Format.ACodec.AAC, 384, true)
        )
        // WEBM Dash Video
        FORMAT_MAP.put(
            278,
            Format(278, "webm", 144, Format.VCodec.VP9, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            242,
            Format(242, "webm", 240, Format.VCodec.VP9, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            243,
            Format(243, "webm", 360, Format.VCodec.VP9, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            244,
            Format(244, "webm", 480, Format.VCodec.VP9, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            247,
            Format(247, "webm", 720, Format.VCodec.VP9, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            248,
            Format(248, "webm", 1080, Format.VCodec.VP9, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            271,
            Format(271, "webm", 1440, Format.VCodec.VP9, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            313,
            Format(313, "webm", 2160, Format.VCodec.VP9, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            302,
            Format(302, "webm", 720, Format.VCodec.VP9, 60, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            308,
            Format(308, "webm", 1440, Format.VCodec.VP9, 60, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            303,
            Format(303, "webm", 1080, Format.VCodec.VP9, 60, Format.ACodec.NONE, true)
        )
        FORMAT_MAP.put(
            315,
            Format(315, "webm", 2160, Format.VCodec.VP9, 60, Format.ACodec.NONE, true)
        )
        // WEBM Dash Audio
        FORMAT_MAP.put(
            171,
            Format(171, "webm", Format.VCodec.NONE, Format.ACodec.VORBIS, 128, true)
        )
        FORMAT_MAP.put(
            249,
            Format(249, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 48, true)
        )
        FORMAT_MAP.put(
            250,
            Format(250, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 64, true)
        )
        FORMAT_MAP.put(
            251,
            Format(251, "webm", Format.VCodec.NONE, Format.ACodec.OPUS, 160, true)
        )
        streamUrl = "aHR0cHM6Ly9jZG4uZ29wbGF5dm4uY29tL3l0Yi9saW5r"
        // HLS Live Stream
        FORMAT_MAP.put(
            91,
            Format(91, "mp4", 144, Format.VCodec.H264, Format.ACodec.AAC, 48, false, true)
        )
        FORMAT_MAP.put(
            92,
            Format(92, "mp4", 240, Format.VCodec.H264, Format.ACodec.AAC, 48, false, true)
        )
        FORMAT_MAP.put(
            93,
            Format(93, "mp4", 360, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true)
        )
        FORMAT_MAP.put(
            94,
            Format(94, "mp4", 480, Format.VCodec.H264, Format.ACodec.AAC, 128, false, true)
        )
        FORMAT_MAP.put(
            95,
            Format(95, "mp4", 720, Format.VCodec.H264, Format.ACodec.AAC, 256, false, true)
        )
        FORMAT_MAP.put(
            96,
            Format(96, "mp4", 1080, Format.VCodec.H264, Format.ACodec.AAC, 256, false, true)
        )
    }

    fun start(youtubeUrl: String) {
        if (apiKey.isEmpty()) {
            return
        }
        cancel()
        this.youtubeUrl = youtubeUrl
        try {
            getVideoId()
        } catch (e: Exception) {
            e.printStackTrace()
            onAsyncTaskListener?.onExtractionComplete(null)
            return
        }
        if (videoId.isNullOrEmpty()) {
            onAsyncTaskListener?.onExtractionComplete(null)
            return
        }
        try {
            getData()
        } catch (e: Exception) {
            e.printStackTrace()
            onAsyncTaskListener?.onExtractionComplete(null)
        }
    }

    private fun getData() {
        if (myHttpRequest == null) {
            myHttpRequest = MyHttpRequestYoutube(context)
        } else {
            myHttpRequest?.cancel()
        }
        val requestParams = RequestParams()
        requestParams.put("id", "$videoId")
        requestParams.put("apiKey", apiKey)
        myHttpRequest!!.request(
            false,
            Util.decode(streamUrl),
            requestParams,
            null,
            false,
            null,
            object : MyHttpRequestYoutube.ResponseListener {
                override fun onFailure(statusCode: Int) {
                    onAsyncTaskListener?.onExtractionComplete(null)
                }

                override fun onSuccess(statusCode: Int, responseString: String?) {
                    if (responseString.isNullOrEmpty()) {
                        onAsyncTaskListener?.onExtractionComplete(null)
                        return
                    }
                    val ytFiles = handlePlayerJson(responseString)
                    onAsyncTaskListener?.onExtractionComplete(ytFiles, videoMeta)
                }
            })
    }

    private fun handlePlayerJson(responseString: String): SparseArray<YtFile>? {
        val jsonObject = getJsonObject(responseString)
        val streamingData = getJsonObject(jsonObject, "streamingData") ?: return null
        var ytFiles = SparseArray<YtFile>()
        val encSignatures = SparseArray<String>()
        //START: MY_CHECK: If youtube live has finish recent, check link same as youtube live (because api return m3u8, not mp4)
        if (streamingData.has("hlsManifestUrl")) {
            val hlsManifestUrl = getString(streamingData, "hlsManifestUrl")
            if (!hlsManifestUrl.isNullOrEmpty()) {
                val itag = 22
                val format = Format(itag, "m3u8", 720, null, null, false)
                val newVideo = YtFile(format, hlsManifestUrl)
                ytFiles = SparseArray()
                ytFiles.put(itag, newVideo)
                return ytFiles
            }
        }
        //END: MY_CHECK: If youtube live has finish recent, check link same as youtube live (because api return m3u8, not mp4)
        val formats = getJsonArray(streamingData, "formats") ?: return null
        var mat: Matcher?
        for (i in 0 until formats.length()) {
            val format = formats.getJSONObject(i)
            val itag = format.getInt("itag")
            if (FORMAT_MAP[itag] != null) {
                if (format.has("url")) {
                    val url = format.getString("url").replace("\\u0026", "&")
                    ytFiles.append(itag, YtFile(FORMAT_MAP[itag], url))
                } else if (format.has("signatureCipher")) {
                    mat = patSigEncUrl.matcher(format.getString("signatureCipher"))
                    val matSig = patSignature.matcher(format.getString("signatureCipher"))
                    if (mat.find() && matSig.find()) {
                        val url = URLDecoder.decode(mat.group(1), "UTF-8")
                        val signature = URLDecoder.decode(matSig.group(1), "UTF-8")
                        ytFiles.append(itag, YtFile(FORMAT_MAP[itag], url))
                        encSignatures.append(itag, signature)
                    }
                }
            }
        }
        val adaptiveFormats = streamingData.getJSONArray("adaptiveFormats")
        for (i in 0 until adaptiveFormats.length()) {
            val adaptiveFormat = adaptiveFormats.getJSONObject(i)
            val itag = adaptiveFormat.getInt("itag")
            if (FORMAT_MAP[itag] != null) {
                if (adaptiveFormat.has("url")) {
                    val url = adaptiveFormat.getString("url").replace("\\u0026", "&")
                    ytFiles.append(itag, YtFile(FORMAT_MAP[itag], url))
                } else if (adaptiveFormat.has("signatureCipher")) {
                    mat = patSigEncUrl.matcher(adaptiveFormat.getString("signatureCipher"))
                    val matSig =
                        patSignature.matcher(adaptiveFormat.getString("signatureCipher"))
                    if (mat.find() && matSig.find()) {
                        val url = URLDecoder.decode(mat.group(1), "UTF-8")
                        val signature = URLDecoder.decode(matSig.group(1), "UTF-8")
                        ytFiles.append(itag, YtFile(FORMAT_MAP[itag], url))
                        encSignatures.append(itag, signature)
                    }
                }
            }
        }
        val videoDetails = getJsonObject(jsonObject, "videoDetails") ?: return ytFiles
        videoMeta = VideoMeta(
            videoDetails.getString("videoId"),
            videoDetails.getString("title"),
            videoDetails.getString("author"),
            videoDetails.getString("channelId"),
            videoDetails.getString("lengthSeconds").toLong(),
            videoDetails.getString("viewCount").toLong(),
            videoDetails.getBoolean("isLiveContent"),
            videoDetails.getString("shortDescription")
        )

        if (encSignatures.size() > 0) {
            val curJsFileName: String
            if ((CACHING
                        && ((decipherJsFileName == null) || (decipherFunctions == null) || (decipherFunctionName == null)))
            ) {
                readDecipherFunctFromCache()
            }
            mat = patDecryptionJsFile.matcher(responseString)
            if (!mat.find()) mat = patDecryptionJsFileWithoutSlash.matcher(responseString)
            if (mat.find()) {
                curJsFileName = mat.group(0).replace("\\/", "/")
                if (decipherJsFileName == null || decipherJsFileName != curJsFileName) {
                    decipherFunctions = null
                    decipherFunctionName = null
                }
                decipherJsFileName = curJsFileName
            }
            val signature: String?
            decipheredSignature = null
            if (decipherSignature(encSignatures)) {
                lock.lock()
                try {
                    jsExecuting.await(7, TimeUnit.SECONDS)
                } finally {
                    lock.unlock()
                }
            }
            signature = decipheredSignature
            if (signature == null) {
                return null
            } else {
                val sigs = signature.split("\n").toTypedArray()
                var i = 0
                while (i < encSignatures.size() && i < sigs.size) {
                    val key = encSignatures.keyAt(i)
                    var url = ytFiles[key].url
                    url += "&sig=" + sigs[i]
                    val newFile = YtFile(FORMAT_MAP[key], url)
                    ytFiles.put(key, newFile)
                    i++
                }
            }
        }
        if (ytFiles.size() == 0) {
            return null
        }
        return ytFiles
    }

    private fun readDecipherFunctFromCache() {
        val cacheFile = File(cacheDirPath + "/" + CACHE_FILE_NAME)
        // The cached functions are valid for 2 weeks
        if (cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified()) < 1209600000) {
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(FileInputStream(cacheFile), "UTF-8"))
                decipherJsFileName = reader.readLine()
                decipherFunctionName = reader.readLine()
                decipherFunctions = reader.readLine()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (reader != null) {
                    try {
                        reader.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun decipherSignature(encSignatures: SparseArray<String>): Boolean {
        if (decipherJsFileName.isNullOrEmpty()) {
            return false
        }
        // Assume the functions don't change that much
        if (decipherFunctionName == null || decipherFunctions == null) {
            val decipherFunctUrl = "https://youtube.com$decipherJsFileName"
            var reader: BufferedReader? = null
            val javascriptFile: String
            val url = URL(decipherFunctUrl)
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.setRequestProperty("User-Agent", MyHttpRequestYoutube.USER_AGENT)
            try {
                reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
                val sb = StringBuilder()
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    sb.append(line)
                    sb.append(" ")
                }
                javascriptFile = sb.toString()
            } finally {
                reader?.close()
                urlConnection.disconnect()
            }
            var mat = patSignatureDecFunction.matcher(javascriptFile)
            if (mat.find()) {
                decipherFunctionName = mat.group(1)
                val patMainVariable = Pattern.compile(
                    ("(var |\\s|,|;)" + decipherFunctionName!!.replace("$", "\\$") +
                            "(=function\\((.{1,3})\\)\\{)")
                )
                var mainDecipherFunct: String
                mat = patMainVariable.matcher(javascriptFile)
                if (mat.find()) {
                    mainDecipherFunct = "var " + decipherFunctionName + mat.group(2)
                } else {
                    val patMainFunction = Pattern.compile(
                        ("function " + decipherFunctionName!!.replace("$", "\\$") +
                                "(\\((.{1,3})\\)\\{)")
                    )
                    mat = patMainFunction.matcher(javascriptFile)
                    if (!mat.find()) return false
                    mainDecipherFunct = "function " + decipherFunctionName + mat.group(2)
                }
                var startIndex = mat.end()
                var braces = 1
                var i = startIndex
                while (i < javascriptFile.length) {
                    if (braces == 0 && startIndex + 5 < i) {
                        mainDecipherFunct += javascriptFile.substring(startIndex, i) + ";"
                        break
                    }
                    if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
                    i++
                }
                decipherFunctions = mainDecipherFunct
                // Search the main function for extra functions and variables
                // needed for deciphering
                // Search for variables
                mat = patVariableFunction.matcher(mainDecipherFunct)
                while (mat.find()) {
                    val variableDef = "var " + mat.group(2) + "={"
                    if (decipherFunctions!!.contains(variableDef)) {
                        continue
                    }
                    startIndex = javascriptFile.indexOf(variableDef) + variableDef.length
                    var braces = 1
                    var i = startIndex
                    while (i < javascriptFile.length) {
                        if (braces == 0) {
                            decipherFunctions += variableDef + javascriptFile.substring(
                                startIndex,
                                i
                            ) + ";"
                            break
                        }
                        if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
                        i++
                    }
                }
                // Search for functions
                mat = patFunction.matcher(mainDecipherFunct)
                while (mat.find()) {
                    val functionDef = "function " + mat.group(2) + "("
                    if (decipherFunctions!!.contains(functionDef)) {
                        continue
                    }
                    startIndex = javascriptFile.indexOf(functionDef) + functionDef.length
                    var braces = 0
                    var i = startIndex
                    while (i < javascriptFile.length) {
                        if (braces == 0 && startIndex + 5 < i) {
                            decipherFunctions += functionDef + javascriptFile.substring(
                                startIndex,
                                i
                            ) + ";"
                            break
                        }
                        if (javascriptFile[i] == '{') braces++ else if (javascriptFile[i] == '}') braces--
                        i++
                    }
                }
                decipherViaWebView(encSignatures)
                if (CACHING) {
                    writeDeciperFunctToChache()
                }
            } else {
                return false
            }
        } else {
            decipherViaWebView(encSignatures)
        }
        return true
    }

    private fun writeDeciperFunctToChache() {
        val cacheFile = File("$cacheDirPath/$CACHE_FILE_NAME")
        var writer: BufferedWriter? = null
        try {
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(cacheFile), "UTF-8"))
            writer.write(decipherJsFileName + "\n")
            writer.write(decipherFunctionName + "\n")
            writer.write(decipherFunctions)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (writer != null) {
                try {
                    writer.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun decipherViaWebView(encSignatures: SparseArray<String>) {
        val context = refContext.get() ?: return
        val stb = StringBuilder(decipherFunctions + " function decipher(")
        stb.append("){return ")
        for (i in 0 until encSignatures.size()) {
            val key = encSignatures.keyAt(i)
            if (i < encSignatures.size() - 1) stb.append(decipherFunctionName).append("('").append(
                encSignatures[key]
            ).append("')+\"\\n\"+") else stb.append(decipherFunctionName).append("('").append(
                encSignatures[key]
            ).append("')")
        }
        stb.append("};decipher();")
//        Handler(Looper.getMainLooper()).post(Runnable {
//            JsEvaluator(context).evaluate(stb.toString(), object : JsCallback {
//                override fun onResult(result: String) {
//                    lock.lock()
//                    try {
//                        decipheredSignature = result
//                        jsExecuting.signal()
//                    } finally {
//                        lock.unlock()
//                    }
//                }
//
//                override fun onError(errorMessage: String) {
//                    lock.lock()
//                    try {
//                        jsExecuting.signal()
//                    } finally {
//                        lock.unlock()
//                    }
//                }
//            })
//        })
    }

    private fun getVideoId() {
        var matcher = patYouTubePageLink.matcher(youtubeUrl)
        if (matcher.find()) {
            videoId = matcher.group(3)
            return
        }
        matcher = patYouTubeShortLink.matcher(youtubeUrl)
        if (matcher.find()) {
            videoId = matcher.group(3)
            return
        }
        matcher = patYouTubeEmbedLink.matcher(youtubeUrl)
        if (matcher.find()) {
            videoId = matcher.group(3)
            return
        }
        if (!youtubeUrl.startsWith("http", true)) {
            videoId = youtubeUrl
            youtubeUrl = "https://www.youtube.com/watch?v=$youtubeUrl"
        }
    }

    fun cancel() {
        myHttpRequest?.cancel()
    }

    fun cancel(mayInterruptIfRunning: Boolean) {
        cancel()
    }

    interface OnAsyncTaskListener {
        /*fun onPreExecute()
        fun doInBackground()
        fun onPostExecute(data: String)*/
        fun onExtractionComplete(ytFiles: SparseArray<YtFile>?, videoMeta: VideoMeta? = null)
    }

    private fun getJsonObject(json: String?): JSONObject? {
        if (json.isNullOrEmpty()) {
            return null
        }
        try {
            return JSONObject(json)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getJsonObject(jObject: JSONObject?, param: String): JSONObject? {
        if (!existParam(jObject, param)) {
            return null
        }
        try {
            return jObject?.getJSONObject(param)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getJsonArray(jObject: JSONObject?, param: String?): JSONArray? {
        if (!existParam(jObject, param)) {
            return null
        }
        try {
            return jObject?.getJSONArray(param)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }

    fun getJsonArray(json: String?): JSONArray? {
        try {
            return JSONArray(json)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getString(jObject: JSONObject?, param: String): String? {
        if (!existParam(jObject, param)) {
            return null
        }
        try {
            return jObject?.getString(param)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }

    private fun existParam(jObject: JSONObject?, param: String?): Boolean {
        if (jObject == null || param == null) {
            return false
        }
        return if (!jObject.has(param)) {
            false
        } else !jObject.isNull(param)
    }
}