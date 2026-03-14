package com.pocketpass.app.ui

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.pocketpass.app.data.SyncRepository
import com.pocketpass.app.data.UserPreferences
import com.pocketpass.app.util.LocalSoundManager
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AvatarCreatorScreen(
    onAvatarSaved: () -> Unit
) {
    val soundManager = LocalSoundManager.current
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences(context) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var miiHexData by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Check if user has saved Miis
    val miiCount by userPreferences.getMiiCount().collectAsState(initial = 0)

    // Safely save the avatar and trigger navigation when hex is received
    LaunchedEffect(miiHexData) {
        val hexData = miiHexData
        if (hexData != null && hexData.isNotBlank()) {
            userPreferences.saveAvatarHex(hexData)
            // Sync profile to Supabase so friends see the updated Mii
            try { SyncRepository(context).syncProfile() } catch (_: Exception) { }
            isSaving = false
            miiHexData = null // clear it
            // Destroy WebView before navigating to prevent crashes
            // on devices like Ayn Thor where WebGL cleanup can fail
            try {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            } catch (_: Exception) { }
            onAvatarSaved()
        } else if (hexData != null && hexData.isBlank()) {
            // If somehow we got blank data, reset saving state
            isSaving = false
            miiHexData = null
        }
    }

    // Clean up WebView when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            } catch (_: Exception) { }
        }
    }

    // Mii Maker background music
    val musicVolume by userPreferences.musicVolumeFlow.collectAsState(initial = 0.3f)
    val miiMusicRef = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        val mp = MediaPlayer.create(context, com.pocketpass.app.R.raw.mii_maker_theme)?.apply {
            isLooping = true
            setVolume(musicVolume, musicVolume)
            if (musicVolume > 0f) start()
        }
        miiMusicRef.value = mp
        onDispose {
            mp?.stop()
            mp?.release()
            miiMusicRef.value = null
        }
    }

    // Pause/resume on app background/foreground
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val mp = miiMusicRef.value ?: return@LifecycleEventObserver
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    if (mp.isPlaying) mp.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (musicVolume > 0f) mp.start()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Intercept system back button
    BackHandler(enabled = true) {
        // If user has saved Miis, go back to Plaza
        if (miiCount > 0) {
            onAvatarSaved()
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            webViewRef?.loadUrl("https://appassets.androidplatform.net/assets/miicreator/index.html")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (miiCount > 0) {
                IconButton(onClick = { soundManager.playBack(); onAvatarSaved() }) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Go Back",
                        tint = com.pocketpass.app.ui.theme.DarkText
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            Button(
                onClick = {
                    if (isSaving) return@Button
                    val webView = webViewRef ?: return@Button
                    soundManager.playSuccess()
                    isSaving = true

                    // Extract the Mii data with validation
                    try { webView.evaluateJavascript(
                        """
                        (function() {
                            try {
                                console.log('[Save] Attempting to extract Mii data...');

                                // Validate editor exists
                                if (!window.editor) {
                                    console.error('[Save] ERROR: window.editor not found!');
                                    alert('Error: Editor not ready. Please wait a moment and try again.');
                                    return 'ERROR_NO_EDITOR';
                                }

                                if (!window.editor.mii) {
                                    console.error('[Save] ERROR: window.editor.mii not found!');
                                    alert('Error: No Mii loaded. Please customize your Mii first.');
                                    return 'ERROR_NO_MII';
                                }

                                // Extract as base64 (works best with the API)
                                const studioBuffer = window.editor.mii.encodeStudio();
                                const base64 = studioBuffer.toString('base64');

                                console.log('[Save] ✓ Successfully extracted Mii data');
                                console.log('[Save] Format: base64');
                                console.log('[Save] Length:', base64.length);
                                console.log('[Save] Sample:', base64.substring(0, 40));

                                if (base64.length < 50) {
                                    console.error('[Save] ERROR: Extracted data too short!');
                                    alert('Error: Mii data seems corrupted. Please try again.');
                                    return 'ERROR_DATA_TOO_SHORT';
                                }

                                // Send to Android
                                PocketPassBridge.saveMiiHex(base64);
                                return 'SUCCESS';

                            } catch(e) {
                                console.error('[Save] FATAL ERROR:', e);
                                alert('Error saving Mii: ' + e.message);
                                return 'ERROR_EXCEPTION';
                            }
                        })();
                        """.trimIndent()
                    ) { result ->
                        // Check if save failed
                        if (result?.contains("ERROR") == true) {
                            android.util.Log.e("AvatarCreator", "Save failed: $result")
                            isSaving = false
                        }
                    }
                    } catch (e: Exception) {
                        android.util.Log.e("AvatarCreator", "evaluateJavascript crashed: ${e.message}", e)
                        isSaving = false
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.pocketpass.app.ui.theme.PocketPassGreen,
                    contentColor = com.pocketpass.app.ui.theme.OffWhite
                )
            ) {
                Text(
                    text = if (isSaving) "Saving..." else "Save & Continue",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            // WebView with editor
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                onRelease = { webView ->
                    // Explicitly clean up WebView when AndroidView is released
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (_: Exception) { }
                },
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this
                        val assetLoader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                            .build()

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            // Enable WebGL and 3D rendering
                            javaScriptCanOpenWindowsAutomatically = false
                            mediaPlaybackRequiresUserGesture = false
                            databaseEnabled = true

                            // CRITICAL: Enable network access for external API calls
                            cacheMode = WebSettings.LOAD_DEFAULT
                            blockNetworkImage = false
                            blockNetworkLoads = false

                            // Force desktop mode (user agent only - no viewport scaling)
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                            // Viewport scaling for proper layout on all screen sizes
                            useWideViewPort = true
                            loadWithOverviewMode = true

                            // Enable hardware acceleration for better WebGL performance
                            @Suppress("DEPRECATION")
                            setRenderPriority(WebSettings.RenderPriority.HIGH)
                        }

                        // Enable hardware acceleration
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    val tag = "AvatarCreator-JS"
                                    val msg = "[${it.messageLevel()}] ${it.message()} (${it.sourceId()}:${it.lineNumber()})"
                                    when (it.messageLevel()) {
                                        android.webkit.ConsoleMessage.MessageLevel.ERROR -> android.util.Log.e(tag, msg)
                                        android.webkit.ConsoleMessage.MessageLevel.WARNING -> android.util.Log.w(tag, msg)
                                        else -> android.util.Log.d(tag, msg)
                                    }
                                }
                                return true
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                android.util.Log.d("AvatarCreator", "Loading progress: $newProgress%")
                            }
                        }
                        
                        // The JavaScript interface bridge
                        addJavascriptInterface(
                            PocketPassBridge { hexData ->
                                Handler(Looper.getMainLooper()).post {
                                    miiHexData = hexData
                                }
                            },
                            "PocketPassBridge"
                        )

                        // Hide the built-in save button — we use our own "Save & Continue"
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val response = assetLoader.shouldInterceptRequest(request.url)
                                if (response != null && response.mimeType == null) {
                                    val path = request.url.path ?: ""
                                    if (path.endsWith(".bin") || path.endsWith(".obj") || path.endsWith(".gltf") || path.endsWith(".glb")) {
                                        response.mimeType = "model/gltf-binary"
                                    } else if (path.endsWith(".json")) {
                                        response.mimeType = "application/json"
                                    }
                                }
                                return response
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        // Ensure viewport meta tag exists for proper scaling
                                        var viewport = document.querySelector('meta[name="viewport"]');
                                        if (!viewport) {
                                            viewport = document.createElement('meta');
                                            viewport.name = 'viewport';
                                            document.head.appendChild(viewport);
                                        }
                                        viewport.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

                                        // Minimal CSS: only hide our save button and credits
                                        var style = document.createElement('style');
                                        style.textContent = '.tab-save { display: none !important; } .library-sidebar .sidebar-credits { display: none !important; }';
                                        document.head.appendChild(style);

                                        function hideButtons() {
                                            var btns = document.querySelectorAll('.library-sidebar .sidebar-buttons button');
                                            btns.forEach(function(btn) {
                                                var text = btn.textContent.trim().toLowerCase();
                                                if (text.indexOf('settings') !== -1 || text.indexOf('help') !== -1 || text.indexOf('contact') !== -1 || text.indexOf('about') !== -1) {
                                                    btn.style.display = 'none';
                                                }
                                            });
                                        }
                                        hideButtons();
                                        new MutationObserver(hideButtons).observe(document.body, {childList: true, subtree: true});
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                val urlStr = request?.url?.toString() ?: "unknown"
                                android.util.Log.e("AvatarCreator", "WebView error: ${error?.description} for $urlStr")
                                if (urlStr.contains(".glb")) {
                                    android.util.Log.e("AvatarCreator", "!!! 3D HEAD MODEL FETCH FAILED !!!")
                                    android.util.Log.e("AvatarCreator", "Check if WiFi is enabled and working")
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                android.util.Log.e("AvatarCreator", "HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
                            }
                        }

                        // Load from the virtual local domain
                        loadUrl("https://appassets.androidplatform.net/assets/miicreator/index.html")
                    }
                }
            )
        }
    }
}

class PocketPassBridge(private val onHexReceived: (String) -> Unit) {
    @JavascriptInterface
    fun saveMiiHex(hexData: String) {
        android.util.Log.d("PocketPassBridge", "╔════════════════════════════════════════╗")
        android.util.Log.d("PocketPassBridge", "║       MII DATA RECEIVED FROM JS        ║")
        android.util.Log.d("PocketPassBridge", "╚════════════════════════════════════════╝")
        android.util.Log.d("PocketPassBridge", "Length: ${hexData.length} chars")
        android.util.Log.d("PocketPassBridge", "Format: ${if (hexData.length == 128) "BASE64" else if (hexData.length == 192) "HEX" else "UNKNOWN/CORRUPT"}")

        if (hexData.length < 50) {
            android.util.Log.e("PocketPassBridge", "DATA TOO SHORT! This will be corrupted!")
        } else {
            android.util.Log.d("PocketPassBridge", "Data length looks good, saving...")
        }

        try {
            onHexReceived(hexData)
        } catch (e: Exception) {
            android.util.Log.e("PocketPassBridge", "Error in onHexReceived: ${e.message}", e)
        }
    }
}