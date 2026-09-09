package com.lottery.portal;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ValueCallback<Uri[]> filePathCallback;
    private final static int FILE_CHOOSER_RESULT_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Swipe to Refresh and WebView
        swipeRefreshLayout = new SwipeRefreshLayout(this);
        webView = new WebView(this);
        
        // Use hardware acceleration for smoother rendering
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        swipeRefreshLayout.addView(webView);
        setContentView(swipeRefreshLayout);

        // Configure WebSettings for full modern web support
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setSupportMultipleWindows(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Enable Cookies (Important for Supabase Auth and Session handling)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Handle Pull-to-Refresh
        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());
        
        // Prevent SwipeRefreshLayout from hijacking normal scroll gestures
        swipeRefreshLayout.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (webView.getScrollY() == 0) {
                swipeRefreshLayout.setEnabled(true);
            } else {
                swipeRefreshLayout.setEnabled(false);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefreshLayout.setRefreshing(false); // Stop loading animation
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleProtocols(request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleProtocols(url);
            }
        });

        // Setup WebChromeClient to handle File Picker & JS Alerts
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // If there's an existing callback, cancel it
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open file manager", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                result.confirm();
                return true;
            }
        });

        // Load your live website URL
        webView.loadUrl("https://ticketsnowonline.kesug.com");
    }

    // Handles native intents like WhatsApp deep links and UPI payment requests
    private boolean handleProtocols(String url) {
        if (url == null) return false;
        if (url.startsWith("whatsapp://") || url.startsWith("https://wa.me/") || url.startsWith("upi://") || url.startsWith("tel:")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            } catch (Exception e) {
                Toast.makeText(this, "App not installed to handle this action", Toast.LENGTH_SHORT).show();
                return true; // Still return true so WebView doesn't try to load the failed intent as a webpage
            }
        }
        return false; // Let WebView load standard HTTP/HTTPS links
    }

    // Captures the file selected by the user and passes it back to the HTML JavaScript
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (filePathCallback == null) return;
            
            Uri[] results = null;
            
            // Check if the user successfully picked a file
            if (resultCode == RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                } else if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                }
            }
            
            // Send results (or null if canceled) back to the WebView
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            
            // CRITICAL FIX: Force WebView to redraw to clear any lingering dark background dim
            if (webView != null) {
                webView.invalidate();
                webView.requestLayout();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // Handle physical back button presses cleanly inside the WebView history
    @@Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
