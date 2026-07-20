package com.ao3.kiosk

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var urlBar: EditText
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnMenu: ImageButton
    private var runtime: GeckoRuntime? = null
    private var canGoBack = false
    private var canGoForward = false
    private var urlBarUserEditing = false

    companion object {
        private const val START_URL = "https://archiveofourown.org"
        private const val DOH_URI = "https://0kbpekmcr1.cloudflare-gateway.com/dns-query"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.geckoview)
        urlBar = findViewById(R.id.url_bar)
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnReload = findViewById(R.id.btn_reload)
        btnMenu = findViewById(R.id.btn_menu)

        setupGeckoView()
        setupUrlBar()
        setupNavigationButtons()
        setupMenu()
        setupBackButton()
    }

    private fun setupGeckoView() {
        session = GeckoSession()

        session.contentDelegate = object : ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {}
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {}
        }

        session.navigationDelegate = object : NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (!urlBarUserEditing && url != null) {
                    urlBar.setText(url)
                }
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                return null
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
                btnBack.isEnabled = canGoBack
                btnBack.alpha = if (canGoBack) 1.0f else 0.4f
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                this@MainActivity.canGoForward = canGoForward
                btnForward.isEnabled = canGoForward
                btnForward.alpha = if (canGoForward) 1.0f else 0.4f
            }
        }

        session.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (!urlBarUserEditing) {
                    urlBar.setText(url)
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!success) {
                    session.loadUri(START_URL)
                }
            }
        }

        if (runtime == null) {
            val configYaml = buildString {
                appendLine("prefs:")
                appendLine("  network.trr.mode: 3")
                appendLine("  network.trr.uri: \"$DOH_URI\"")
                appendLine("  network.trr.excluded-domains: \"\"")
                appendLine("  network.dns.echloop: true")
                appendLine("  network.dns.use_https_rr_as_altns: true")
                appendLine("  dom.security.https_only_mode: true")
            }
            val configFile = File(filesDir, "geckoview-config.yaml")
            configFile.writeText(configYaml)

            val settings = GeckoRuntimeSettings.Builder()
                .configFilePath(configFile.absolutePath)
                .build()
            runtime = GeckoRuntime.create(this, settings)

            installWebExtension()
        }

        session.open(runtime!!)
        geckoView.setSession(session)
        session.loadUri(START_URL)
    }

    private fun installWebExtension() {
        val extController = runtime!!.webExtensionController
        extController.ensureBuiltIn(
            "resource://android/assets/ao3-translator/",
            "ao3-translator@ao3-kiosk"
        ).accept(
            { ext ->
                android.util.Log.i("AO3Kiosk", "翻译扩展加载成功: ${ext.id}")
            },
            { e ->
                android.util.Log.e("AO3Kiosk", "翻译扩展加载失败", e)
                runOnUiThread {
                    Toast.makeText(this, "翻译扩展加载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun setupUrlBar() {
        urlBar.setOnFocusChangeListener { _, hasFocus ->
            urlBarUserEditing = hasFocus
            if (hasFocus) {
                urlBar.selectAll()
            }
        }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                navigateToUrl(urlBar.text.toString())
                urlBar.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun navigateToUrl(input: String) {
        val url = input.trim()
        if (url.isEmpty()) return

        val finalUrl = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.contains(".") && !url.contains(" ") -> "https://$url"
            else -> "https://archiveofourown.org/works/search?work_search[query]=" + java.net.URLEncoder.encode(url, "UTF-8")
        }
        session.loadUri(finalUrl)
    }

    private fun setupNavigationButtons() {
        btnBack.setOnClickListener {
            if (canGoBack) session.goBack()
        }
        btnForward.setOnClickListener {
            if (canGoForward) session.goForward()
        }
        btnReload.setOnClickListener {
            session.reload()
        }
    }

    private fun setupMenu() {
        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, R.string.menu_home)
            popup.menu.add(0, 2, 0, R.string.menu_doh)
            popup.menu.add(0, 3, 0, R.string.menu_clear_cache)
            popup.menu.add(0, 4, 0, R.string.menu_about)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { session.loadUri(START_URL); true }
                    2 -> { showDoHStatus(); true }
                    3 -> { clearCache(); true }
                    4 -> { showAbout(); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun showDoHStatus() {
        Toast.makeText(this, R.string.doh_enabled, Toast.LENGTH_SHORT).show()
    }

    private fun clearCache() {
        Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show()
        session.reload()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("关于 AO3 Kiosk")
            .setMessage(getString(R.string.about_text))
            .setPositiveButton("确定", null)
            .show()
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) {
                    session.goBack()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        session.setActive(true)
    }

    override fun onStop() {
        super.onStop()
        session.setActive(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        session.close()
    }
}
