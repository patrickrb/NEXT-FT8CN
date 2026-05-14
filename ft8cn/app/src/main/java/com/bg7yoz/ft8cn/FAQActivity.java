package com.bg7yoz.ft8cn;
/**
 * WebView for collecting questions/feedback.
 * @author BGY70Z
 * @date 2023-03-20
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class FAQActivity extends AppCompatActivity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faqactivity);


        WebView webView = (WebView) findViewById(R.id.faqWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);       // This needs to be enabled

        /* Get the webview url. Note the word is "product" not "products"; "products" is an old parameter and using the wrong address will prevent successful submission */
        //String url = "https://www.qrz.com/db/BG7YOZ";
        String url = "https://support.qq.com/product/415890";

        /* Embedded WebViewClient allows opening web pages within the app instead of launching an external browser */
        WebViewClient webViewClient = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                super.shouldOverrideUrlLoading(view, url);
                view.loadUrl(url);
                return true;
            }
        };
        webView.setWebViewClient(webViewClient);

        webView.loadUrl(url);
    }

}