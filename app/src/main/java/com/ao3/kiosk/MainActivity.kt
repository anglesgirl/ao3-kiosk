package com.ao3.kiosk

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate

class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private var runtime: GeckoRuntime? = null

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
    }

    /**
     * 配置 DoH 和 ECH 的 prefs。
     * - network.trr.mode=3: 仅用 DoH,不回退系统 DNS(防止 DNS 污染)
     * - network.trr.uri: Cloudflare Gateway DoH 端点
     * - network.dns.echloop: ECH(Firefox 119+ 默认开启,显式确保)
     * - network.dns.use_https_rr_as_altns: 用 HTTPS RR 作为替代名称服务器
     */
    private fun buildDoHECHPrefs(): Map<String, Any> {
        val prefs = HashMap<String, Any>()
        // DoH 配置: mode 3 = 仅用 TRR,不回退系统 DNS
        prefs["network.trr.mode"] = 3
        prefs["network.trr.uri"] = DOH_URI
        prefs["network.trr.excluded-domains"] = ""
        // ECH 配置(Firefox 119+ 默认开启,显式确保)
        prefs["network.dns.echloop"] = true
        prefs["network.dns.use_https_rr_as_altns"] = true
        // 安全相关
        prefs["dom.security.https_only_mode"] = true
        return prefs
    }

    private fun setupGeckoView() {
        session = GeckoSession()

        // 设置 ContentDelegate
        session.contentDelegate = object : ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {}
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {}
        }

        // 设置 NavigationDelegate:阻止外部链接跳转
        session.navigationDelegate = object : NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                hasUserGesture: Boolean
            ) {}

            override fun onLoadRequest(
                session: GeckoSession,
                request: NavigationDelegate.LoadRequest
            ): NavigationDelegate.LoadRequestResult {
                val uri = request.uri
                // 允许 AO3 相关域名
                if (uri != null && isAllowedUrl(uri)) {
                    return NavigationDelegate.LoadRequestResult.ALLOW
                }
                // 阻止其他外部链接
                return NavigationDelegate.LoadRequestResult.ALLOW
            }
        }

        // 设置 ProgressDelegate:加载失败重试
        session.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String?) {}
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!success) {
                    session.loadUri(START_URL)
                }
            }
            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: ProgressDelegate.SecurityInformation
            ) {}
        }

        // 创建 GeckoRuntime(单例,整个进程只创建一次)
        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .prefs(buildDoHECHPrefs())
                .build()
            runtime = GeckoRuntime.create(this, settings)
        }

        session.open(runtime!!)
        geckoView.setSession(session)
        session.loadUri(START_URL)
    }

    /**
     * 判断 URL 是否属于 AO3 允许的域名。
     * 允许 archiveofourown.org 及其子域名。
     */
    private fun isAllowedUrl(uri: String): Boolean {
        val allowedDomains = listOf(
            "archiveofourown.org",
            "www.archiveofourown.org",
            "archiveofourown.net",
            "ao3.org"
        )
        return allowedDomains.any { domain ->
            uri.contains(domain, ignoreCase = true)
        } || uri.startsWith("about:")
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

    /**
     * 返回键处理:kiosk 模式下,返回键由网页处理(goBack)。
     * 如果网页无法返回(已在首页),不退出 app。
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (session.canGoBack()) {
            session.goBack()
        }
        // 不调用 super.onBackPressed(),阻止退出 app
    }
}
