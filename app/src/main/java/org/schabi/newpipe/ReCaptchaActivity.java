package org.schabi.newpipe;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;

import org.schabi.newpipe.util.ThemeHelper;

import androidx.annotation.NonNull;

/*
 * Created by beneth <bmauduit@beneth.fr> on 06.12.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * ReCaptchaActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
public class ReCaptchaActivity extends AppCompatActivity {
    public static final int RECAPTCHA_REQUEST = 10;
    public static final String RECAPTCHA_URL_EXTRA = "recaptcha_url_extra";
    public static final String TAG = ReCaptchaActivity.class.toString();
    public static final String YT_URL = "https://www.youtube.com";

    private WebView webView;
    private String foundCookies = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recaptcha);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        String url = getIntent().getStringExtra(RECAPTCHA_URL_EXTRA);
        if (url == null || url.isEmpty()) {
            url = YT_URL;
        }

        // set return to Cancel by default
        setResult(RESULT_CANCELED);


        webView = findViewById(R.id.reCaptchaWebView);

        // enable Javascript
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleCookies(url);
            }
        });

        // cleaning cache, history and cookies from webView
        webView.clearCache(true);
        webView.clearHistory();
        android.webkit.CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(aBoolean -> {});
        } else {
            cookieManager.removeAllCookie();
        }

        webView.loadUrl(url);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_recaptcha, menu);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setTitle(R.string.title_activity_recaptcha);
            actionBar.setSubtitle(R.string.subtitle_activity_recaptcha);
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        saveCookiesAndFinish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_item_done:
                saveCookiesAndFinish();
                return true;
            default:
                return false;
        }
    }

    private void saveCookiesAndFinish() {
        handleCookies(webView.getUrl()); // try to get cookies of unclosed page
        if (!foundCookies.isEmpty()) {
            // give cookies to Downloader class
            DownloaderImpl.getInstance().setCookies(foundCookies);
            setResult(RESULT_OK);
        }

        Intent intent = new Intent(this, org.schabi.newpipe.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NavUtils.navigateUpTo(this, intent);
    }


    private void handleCookies(String url) {
        String cookies = CookieManager.getInstance().getCookie(url);
        if (MainActivity.DEBUG)
            Log.d(TAG, "handleCookies: url=" + url + "; cookies=" + (cookies == null ? "null" : cookies));
        if (cookies == null) return;

        addYoutubeCookies(cookies);
        // add other methods to extract cookies here
    }

    private void addYoutubeCookies(@NonNull String cookies) {
        if (cookies.contains("s_gl=") || cookies.contains("goojf=") || cookies.contains("VISITOR_INFO1_LIVE=")) {
            // youtube seems to also need the other cookies:
            addCookie(cookies);
        }
    }

    private void addCookie(String cookie) {
        if (foundCookies.contains(cookie)) {
            return;
        }

        if (foundCookies.isEmpty() || foundCookies.endsWith("; ")) {
            foundCookies += cookie;
        } else if (foundCookies.endsWith(";")) {
            foundCookies += " " + cookie;
        } else {
            foundCookies += "; " + cookie;
        }
    }
}
