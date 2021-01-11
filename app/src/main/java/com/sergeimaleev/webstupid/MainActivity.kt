package com.sergeimaleev.webstupid

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.sergeimaleev.webstupid.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var inputManager: InputMethodManager
    private lateinit var binding: ActivityMainBinding

    override fun onNewIntent(intent: Intent?) {
        handleSearchIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.bind(
            this.layoutInflater.inflate(
                R.layout.activity_main,
                null,
                false
            )
        )
        setContentView(binding.root)

        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        with(binding) {
            with(webWiew.settings) {
                allowContentAccess = true
                javaScriptEnabled = true
                builtInZoomControls = false
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                defaultTextEncodingName = "utf-8"
            }

            webWiew.webViewClient = object : WebViewClient() {
                override fun onLoadResource(view: WebView?, url: String?) {
                    binding.webWiew.isVisible = true
                    super.onLoadResource(view, url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    setReloadButton(LoadingSTATE.LOADED)
                    setProgressBarState(LoadingSTATE.LOADED)
                    setGoButtonState(LoadingSTATE.LOADED)
                    currentFocus?.clearFocus()
                    url?.let(input::setText)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    setGoButtonState(LoadingSTATE.LOADING)
                    setReloadButton(LoadingSTATE.LOADING)
                    setProgressBarState(LoadingSTATE.LOADING)
                    showGoBack(webWiew.canGoBack())
                    if (input.text.isNullOrEmpty()) {
                        url?.let(input::setText)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    setGoButtonState(LoadingSTATE.DEFAULT)
                    super.onReceivedError(view, request, error)
                    showGoBack(webWiew.canGoBack())
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.proceed()
                    Toast.makeText(
                        root.context,
                        "Ssl Error " + error?.primaryError.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            webWiew.webChromeClient = object : WebChromeClient() {

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        binding.progressbar.setProgress(newProgress, true)
                    } else {
                        binding.progressbar.progress = newProgress
                    }
                }
            }

            input.setOnEditorActionListener { v, actionId, event ->
                when (actionId) {
                    EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_DONE -> {
                        go.callOnClick()
                        true
                    }
                    else -> false
                }
            }

            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s.isNullOrBlank()) {
                        setGoButtonState(LoadingSTATE.DEFAULT)
                    } else {
                        setGoButtonState(LoadingSTATE.LOADED)
                    }
                }
            })

            go.setOnClickListener {
                workWithInput(input.text)?.let(webWiew::loadUrl)
                hideKeyboard()
            }
            goBack.setOnClickListener {
                webWiew.goBack()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        handleSearchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        binding.webWiew.onResume()
    }

    override fun onPause() {
        binding.webWiew.onPause()
        super.onPause()
    }

    override fun onBackPressed() {
        if (binding.webWiew.canGoBack()) {
            binding.webWiew.goBack()
        } else {
            super.onBackPressed()
        }
    }


    private fun handleSearchIntent(newIntent: Intent?) {
        newIntent?.dataString?.let {
            workWithInput(it)?.let { finalQuery ->
                setProgressBarState(LoadingSTATE.LOADING)
                binding.webWiew.loadUrl(finalQuery)
            }
        }
    }

    private fun workWithInput(inputTxt: CharSequence?): String? {
        return if (inputTxt.isNullOrBlank()) {
            null
        } else if (inputTxt == HTTP || inputTxt == HTTPS || inputTxt == WWW) {
            null
        } else if (inputTxt.startsWith(HTTP) || inputTxt.startsWith(HTTPS)) {
            inputTxt.toString()
        } else if (inputTxt.startsWith(WWW)) {
            HTTPS + inputTxt
        } else {
            "${getDefaultSearch()}$inputTxt"
        }
    }

    private fun getDefaultSearch(): String {
        return GOOGLE_SEARCH
    }

    private fun showGoBack(show: Boolean) {
        binding.apply {
            goBack.isVisible = show
        }
    }

    private fun setProgressBarState(state: LoadingSTATE) {
        when (state) {
            LoadingSTATE.LOADING -> {
                binding.progressbar.show()
            }
            LoadingSTATE.LOADED -> {
                binding.progressbar.hide()
            }
            LoadingSTATE.DEFAULT -> {
                binding.progressbar.hide()
            }
        }
    }

    private fun setGoButtonState(state: LoadingSTATE) {
        binding.apply {
            when (state) {
                LoadingSTATE.DEFAULT -> {
                    go.isEnabled = false
                }
                LoadingSTATE.LOADED -> {
                    go.isEnabled = true
                }

                LoadingSTATE.LOADING -> {
                    go.isEnabled = false
                }
            }
        }
    }

    private fun setReloadButton(state: LoadingSTATE) {
        binding.apply {
            when (state) {
                LoadingSTATE.DEFAULT -> {
                    reload.isVisible = false
                }
                LoadingSTATE.LOADED -> {
                    reload.isVisible = true
                    reload.setImageResource(R.drawable.ic_refresh_24)
                    reload.setOnClickListener {
                        val url = webWiew.originalUrl ?: input.text
                        webWiew.loadUrl(url.toString())
                    }
                }
                LoadingSTATE.LOADING -> {
                    reload.isVisible = true
                    reload.setImageResource(R.drawable.ic_close_24)
                    reload.setOnClickListener {
                        webWiew.stopLoading()
                        setReloadButton(LoadingSTATE.LOADED)
                    }
                }
            }
        }
    }

    private fun hideKeyboard() {
        inputManager.hideSoftInputFromWindow(
            currentFocus?.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }

    companion object {
        private const val HTTP = "http://"
        private const val HTTPS = "https://"
        private const val WWW = "www."

        private const val GOOGLE_SEARCH = "https://www.google.com/search?q="
    }
}

enum class LoadingSTATE {
    LOADING, LOADED, DEFAULT
}