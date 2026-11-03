package com.calculino.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.View;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.widget.ProgressBar;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.app.AlertDialog;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.provider.MediaStore;
import android.os.Environment;
import android.content.ContentValues;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private String fcmTokenForWebView = "";
    private static final String WEBSITE_URL = "file:///C:/Users/moken/.lmstudio/apps/bionic/projects/d49037d8-47f4-5808-9028-c707de117f8f/workspace/calculator-app/kulu.html";
    private SwipeRefreshLayout swipeRefresh;
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set system bar colors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int systemBarColor = Color.parseColor("#D86FC4");
            getWindow().setStatusBarColor(systemBarColor);
            getWindow().setNavigationBarColor(systemBarColor);
        }
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(Color.parseColor("#6366F1"));
        swipeRefresh.setOnRefreshListener(() -> {
            webView.reload();
        });
        checkAndShowRateDialog(5);
        
        setupWebView();
        
        // Enable Service Worker support (API 24+)
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            ServiceWorkerController swController = ServiceWorkerController.getInstance();
            swController.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return null;
                }
            });
        }

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        // Explicitly fetch and send FCM token at app startup
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        final String fcmToken = task.getResult();
                        fcmTokenForWebView = fcmToken;
                        android.util.Log.d("FCM_TOKEN", "Token received: " + fcmToken);

                        // Show token status via JavaScript in WebView (debug)
                        runOnUiThread(() -> {
                            if (webView != null) {
                                webView.evaluateJavascript(
                                    "window.FCM_TOKEN = '" + fcmToken + "'; window.dispatchEvent(new Event('fcm_token_ready')); console.log('FCM Token set');", null);
                            }
                        });

                        new Thread(() -> {
                            try {
                                // Endpoint is generated by PHP based on Website URL:
                                // User app  => /fcm_token.php
                                // Admin app => /admin/fcm_token.php
                                String endpoint = "file://file:///C:/Users/moken/.lmstudio/apps/bionic/projects/d49037d8-47f4-5808-9028-c707de117f8f/workspace/calculator-app/kulu.html/fcm_token.php";
                                android.util.Log.d("FCM_TOKEN", "Sending token to: " + endpoint);

                                java.net.URL tokenUrl = new java.net.URL(endpoint);
                                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) tokenUrl.openConnection();
                                conn.setRequestMethod("POST");
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setRequestProperty("User-Agent", "BPWalletApp/1.0");
                                conn.setDoOutput(true);
                                conn.setConnectTimeout(15000);
                                conn.setReadTimeout(15000);
                                String body = "{\"token\":\"" + fcmToken + "\"}";
                                try (java.io.OutputStream os = conn.getOutputStream()) {
                                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                }
                                int status = conn.getResponseCode();
                                android.util.Log.d("FCM_TOKEN", "POST response: HTTP " + status);
                                conn.disconnect();

                                // Fallback: GET request if POST fails
                                if (status < 200 || status >= 300) {
                                    android.util.Log.d("FCM_TOKEN", "POST failed, trying GET fallback...");
                                    String t = java.net.URLEncoder.encode(fcmToken, "UTF-8");
                                    java.net.URL fallbackUrl = new java.net.URL(endpoint + "?token=" + t);
                                    java.net.HttpURLConnection fallbackConn = (java.net.HttpURLConnection) fallbackUrl.openConnection();
                                    fallbackConn.setRequestMethod("GET");
                                    fallbackConn.setRequestProperty("User-Agent", "BPWalletApp/1.0");
                                    fallbackConn.setConnectTimeout(15000);
                                    fallbackConn.setReadTimeout(15000);
                                    int fbStatus = fallbackConn.getResponseCode();
                                    android.util.Log.d("FCM_TOKEN", "GET fallback response: HTTP " + fbStatus);
                                    fallbackConn.disconnect();
                                }
                            } catch (Exception e) {
                                android.util.Log.e("FCM_TOKEN", "Error sending token: " + e.getMessage(), e);
                            }
                        }).start();
                    } else {
                        android.util.Log.e("FCM_TOKEN", "Failed to get token: " + (task.getException() != null ? task.getException().getMessage() : "unknown error"));
                        // Show error as Toast so user can see
                        runOnUiThread(() -> {
                            String errMsg = task.getException() != null ? task.getException().getMessage() : "Unknown FCM error";
                            Toast.makeText(MainActivity.this, "FCM Error: " + errMsg, Toast.LENGTH_LONG).show();
                        });
                    }
                });
        } catch (Exception e) {
            android.util.Log.e("FCM_TOKEN", "Firebase init error: " + e.getMessage(), e);
            Toast.makeText(this, "Firebase Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        // Schedule background notification polling every 15 minutes
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
            NotificationWorker.class, 15, TimeUnit.MINUTES)
            .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notification_check",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest);
        
        
        
        // Handle deep link intent
        handleIntent(getIntent());
        
        // Load directly; ConnectivityManager can be unreliable on some devices/VPNs.
        // WebView will show its own error page if the connection is actually unavailable.
        webView.loadUrl(WEBSITE_URL);
    }

    private void checkAndShowRateDialog(int targetLaunches) {
        android.content.SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean ratedAlready = prefs.getBoolean("rated", false);
        if (ratedAlready) return;
        int launches = prefs.getInt("launch_count", 0) + 1;
        prefs.edit().putInt("launch_count", launches).apply();
        if (launches == targetLaunches) {
            new AlertDialog.Builder(this)
                .setTitle("Enjoying the app?")
                .setMessage("If you like the app, please take a moment to rate it. It won't take more than a minute. Thanks!")
                .setPositiveButton("Rate Now", (d, w) -> {
                    prefs.edit().putBoolean("rated", true).apply();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
                    } catch (android.content.ActivityNotFoundException e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                    }
                })
                .setNeutralButton("Remind Later", null)
                .setNegativeButton("No Thanks", (d, w) -> prefs.edit().putBoolean("rated", true).apply())
                .show();
        }
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDatabaseEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                // Save cookies for background notification worker
                String cookies = CookieManager.getInstance().getCookie(WEBSITE_URL);
                if (cookies != null && !cookies.isEmpty()) {
                    getSharedPreferences("bp_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("session_cookies", cookies)
                        .putString("website_url", WEBSITE_URL)
                        .apply();
                }
                // Re-inject FCM token after every page load so website JS can POST it with user_id/session.
                if (fcmTokenForWebView != null && !fcmTokenForWebView.isEmpty()) {
                    view.evaluateJavascript("window.FCM_TOKEN = '" + fcmTokenForWebView + "'; window.dispatchEvent(new Event('fcm_token_ready'));", null);
                }
                // Custom JavaScript injection
                view.evaluateJavascript("/**    * Basic Scientific Calculator Logic    * Handles arithmetic, scientific functions, memory, and history.    * Supports chained expression building (e.g., \"5+6+7\").    */    // State variables   let currentOperand = \'0\';   let previousOperand = \'\'; // Now stores the full expression string (e.g., \"5 + 6 +\")   let operation = undefined;   let shouldResetPrevious = false;   let memory = 0;    // DOM Elements   const currentOperandDisplay = document.getElementById(\'current-operand\');   const previousOperandDisplay = document.getElementById(\'previous-operand\');   const historyList = document.getElementById(\'history-list\');    /**    * Updates the display elements    */   function updateDisplay() {       currentOperandDisplay.innerText = currentOperand;       // The first line now shows the entire expression built so far       previousOperandDisplay.innerText = previousOperand;   }    /**    * Appends a number to the current operand    */   function appendNumber(number) {       if (currentOperand === \'0\' || shouldResetPrevious) {           currentOperand = number;           shouldResetPrevious = false;       } else {           currentOperand = currentOperand + number;       }       updateDisplay();   }    /**    * Appends a decimal point to the current operand    */   function appendDecimal() {       if (shouldResetPrevious) {           currentOperand = \'0.\';           shouldResetPrevious = false;       } else if (!currentOperand.includes(\'.\')) {           currentOperand = currentOperand + \'.\';       }       updateDisplay();   }    /**    * Appends a constant value (e.g., pi)    */   function appendConstant(constant) {       if (shouldResetPrevious) {           currentOperand = constant === \'pi\' ? String(Math.PI.toFixed(8)) : String(Math.E.toFixed(8));           shouldResetPrevious = false;       } else {           currentOperand = currentOperand + (constant === \'pi\' ? Math.PI.toFixed(8) : Math.E.toFixed(8));       }       updateDisplay();   }    /**    * Builds the expression string as operators are pressed    */   function chooseOperation(op) {       if (operation === undefined) {           // First operator pressed: \"5\" becomes \"5 +\"           if (currentOperand !== \'0\') {               previousOperand = currentOperand + \" \" + op;           } else {               previousOperand = op;           }           operation = op;       } else {           // Subsequent operators: \"5 +\" becomes \"5 + 6 +\"           previousOperand += \" \" + currentOperand + \" \" + op;           currentOperand = \'0\';       }       shouldResetPrevious = true;       updateDisplay();   }    /**    * Performs the final calculation of the built expression    */   function calculate() {       if (operation === undefined || shouldResetPrevious) {           return;       }        // Combine the expression string with the final number being typed       const fullExpression = previousOperand + \" \" + currentOperand;        // Evaluate the expression string       const result = evaluateExpression(fullExpression);        // Update History       addToHistory(fullExpression, result);        currentOperand = String(result);       operation = undefined;       previousOperand = \'\';       shouldResetPrevious = false;       updateDisplay();   }    /**    * Simple expression evaluator for +, -, *, /    * Handles basic order of operations (multiplication/division before addition/subtraction)    */   function evaluateExpression(expr) {       try {           // Tokenize the string into numbers and operators           // This handles basic expressions like \"5 + 6 * 7\"           // For a basic calculator, we\'ll use a simple two-pass approach            // Pass 1: Handle multiplication and division           let tokens = expr.split(\' \');           for (let i = 0; i < tokens.length; i++) {               if (tokens[i] === \'*\' || tokens[i] === \'/\') {                   const prev = parseFloat(tokens[i - 1]);                   const next = parseFloat(tokens[i + 1]);                   let res = 0;                   if (tokens[i] === \'*\') res = prev * next;                   if (tokens[i] === \'/\') res = prev / next;                    tokens.splice(i - 1, 3, res.toString());                   i--; // Adjust index after splice               }           }            // Pass 2: Handle addition and subtraction           let total = parseFloat(tokens[0]);           for (let i = 1; i < tokens.length; i += 2) {               const op = tokens[i];               const val = parseFloat(tokens[i + 1]);               if (op === \'+\') total += val;               if (op === \'-\') total -= val;           }            return total;       } catch (e) {           return \"Error\";       }   }    /**    * Scientific Functions    * These are treated as unary operations on the current operand.    */   function mathFunc(type) {       let val = parseFloat(currentOperand);       let result = 0;        switch (type) {           case \'sin\': result = Math.sin(val * Math.PI / 180); break; // Degrees           case \'cos\': result = Math.cos(val * Math.PI / 180); break;           case \'tan\': result = Math.tan(val * Math.PI / 180); break;           case \'log\': result = Math.log10(val); break;           case \'sqrt\': result = Math.sqrt(val); break;           case \'pow\':               result = Math.pow(val, 2);               break;           case \'exp\': result = Math.exp(val); break;           case \'fact\':               result = factorial(val);               break;       }        if (isNaN(result) || !isFinite(result)) {           currentOperand = \"Error\";       } else {           currentOperand = String(Number(result.toFixed(8)));       }       updateDisplay();   }    /**    * Factorial helper    */   function factorial(n) {       if (n < 0) return \"Error\";       if (n === 0 || n === 1) return 1;       let res = 1;       for (let i = 2; i <= n; i++) res *= i;       return res;   }    /**    * Memory Functions    */   function memoryClear() {       memory = 0;       alert(\"Memory Cleared\");   }    function memoryRecall() {       currentOperand = String(memory);       updateDisplay();   }    function memoryAdd() {       memory += parseFloat(currentOperand) || 0;   }    function memorySub() {       memory -= parseFloat(currentOperand) || 0;   }    /**    * History Management    */   function addToHistory(expression, result) {       const historyItem = document.createElement(\'div\');       historyItem.className = \'history-item\';       historyItem.innerHTML = `<span class=\"expr\">${expression}</span><br><strong>${result}</strong>`;       historyList.prepend(historyItem);   }    function clearHistory() {       historyList.innerHTML = \'\';   }    /**    * Clear everything    */   function clearCalculator() {       currentOperand = \'0\';       previousOperand = \'\';       operation = undefined;       shouldResetPrevious = false;       updateDisplay();   }    /**    * Toggle \"Scientific\" mode visual    */   function toggleMode() {       const sciRows = document.querySelectorAll(\'.sci-row\');       sciRows.forEach(row => {           row.style.display = (row.style.display === \'none\') ? \'grid\' : \'none\';       });   }    // Initialize display   updateDisplay()", null);
                // Custom CSS injection via Base64 to avoid quote escaping issues
                String cssStr = "@import url(\'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600&display=swap\');    :root {       /* Palette - Premium Dark Mode */       --bg-color: #121214;       --calc-bg: #1c1c1e;       --display-bg: #000000;       --text-primary: #ffffff;       --text-secondary: #a1a1a6;        /* Button Colors */       --btn-num-bg: #2c2c2e;       --btn-op-bg: #ff9500;       --btn-op-hover: #ffaa33;       --btn-clear-bg: #3a3a3c;       --btn-sci-bg: #252527;        /* Shadows & Effects */       --shadow-main: 0 15px 40px rgba(0, 0, 0, 0.7);       --shadow-inner: inset 2px 2px 5px rgba(0, 0, 0, 0.5),                        inset -1px -1px 2px rgba(255, 255, 255, 0.05);       --glass-bg: rgba(28, 28, 30, 0.7);       --glass-border: rgba(255, 255, 255, 0.1);   }    * {       box-sizing: border-box;       margin: 0;       padding: 0;       font-family: \'Inter\', -apple-system, BlinkMacSystemFont, sans-serif;   }    body {       background: radial-gradient(circle at center, #2c2c2e 0%, #000000 100%);       display: flex;       justify-content: center;       align-items: center;       min-height: 100vh;       color: var(--text-primary);   }    .main-container {       display: flex;       gap: 2rem;       padding: 20px;       flex-wrap: wrap;       justify-content: center;       align-items: center;   }    /* Calculator Chassis - Made slightly more compact */   .calculator {       background-color: var(--calc-bg);       padding: 18px;       border-radius: 32px;       box-shadow: var(--shadow-main), 0 0 0 1px rgba(255,255,255,0.05);       width: 340px;       border: 1px solid rgba(255,255,255,0.05);   }    /* Display Area - Slightly reduced size */   .display {       background-color: var(--display-bg);       padding: 20px;       border-radius: 20px;       text-align: right;       margin-bottom: 18px;       min-height: 100px;       display: flex;       flex-direction: column;       justify-content: flex-end;       border: 1px solid rgba(255,255,255,0.05);       box-shadow: inset 0 4px 10px rgba(0,0,0,1);   }    #previous-operand {       color: var(--text-secondary);       font-size: 1rem;       font-weight: 300;       margin-bottom: 5px;       min-height: 1.2rem;   }    #current-operand {       font-size: 2.8rem;       font-weight: 600;       letter-spacing: -1px;       word-wrap: break-word;       word-break: break-all;       line-height: 1;   }    /* Button Grid - More compact spacing */   .buttons {       display: grid;       grid-template-columns: repeat(4, 1fr);       gap: 9px;   }    button {       height: 56px; /* Reduced from 64px */       padding: 0;       border: none;       border-radius: 14px;       background-color: var(--btn-num-bg);       color: white;       font-size: 1.2rem;       font-weight: 500;       cursor: pointer;       transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);       box-shadow: var(--shadow-inner);       border: 1px solid rgba(255,255,255,0.03);   }    button:hover {       transform: translateY(-2px);       filter: brightness(1.2);       box-shadow: 0 8px 15px rgba(0,0,0,0.4);   }    button:active {       transform: translateY(1px);       box-shadow: inset 4px 4px 8px rgba(0,0,0,0.8);   }    .mode-toggle {       background: linear-gradient(145deg, #3498db, #2980b9);       font-size: 1.2rem;   }    .clear {       background-color: var(--btn-clear-bg);   }    .operator {       background-color: var(--btn-op-bg);       color: white;   }    .operator:hover {       background-color: var(--btn-op-hover);   }    /* Repositioned Equals Button - No longer spans 2 rows */   .equals {       background-color: #34c759;       grid-column: 4;       grid-row: 4;   }    .equals:hover {       background-color: #4cd964;   }    /* Decimal Button - Spans 2 columns to fill the width */   .decimal {       grid-column: span 2;   }    /* Scientific Section - Even more compact */   .sci-row {       grid-column: span 4;       display: grid;       grid-template-columns: repeat(4, 1fr);       gap: 6px;       margin: 4px 0;       padding: 6px;       background: rgba(255,255,255,0.02);       border-radius: 12px;       border: 1px solid rgba(255,255,255,0.03);   }    .sci-btn {       height: 36px;       padding: 0;       font-size: 0.75rem;       background-color: var(--btn-sci-bg);       color: var(--text-secondary);   }    /* History Panel - Sized to match */   .history-panel {       background: var(--glass-bg);       backdrop-filter: blur(12px);       -webkit-backdrop-filter: blur(12px);       padding: 20px;       border-radius: 32px;       width: 280px;       max-height: 550px;       display: flex;       flex-direction: column;       box-shadow: var(--shadow-main);       border: 1px solid var(--glass-border);   }    .history-panel h3 {       font-weight: 600;       margin-bottom: 12px;       color: var(--text-primary);   }    #history-list {       flex-grow: 1;       overflow-y: auto;       margin: 0 0 15px 0;       padding-right: 8px;   }    .history-item {       padding: 10px;       border-bottom: 1px solid rgba(255,255,255,0.05);       font-size: 0.85rem;   }    .history-item .expr {       color: var(--text-secondary);       display: block;       margin-bottom: 3px;   }    .clear-history-btn {       background-color: rgba(255,255,255,0.1);       font-size: 0.8rem;       padding: 10px;       border-radius: 10px;   }    /* Scrollbar Styling */   #history-list::-webkit-scrollbar {       width: 4px;   }   #history-list::-webkit-scrollbar-thumb {       background: rgba(255,255,255,0.2);       border-radius: 10px;";
                String b64Css = android.util.Base64.encodeToString(cssStr.getBytes(), android.util.Base64.NO_WRAP);
                view.evaluateJavascript("(function(){var s=document.createElement('style');s.textContent=atob('" + b64Css + "');document.head.appendChild(s);})()", null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleWebViewUrl(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null && request.getUrl() != null) {
                    return handleWebViewUrl(view, request.getUrl().toString());
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) progressBar.setProgress(newProgress);
            }
            
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                // Camera intent
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, "camera_photo");
                cameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);

                // File chooser intent
                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fileIntent.setType("*/*");

                // Combine into chooser
                Intent chooserIntent = Intent.createChooser(fileIntent, "Select file");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});

                fileUploadLauncher.launch(chooserIntent);
                return true;
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_LONG).show();
            }
        });
    }


    private boolean handleWebViewUrl(WebView view, String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("tel:") || lower.startsWith("mailto:") || lower.startsWith("sms:") || lower.startsWith("smsto:") || lower.startsWith("whatsapp:") || lower.startsWith("market:") || lower.startsWith("intent:")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                startActivity(intent);
                return true;
            } catch (Exception ignored) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                    return true;
                } catch (Exception ignoredAgain) {
                    return true;
                }
            }
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return true;
        }
        try {
            java.net.URL baseUrl = new java.net.URL(WEBSITE_URL);
            java.net.URL targetUrl = new java.net.URL(url);
            if (targetUrl.getHost() != null && !targetUrl.getHost().equalsIgnoreCase(baseUrl.getHost())) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                startActivity(browserIntent);
                return true;
            }
        } catch (Exception e) { /* ignore, load in webview */ }
        view.loadUrl(url);
        return true;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String deepUrl = intent.getData().toString();
            if (deepUrl.startsWith("http")) {
                webView.loadUrl(deepUrl);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private final ActivityResultLauncher<Intent> fileUploadLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileUploadCallback == null) return;
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                fileUploadCallback.onReceiveValue(new Uri[]{result.getData().getData()});
            } else if (result.getResultCode() == RESULT_OK && cameraImageUri != null) {
                fileUploadCallback.onReceiveValue(new Uri[]{cameraImageUri});
            } else {
                fileUploadCallback.onReceiveValue(null);
            }
            fileUploadCallback = null;
        });

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}