package com.example.portalapp;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessageCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // File picker handler for Excel imports, profile pictures, and UTR screenshots
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (uploadMessageCallback == null) return;

                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getData() != null) {
                            results = new Uri[]{result.getData().getData()};
                        } else if (result.getData().getClipData() != null) {
                            int count = result.getData().getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = result.getData().getClipData().getItemAt(i).getUri();
                            }
                        }
                    }
                    uploadMessageCallback.onReceiveValue(results);
                    uploadMessageCallback = null;
                }
        );

        // Initialize WebView
        webView = new WebView(this);
        setContentView(webView);

        // Hardware acceleration prevents background blur and tap-drop bugs
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // WebSettings configuration for full script, local DB, and responsive execution
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Prevent blank popup freezes by handling everything directly in one window
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Viewport and layout settings
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Keep cookies & login sessions active between app reboots
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Intercept external deep links (WhatsApp, Phone Call, UPI apps)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (url.startsWith("tel:") ||
                    url.startsWith("whatsapp:") ||
                    url.startsWith("https://wa.me/") ||
                    url.startsWith("upi:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (ActivityNotFoundException e) {
                        String targetApp = url.startsWith("upi:") ? "UPI Payment app" :
                                           url.startsWith("tel:") ? "Phone Dialer" : "WhatsApp";
                        Toast.makeText(MainActivity.this, "No compatible " + targetApp + " installed on this device.", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
                return false;
            }
        });

        // WebChromeClient bridges native Android file picker to HTML file inputs
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessageCallback != null) {
                    uploadMessageCallback.onReceiveValue(null);
                }
                uploadMessageCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    filePickerLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    uploadMessageCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open device file picker.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });

        // Load your live website URL (or file:///android_asset/index.html if local)
        webView.loadUrl("https://your-website-url.com");
    }

    // Support device hardware back button inside the WebView history
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
