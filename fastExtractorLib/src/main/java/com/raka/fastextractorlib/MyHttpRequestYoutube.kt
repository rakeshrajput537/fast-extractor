package com.raka.fastextractorlib

import android.content.Context
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MyHttpRequestYoutube(private val context: Context) {
    private var okHttpClient: OkHttpClient? = null
    private var isHasLoaded = false
    private var url: String? = null

    init {
        getOkHttpClient()
    }

    private fun getOkHttpClient() {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun cancel() {
        if (isHasLoaded) {
            return
        }
        try {
            okHttpClient?.dispatcher?.cancelAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun request(
        isPostMethod: Boolean,
        url: String,
        requestParams: RequestParams?,
        headers: HashMap<String, String>?,
        isPostJson: Boolean,
        jsonData: String?,
        responseListener: ResponseListener?
    ) {
        var newUrl = url
        if (newUrl.isEmpty() || !newUrl.startsWith("http")) {
            responseListener?.onFailure(-100)
            return
        }
        this.url = url
        var params = requestParams
        params = addDefaultRequestParam(params)
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient()
        }
        val request: Request
        val headersRequest = configHeader(headers)
        val builder = Request.Builder().headers(headersRequest)
        val isFormData = true
        if (!isPostMethod) {
            newUrl = generateUrlByParam(newUrl, params)
            request = builder
                .url(newUrl)
                .build()
        } else {
            request = if (isPostJson && !jsonData.isNullOrEmpty()) {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                Request.Builder().url(newUrl).post(jsonData.toRequestBody(mediaType)).build()
            } else if (!isFormData) {
                builder
                    .url(newUrl)
                    .post(getParam(params))
                    .build()
            } else {
                Request.Builder()
                    .url(newUrl)
                    .post(getParamFormData(params))
                    .build()
            }
        }
        Loggers.e("MyHttpRequest_url_first_isPost = $isPostMethod", newUrl)
        okHttpClient!!.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Loggers.e("MyHttpRequest_url onFailure", "url = $newUrl")
                e.printStackTrace()
                responseListener?.onFailure(-101)
                isHasLoaded = true
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (responseListener != null) {
                    if (response.isSuccessful && response.body != null) {
                        try {
                            val result = response.body!!.string()
                            Loggers.e("MyHttpRequest_url", newUrl)
                            Loggers.e("MyHttpRequest_result", result)
                            Loggers.e("MyHttpRequest_result", "----------------")
                            responseListener.onSuccess(response.code, result)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            responseListener.onFailure(response.code)
                        }
                    } else {
                        responseListener.onFailure(response.code)
                    }
                }
                isHasLoaded = true
            }
        })
    }

    private fun getParamFormData(requestParams: RequestParams?): RequestBody {
        if (requestParams == null) {
            return MultipartBody.Builder().build()
        }
        val urlParams: ConcurrentHashMap<String, String> = requestParams.urlParams
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        var pr = ""
        for ((key, filePath) in urlParams) {
            if (pr.isNotEmpty()) {
                pr += " \n "
            }
            builder.addFormDataPart(key, filePath)
            pr += "key = $key _value = $filePath"
        }
        Loggers.e("MyHttpRequest getParam", pr)
        return builder.build()
    }

    private fun getParam(requestParams: RequestParams?): RequestBody {
        if (requestParams == null) {
            return FormBody.Builder().build()
        }
        val urlParams: ConcurrentHashMap<String, String> = requestParams.urlParams
        val builder = FormBody.Builder()
        var pr = ""
        for ((key, value) in urlParams) {
            builder.add(key, value)
            if (pr.isNotEmpty()) {
                pr += " \n "
            }
            pr += "key = $key _value = $value"
        }
        Loggers.e("MyHttpRequest getParam", pr)
        return builder.build()
    }

    private fun generateUrlByParam(url: String, requestParams: RequestParams?): String {
        var newUrl: String? = url
        if (newUrl == null || newUrl.isEmpty()) {
            return ""
        }
        if (requestParams == null) {
            return newUrl
        }
        val urlParams: ConcurrentHashMap<String, String> = requestParams.urlParams
        var params = ""
        var flag = false
        for ((key, value) in urlParams) {
            if (flag) {
                params += "&"
            }
            params += "$key=$value"
            flag = true
        }
        if (params.isNotEmpty()) {
            newUrl += if (!newUrl.contains("?")) {
                "?$params"
            } else {
                "&$params"
            }
        }
        return newUrl
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.55 Safari/537.36"
    }

    private fun configHeader(addHeader: HashMap<String, String>?): Headers {
        val headers = HashMap<String, String>()
        headers["User-Agent"] = USER_AGENT
        headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        headers["Accept-Charset"] = "ISO-8859-1,utf-8;q=0.7,*;q=0.7"
        headers["Accept-Language"] = "vi-VN,vi;q=0.9,fr-FR;q=0.8,fr;q=0.7,en-US;q=0.6,en;q=0.5"
        addHeader?.let {
            for ((key, value) in addHeader) {
                headers[key] = value
            }
        }
        return headers.toHeaders()
    }

    private fun addDefaultRequestParam(params: RequestParams?): RequestParams {
        var requestParams = params
        if (requestParams == null) {
            requestParams = RequestParams()
        }
        requestParams.put("vCode", "${Util.getVersionCode(context)}")
        requestParams.put("vName", "${Util.getVersionName(context)}")
        requestParams.put("pn", "${context.packageName}")
        requestParams.put("libCode", "2")
        return requestParams
    }

    interface ResponseListener {
        fun onFailure(statusCode: Int)
        fun onSuccess(statusCode: Int, responseString: String?)
    }

    interface ResponseBodyHttpListener {
        fun onFailure(statusCode: Int)
        fun onSuccess(statusCode: Int, responseBody: ResponseBody?)
    }
}