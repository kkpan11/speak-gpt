/**************************************************************************
 * Copyright (c) 2023-2025 Dmytro Ostapenko. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **************************************************************************/

package org.teslasoft.assistant.ui.activities

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.navigation.NavigationBarView
import org.teslasoft.assistant.R
import org.teslasoft.assistant.preferences.ApiEndpointPreferences
import org.teslasoft.assistant.preferences.DeviceInfoProvider
import org.teslasoft.assistant.preferences.GlobalPreferences
import org.teslasoft.assistant.preferences.Logger
import org.teslasoft.assistant.preferences.Preferences
import org.teslasoft.assistant.pwa.PWAActivity
import org.teslasoft.assistant.theme.ThemeManager
import org.teslasoft.assistant.ui.fragments.tabs.ChatsListFragment
import org.teslasoft.assistant.ui.fragments.tabs.ExploreFragment
import org.teslasoft.assistant.ui.fragments.tabs.PlaygroundFragment
import org.teslasoft.assistant.ui.fragments.tabs.PromptsFragment
import org.teslasoft.assistant.ui.fragments.tabs.ToolsFragment
import org.teslasoft.assistant.ui.onboarding.WelcomeActivity
import org.teslasoft.assistant.util.WindowInsetsUtil
import org.teslasoft.core.auth.SystemInfo
import org.teslasoft.core.auth.internal.ApplicationSignature
import java.util.EnumSet
import androidx.core.graphics.drawable.toDrawable
import eightbitlab.com.blurview.BlurView

class MainActivity : FragmentActivity(), Preferences.PreferencesChangedListener {

    private var navigationBar: BottomNavigationView? = null
    private var fragmentContainer: ConstraintLayout? = null
    private var btnDebugger: ImageButton? = null
    private var debuggerWindow: ConstraintLayout? = null
    private var btnCloseDebugger: ImageButton? = null
    private var btnInitiateCrash: MaterialButton? = null
    private var btnLaunchPWA: MaterialButton? = null
    private var btnTogglePWA: MaterialButton? = null
    private var threadLoader: LinearLayout? = null
    private var devIds: TextView? = null
    private var frameChats: Fragment? = null
    private var framePlayground: Fragment? = null
    private var frameTools: Fragment? = null
    private var framePrompts: Fragment? = null
    private var frameExplore: Fragment? = null
    private var root: ConstraintLayout? = null
    private var preferences: Preferences? = null
    private var btnDebugActivity: MaterialButton? = null
    private var needsRestart: Boolean = false
    private var selectedTab: Int = 1
    private var isInitialized: Boolean = false
    private var splashScreen: SplashScreen? = null
    private var debugBlurView: BlurView? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 30) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }

        super.onCreate(savedInstanceState)

        splashScreen = installSplashScreen()
        splashScreen?.setKeepOnScreenCondition { true }

        val consent: SharedPreferences = getSharedPreferences("setup", MODE_PRIVATE)

        if (!consent.getBoolean("setup", false)) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        preferences = Preferences.getPreferences(this, "").addOnPreferencesChangedListener(this)

        navigationBar = findViewById(R.id.navigation_bar)

        fragmentContainer = findViewById(R.id.fragment)
        root = findViewById(R.id.root)
        btnDebugger = findViewById(R.id.btn_open_debugger)
        debuggerWindow = findViewById(R.id.debugger_window)
        btnCloseDebugger = findViewById(R.id.btn_close_debugger)
        btnInitiateCrash = findViewById(R.id.btn_initiate_crash)
        btnDebugActivity = findViewById(R.id.btn_debug_activity)
        btnLaunchPWA = findViewById(R.id.btn_launch_pwa)
        btnTogglePWA = findViewById(R.id.btn_toggle_pwa)
        devIds = findViewById(R.id.dev_ids)
        threadLoader = findViewById(R.id.thread_loader)
        debugBlurView = findViewById(R.id.debug_blur)

        val decorView = window.decorView
        val rootView: ViewGroup = decorView.findViewById(android.R.id.content)
        val windowBackground = decorView.background

        debugBlurView?.setupWith(rootView)
            ?.setFrameClearDrawable(windowBackground)
            ?.setBlurRadius(24f)

        threadLoader?.visibility = View.VISIBLE

        btnDebugger?.visibility = View.GONE
        debuggerWindow?.visibility = View.GONE

        preloadAmoled()

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                if (debuggerWindow?.visibility == View.VISIBLE) {
                    debuggerWindow?.visibility = View.GONE
                } else {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.label_confirm_exit)
                        .setMessage(R.string.msg_confirm_exit)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            finish()
                        }
                        .setNegativeButton(R.string.no) { _, _ -> }
                        .show()
                }
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (debuggerWindow?.visibility == View.VISIBLE) {
                        debuggerWindow?.visibility = View.GONE
                    } else {
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(R.string.label_confirm_exit)
                            .setMessage(R.string.msg_confirm_exit)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                finish()
                            }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .show()
                    }
                }
            })
        }

        if (isPWAActivityEnabled(this)) {
            btnTogglePWA?.text = "Disable PWA"
        } else {
            btnTogglePWA?.text = "Enable PWA"
        }

        btnTogglePWA?.setOnClickListener {
            val pm = packageManager
            if (isPWAActivityEnabled(this)) {
                btnTogglePWA?.text = "Enable PWA"
                pm.setComponentEnabledSetting(
                    ComponentName(this, "org.teslasoft.assistant.pwa.PWAActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
                )
                Logger.log(this, "event", "ComponentManager", "info", "Component disabled: org.teslasoft.assistant.pwa.PWAActivity")
            } else {
                btnTogglePWA?.text = "Disable PWA"
                pm.setComponentEnabledSetting(
                    ComponentName(this, "org.teslasoft.assistant.pwa.PWAActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
                )
                Logger.log(this, "event", "ComponentManager", "info", "Component enabled: org.teslasoft.assistant.pwa.PWAActivity")
            }
        }

        Thread {
            DeviceInfoProvider.assignInstallationId(this)

            runOnUiThread {
                navigationBar!!.setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener { item: MenuItem ->
                    when (item.itemId) {
                        R.id.menu_chat -> {
                            menuChats()
                            return@OnItemSelectedListener true
                        }
                        R.id.menu_playground -> {
                            menuPlayground()
                            return@OnItemSelectedListener true
                        }
                        R.id.menu_tools -> {
                            menuTools()
                            return@OnItemSelectedListener true
                        }
                        R.id.menu_prompts -> {
                            menuPrompts()
                            return@OnItemSelectedListener true
                        }
                        R.id.menu_tips -> {
                            menuExplore()
                            return@OnItemSelectedListener true
                        }
                    }

                    return@OnItemSelectedListener false
                })

                val installationId = DeviceInfoProvider.getInstallationId(this)
                val androidId = DeviceInfoProvider.getAndroidId(this)

                if (preferences!!.getDebugMode()) {
                    btnDebugger?.visibility = View.VISIBLE
                    btnDebugger?.setOnClickListener {
                        debuggerWindow?.visibility = View.VISIBLE
                    }

                    btnCloseDebugger?.setOnClickListener {
                        debuggerWindow?.visibility = View.GONE
                    }

                    btnInitiateCrash?.setOnClickListener {
                        throw RuntimeException("Test crash")
                    }

                    btnDebugActivity?.setOnClickListener {
                        startActivity(Intent(this, DebugMaterial::class.java))
                    }

                    btnLaunchPWA?.setOnClickListener {
                        if (isPWAActivityEnabled(this)) {
                            startActivity(Intent(this, PWAActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            MaterialAlertDialogBuilder(this)
                                .setMessage("This component is disabled by the component manager.")
                                .setPositiveButton(R.string.btn_close) { _, _ -> }
                                .show()
                        }
                    }

                    var androidVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Build.VERSION.RELEASE_OR_PREVIEW_DISPLAY else Build.VERSION.RELEASE

                    if (androidVersion.lowercase() == "baklava") {
                        androidVersion = "16 (Beta)"
                    }

                    val pm = packageManager
                    val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { pm.getInstallSourceInfo(packageName).installingPackageName } else { "<Current OS version is not supported>" }

                    val signature = ApplicationSignature(this)
                    val sha1 = signature.getCertificateFingerprint("SHA1")
                    val sha256 = signature.getCertificateFingerprint("SHA256")

                    devIds?.text = "${devIds?.text}\n\nInstallation ID: $installationId\nAndroid ID: $androidId"
                    devIds?.text = "${devIds?.text}\nApp Version: ${packageManager.getPackageInfo(packageName, 0).versionName} (${packageManager.getPackageInfo(packageName, 0).versionCode})"
                    devIds?.text = "${devIds?.text}\nTeslasoft ID version: ${SystemInfo.NAME} ${SystemInfo.VERSION} (${SystemInfo.VERSION_CODE})"
                    devIds?.text = "${devIds?.text}\nKotlin language version: ${KotlinVersion.CURRENT}"
                    devIds?.text = "${devIds?.text}\nJava language version: 21 (LTS)"
                    devIds?.text = "${devIds?.text}\nRuntime version: ${System.getProperty("java.runtime.name")} version ${System.getProperty("java.runtime.version")}"
                    devIds?.text = "${devIds?.text}\nOS: Android"
                    devIds?.text = "${devIds?.text}\nOS version: $androidVersion"
                    devIds?.text = "${devIds?.text}\nFingerprint: ${Build.FINGERPRINT}"
                    devIds?.text = "${devIds?.text}\nManufacturer: ${Build.MANUFACTURER}"
                    devIds?.text = "${devIds?.text}\nModel: ${Build.MODEL}"
                    devIds?.text = "${devIds?.text}\nProduct: ${Build.PRODUCT}"
                    devIds?.text = "${devIds?.text}\nBrand: ${Build.BRAND}"
                    devIds?.text = "${devIds?.text}\nInstall Source: $pi"
                    devIds?.text = "${devIds?.text}\nPackage Certificate SHA1: $sha1"
                    devIds?.text = "${devIds?.text}\nPackage Certificate SHA256: $sha256"
                }

                preInit()

                if (savedInstanceState != null) {
                    adjustPaddings()
                    onRestoredState(savedInstanceState)
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    val fadeOut: Animation = AnimationUtils.loadAnimation(this, R.anim.fade_out)
                    threadLoader?.startAnimation(fadeOut)

                    fadeOut.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(animation: Animation) { /* UNUSED */ }
                        override fun onAnimationEnd(animation: Animation) {
                            runOnUiThread {
                                threadLoader?.visibility = View.GONE
                                threadLoader?.elevation = 0.0f

                                isInitialized = true
                            }
                        }

                        override fun onAnimationRepeat(animation: Animation) { /* UNUSED */ }
                    })
                }, 50)
            }
        }.start()
    }

    private fun preInit() {
        val apiEndpointPreferences = ApiEndpointPreferences.getApiEndpointPreferences(this)

        if (apiEndpointPreferences.getApiEndpoint(this, preferences!!.getApiEndpointId()).apiKey == "") {
            if (preferences!!.getApiKey(this) == "") {
                if (preferences!!.getOldApiKey() == "") {
                    startActivity(Intent(this, WelcomeActivity::class.java).setAction(Intent.ACTION_VIEW))
                    getSharedPreferences("chat_list", MODE_PRIVATE)?.edit()?.putString("data", "[]")?.apply()
                    finish()
                } else {
                    preferences!!.secureApiKey(this)
                    apiEndpointPreferences.migrateFromLegacyEndpoint(this)
                    initUI()
                }
            } else {
                apiEndpointPreferences.migrateFromLegacyEndpoint(this)
                initUI()
            }
        } else {
            initUI()
        }
    }

    private fun initUI() {
        frameChats = ChatsListFragment()
        framePlayground = PlaygroundFragment()
        frameTools = ToolsFragment()
        framePrompts = PromptsFragment()
        frameExplore = ExploreFragment()

        loadFragment(frameChats, 1, 1)
        reloadAmoled()
        splashScreen?.setKeepOnScreenCondition { false }
    }

    private fun isPWAActivityEnabled(context: Context): Boolean {
        try {
            val manager = context.packageManager
            val componentName = ComponentName(context, "org.teslasoft.assistant.pwa.PWAActivity")
            manager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
            return true
        } catch (_: Exception) { /* unused */ }

        return false
    }

    private fun restartActivity() {
        recreate()
    }

    override fun onResume() {
        if (needsRestart) {
            restartActivity()
        }

        super.onResume()

        if (isInitialized) {
            // Reset preferences singleton to global settings
            preferences = Preferences.getPreferences(this, "")

            reloadAmoled()
        }
    }

    @Suppress("DEPRECATION")
    private fun reloadAmoled() {
        if (isDarkThemeEnabled() && preferences?.getAmoledPitchBlack()!!) {
            if (Build.VERSION.SDK_INT < 30) {
                window.navigationBarColor = ResourcesCompat.getColor(resources, R.color.amoled_accent_100, theme)
                window.statusBarColor = ResourcesCompat.getColor(resources, R.color.amoled_window_background, theme)
            }
            window.setBackgroundDrawableResource(R.color.amoled_window_background)
            navigationBar!!.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.amoled_accent_100, theme))

            btnDebugger?.background = ResourcesCompat.getDrawable(resources, R.drawable.btn_accent_tonal_amoled, theme)
            btnCloseDebugger?.background = ResourcesCompat.getDrawable(resources, R.drawable.btn_accent_tonal_amoled, theme)
        } else {
            if (Build.VERSION.SDK_INT < 30) {
                window.navigationBarColor = SurfaceColors.SURFACE_3.getColor(this)
                window.statusBarColor = SurfaceColors.SURFACE_0.getColor(this)
            }
            val colorDrawable = SurfaceColors.SURFACE_0.getColor(this).toDrawable()
            window.setBackgroundDrawable(colorDrawable)
            navigationBar!!.setBackgroundColor(SurfaceColors.SURFACE_3.getColor(this))

            btnDebugger?.background = getDisabledDrawable(ResourcesCompat.getDrawable(resources, R.drawable.btn_accent_tonal, theme)!!)
            btnCloseDebugger?.background = getDisabledDrawable(ResourcesCompat.getDrawable(resources, R.drawable.btn_accent_tonal, theme)!!)
        }

        (frameChats as ChatsListFragment).reloadAmoled(this)
        (framePrompts as PromptsFragment).reloadAmoled(this)
    }

    @Suppress("DEPRECATION")
    private fun preloadAmoled() {
        ThemeManager.getThemeManager().applyTheme(this, isDarkThemeEnabled() && GlobalPreferences.getPreferences(this).getAmoledPitchBlack())
        if (isDarkThemeEnabled() && preferences?.getAmoledPitchBlack()!!) {
            if (Build.VERSION.SDK_INT < 30) {
                window.navigationBarColor = SurfaceColors.SURFACE_0.getColor(this)
                window.statusBarColor = ResourcesCompat.getColor(resources, R.color.amoled_window_background, theme)
            }
            threadLoader?.background = ResourcesCompat.getDrawable(resources, R.color.amoled_window_background, null)
        } else {
            if (Build.VERSION.SDK_INT < 30) {
                window.navigationBarColor = SurfaceColors.SURFACE_3.getColor(this)
                window.statusBarColor = SurfaceColors.SURFACE_0.getColor(this)
            }
            threadLoader?.setBackgroundColor(SurfaceColors.SURFACE_0.getColor(this))
        }
    }

    private fun getDisabledDrawable(drawable: Drawable) : Drawable {
        DrawableCompat.setTint(DrawableCompat.wrap(drawable), getDisabledColor())
        return drawable
    }

    private fun getDisabledColor() : Int {
        return if (isDarkThemeEnabled() && preferences?.getAmoledPitchBlack()!!) {
            ResourcesCompat.getColor(resources, R.color.amoled_accent_100, theme)
        } else {
            SurfaceColors.SURFACE_5.getColor(this)
        }
    }

    private fun isDarkThemeEnabled(): Boolean {
        return when (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> false
            else -> false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", selectedTab)
        super.onSaveInstanceState(outState)
    }

    private fun menuChats() {
        val st = selectedTab
        selectedTab = 1
        loadFragment(frameChats, st, selectedTab)
    }

    private fun menuPlayground() {
        val st = selectedTab
        selectedTab = 2
        loadFragment(framePlayground, st, selectedTab)
    }

    private fun menuTools() {
        val st = selectedTab
        selectedTab = 3
        loadFragment(frameTools, st, selectedTab)
    }

    private fun menuPrompts() {
        val st = selectedTab
        selectedTab = 4
        loadFragment(framePrompts, st, selectedTab)
    }

    private fun menuExplore() {
        val st = selectedTab
        selectedTab = 5
        loadFragment(frameExplore, st, selectedTab)
    }

    private fun onRestoredState(savedInstanceState: Bundle?) {
        selectedTab = savedInstanceState!!.getInt("tab")

        when (selectedTab) {
            1 -> {
                navigationBar?.selectedItemId = R.id.menu_chat
                loadFragment(frameChats, 1, 1)
            }
            2 -> {
                navigationBar?.selectedItemId = R.id.menu_playground
                loadFragment(framePlayground, 1, 1)
            }
            3 -> {
                navigationBar?.selectedItemId = R.id.menu_tools
                loadFragment(frameTools, 1, 1)
            }
            4 -> {
                navigationBar?.selectedItemId = R.id.menu_prompts
                loadFragment(framePrompts, 1, 1)
            }
            5 -> {
                navigationBar?.selectedItemId = R.id.menu_tips
                loadFragment(frameExplore, 1, 1)
            }
        }
    }

    override fun onPreferencesChanged(key: String, value: String) {
        if (key == "debug_mode" || key == "amoled_pitch_black" || key == "hide_model_names" || key == "monochrome_background_for_chat_list") {
            needsRestart = true
        }
    }

    private fun loadFragment(fragment: Fragment?, newTab: Int, prevTab: Int): Boolean {
        if (fragment != null) {
            try {
                val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
                if (newTab < prevTab) {
                    transaction.setCustomAnimations(R.anim.mtrl_fragment_open_enter, R.anim.mtrl_fragment_open_exit)
                } else if (newTab > prevTab) {
                    transaction.setCustomAnimations(R.anim.mtrl_fragment_close_enter, R.anim.mtrl_fragment_close_exit)
                } else {
                    transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                }
                transaction.replace(R.id.fragment, fragment)
                transaction.commit()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        adjustPaddings()
    }

    private fun adjustPaddings() {
        WindowInsetsUtil.adjustPaddings(this, R.id.navigation_bar, EnumSet.of(WindowInsetsUtil.Companion.Flags.STATUS_BAR, WindowInsetsUtil.Companion.Flags.IGNORE_PADDINGS))
        WindowInsetsUtil.adjustPaddings(this, R.id.debug_btn_keeper, EnumSet.of(WindowInsetsUtil.Companion.Flags.STATUS_BAR, WindowInsetsUtil.Companion.Flags.IGNORE_PADDINGS))
        WindowInsetsUtil.adjustPaddings(this, R.id.d, EnumSet.of(WindowInsetsUtil.Companion.Flags.STATUS_BAR, WindowInsetsUtil.Companion.Flags.NAVIGATION_BAR, WindowInsetsUtil.Companion.Flags.IGNORE_PADDINGS))
    }
}
