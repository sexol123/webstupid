package com.sergeimaleev.webstupid

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.View.SYSTEM_UI_LAYOUT_FLAGS
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.sergeimaleev.webstupid.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var cookie: CookieManager
    private lateinit var inputManager: InputMethodManager
    private lateinit var downloadManager: DownloadManager
    private lateinit var binding: ActivityMainBinding

    override fun onNewIntent(intent: Intent?) {
        handleSearchIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webWiew.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        binding.webWiew.restoreState(savedInstanceState)
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
        savedInstanceState?.let {
            binding.webWiew.restoreState(it)
        }

        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

        with(binding) {
            with(webWiew.settings) {
                allowContentAccess = true
                javaScriptEnabled = true
                builtInZoomControls = false
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                defaultTextEncodingName = "utf-8"
                setSupportMultipleWindows(false)
                loadWithOverviewMode = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    offscreenPreRaster = true
                }
                domStorageEnabled = true
            }

            cookie = CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webWiew, true)
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
                    showGoBack(webWiew.canGoBack())
                    showGoForward(webWiew.canGoForward())
                    showGoHome(webWiew.canGoBackOrForward(1).not())
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    setGoButtonState(LoadingSTATE.LOADING)
                    setReloadButton(LoadingSTATE.LOADING)
                    setProgressBarState(LoadingSTATE.LOADING)
                    showGoBack(webWiew.canGoBack())
                    showGoForward(webWiew.canGoForward())
                    showGoHome(webWiew.canGoBackOrForward(1).not())
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
                    showGoForward(webWiew.canGoForward())
                    showGoHome(webWiew.canGoBackOrForward(1).not())
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    super.onReceivedSslError(view, handler, error)
                    Toast.makeText(
                        root.context,
                        "Ssl Error " + error?.primaryError.toString(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            webWiew.webChromeClient = MyChromeWebClient()

            webWiew.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->

                if (!hasWriteStoragePermission()) {
                    Snackbar.make(
                        root,
                        "No permissions, please enable a write permission",
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@setDownloadListener
                }

                val title = URLUtil.guessFileName(url, contentDisposition, mimetype)

                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    allowScanningByMediaScanner()
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        setRequiresCharging(false)
                    }
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) //Notify client once download is completed!
                    setTitle(title)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        title
                    )
                }

                val cookies = cookie.getCookie(url)
                request.addRequestHeader("cookie", cookies)

                downloadManager.enqueue(request)
                Snackbar.make(
                    root,
                    "$title added to downloading..",  //To notify the Client that the file is being downloaded
                    Snackbar.LENGTH_LONG
                ).show()
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

            goForward.setOnClickListener {
                webWiew.goForward()
            }

            goHome.setOnClickListener {
                //setHomeState()
            }

            goNewTab.setOnClickListener {}

            goOptions.setOnClickListener { view ->
                PopupMenu(view.context, view).apply {
                    this.setOnMenuItemClickListener {
                        when (it.groupId) {
                            1 -> {
                                true
                            }
                            2 -> {
                                true
                            }
                        }

                        false
                    }


                    with(menu) {
                        val historySubMenu = this.addSubMenu(R.string.history)
                        historySubMenu.setHeaderTitle(R.string.history)
                        historySubMenu.add("1")
                        historySubMenu.add("2")

                        val bookmarksSubMenu = this.addSubMenu(R.string.bookmarks)
                        bookmarksSubMenu.setHeaderTitle(R.string.bookmarks)
                        bookmarksSubMenu.add("1")
                        bookmarksSubMenu.add("2")
                    }
                }.show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_PERMISSIONS_CODE_WRITE_STORAGE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    val activity = this
                    Snackbar.make(
                        binding.root,
                        "No permissions, please enable a write permission",
                        Snackbar.LENGTH_INDEFINITE
                    ).apply {
                        setAction("OK") {
                            ActivityCompat.requestPermissions(
                                activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                REQUEST_PERMISSIONS_CODE_WRITE_STORAGE
                            )
                        }
                    }.show()
                }

            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onStart() {
        super.onStart()
        handleSearchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        binding.apply {
            webWiew.onResume()
            webWiew.resumeTimers()
        }
    }

    override fun onPause() {
        binding.apply {
            webWiew.onPause()
            webWiew.pauseTimers()
        }
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

    private fun setHomeState() {
        with(binding) {
            webWiew.stopLoading()
            input.text?.clear()
            setReloadButton(LoadingSTATE.DEFAULT)
            setProgressBarState(LoadingSTATE.DEFAULT)
            setGoButtonState(LoadingSTATE.DEFAULT)
            showGoHome(false)
            webWiew.visibility = View.GONE
            webWiew.clearHistory()
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

    private fun showGoHome(show: Boolean) {
        /*binding.apply {
            goHome.isVisible = show
        }*/
    }

    private fun showGoForward(show: Boolean) {
        binding.apply {
            goForward.isVisible = show
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

    private fun hasWriteStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_PERMISSIONS_CODE_WRITE_STORAGE
                )

                return false
            }
        }

        return true
    }

    private fun hideKeyboard() {
        inputManager.hideSoftInputFromWindow(
            currentFocus?.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }

    inner class MyChromeWebClient : WebChromeClient() {

        private var mCustomViewCallback: WebChromeClient.CustomViewCallback? = null

        //private var mOriginalOrientation: Int? = null
        private var mOriginalSystemUiVisibility: Int? = null
        private var mCustomView: View? = null
        private var mFullScreenContainer: FrameLayout? = null

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

        override fun onShowCustomView(
            paramView: View?,
            callback: WebChromeClient.CustomViewCallback?
        ) {
            if (this.mCustomView != null) {
                onHideCustomView()
                return
            }

            mCustomView = paramView
            mOriginalSystemUiVisibility = window.decorView.systemUiVisibility
            //mOriginalOrientation = requestedOrientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            mCustomViewCallback = callback
            (window.decorView as FrameLayout).addView(
                mCustomView, FrameLayout.LayoutParams(
                    -1,
                    -1
                )
            )
            window.decorView.systemUiVisibility = 3846 or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        override fun onHideCustomView() {
            (window.decorView as FrameLayout).removeView(mCustomView)
            mCustomView = null
            window.decorView.systemUiVisibility =
                mOriginalSystemUiVisibility ?: SYSTEM_UI_LAYOUT_FLAGS
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            mCustomViewCallback?.onCustomViewHidden()
            mCustomViewCallback = null
        }
    }

    companion object {
        private const val HTTP = "http://"
        private const val HTTPS = "https://"
        private const val WWW = "www."

        private const val GOOGLE_SEARCH = "https://www.google.com/search?q="

        private const val REQUEST_PERMISSIONS_CODE_WRITE_STORAGE = 666
    }
}

enum class LoadingSTATE {
    LOADING, LOADED, DEFAULT
}