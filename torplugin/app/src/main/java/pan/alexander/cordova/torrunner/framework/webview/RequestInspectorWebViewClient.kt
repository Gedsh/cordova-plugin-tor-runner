package pan.alexander.cordova.torrunner.framework.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.apache.cordova.engine.SystemWebView
import org.apache.cordova.engine.SystemWebViewClient
import org.apache.cordova.engine.SystemWebViewEngine

@SuppressLint("SetJavaScriptEnabled")
open class RequestInspectorWebViewClient @JvmOverloads constructor(
    engine: SystemWebViewEngine,
    webView: SystemWebView,
    private val options: RequestInspectorOptions = RequestInspectorOptions()
) : SystemWebViewClient(engine) {

    private val interceptionJavascriptInterface = RequestInspectorJavaScriptInterface(webView)

    init {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
    }

    final override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (request.url.toString().startsWith("https://localhost/")) {
            return super.shouldInterceptRequest(view, request)
        }
        val recordedRequest = interceptionJavascriptInterface.findRecordedRequestForUrl(
            request.url.toString()
        )
        val webViewRequest = WebViewRequest.Companion.create(request, recordedRequest)
        return shouldInterceptRequest(view, webViewRequest)
    }

    open fun shouldInterceptRequest(
        view: WebView,
        webViewRequest: WebViewRequest
    ): WebResourceResponse? {
        logWebViewRequest(webViewRequest)
        return null
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun logWebViewRequest(webViewRequest: WebViewRequest) {
        Log.i(LOG_TAG, "Sending request from WebView: $webViewRequest")
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        Log.i(LOG_TAG, "Page started loading, enabling request inspection. URL: $url")
        RequestInspectorJavaScriptInterface.enabledRequestInspection(
            view,
            options.extraJavaScriptToInject
        )
        super.onPageStarted(view, url, favicon)
    }

    companion object {
        private const val LOG_TAG = "RequestInspectorWebView"
    }
}
