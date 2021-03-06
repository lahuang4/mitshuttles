package edu.mit.web.lahuang4.mitshuttles;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ShuttleMap extends AppCompatActivity {

    private static final String TAG = "ShuttleMap";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Construct URL from arguments
        String agency = getIntent().getStringExtra("Agency");
        String routeTag = getIntent().getStringExtra("Route Tag");
        String url = "http://www.nextbus.com/googleMap/?a=" + agency + "&r=" + routeTag;
        if (getIntent().hasExtra("Direction") && getIntent().hasExtra("Stop")) {
            String direction = getIntent().getStringExtra("Direction");
            String stop = getIntent().getStringExtra("Stop");
            url += "&d=" + direction + "&s=" + stop;
        }

        Log.d(TAG, "Opening URL " + url);

        setContentView(R.layout.activity_map);

        final WebView webView = (WebView) findViewById(R.id.map_webview);
        webView.setVisibility(View.INVISIBLE);
        // Hide top header after the page loads
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webView.loadUrl("javascript:(function() {" +
                        "document.getElementById('header').style.display = 'none';" +
                        "})()");
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view,int errorCode, String description, String failingUrl) {
                setContentView(R.layout.network_error_message);
            }
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl(url);
    }
}
