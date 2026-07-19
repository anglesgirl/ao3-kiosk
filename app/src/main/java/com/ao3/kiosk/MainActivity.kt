package com.ao3.kiosk

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private var runtime: GeckoRuntime? = null
    private var canGoBack = false

    companion object {
        private const val START_URL = "https://archiveofourown.org"
        private const val DOH_URI = "https://0kbpekmcr1.cloudflare-gateway.com/dns-query"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.geckoview)

        setupImmersiveMode()
        setupGeckoView()
        setupBackButton()
    }

    /**
     * 配置 GeckoView,设置 DoH + ECH 实现绕过 DNS 污染和 SNI 审查。
     * 通过 configFilePath 在 Runtime 启动前注入 Gecko prefs:
     * - DoH: network.trr.mode=3, 仅用 DoH 不回退系统 DNS
     * - ECH: network.dns.echloop=true, 加密 ClientHello (Firefox 119+ 默认开启)
     */
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
            ) {}

            override fun onLoadRequest(
                session: GeckoSession,
                request: NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                return null
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
            }
        }

        session.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {}
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
        }

        session.open(runtime!!)
        geckoView.setSession(session)
        session.loadUri(START_URL)
    }

    /**
     * 返回键:kiosk 模式下,返回键用于网页后退。
     * 如果网页无法后退(已在首页),不退出 app。
     */
    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) {
                    session.goBack()
                }
            }
        })
    }

    /**
     * 沉浸式全屏:隐藏状态栏和导航栏。
     */
    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupImmersiveMode()
        }
    }
}
