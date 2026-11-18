package it.crowclaw.fude;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/www/index.html";
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#0c0c0e"));
        w.setNavigationBarColor(Color.parseColor("#0c0c0e"));

        Reminders.createChannel(this);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.JAPANESE);
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0c0c0e"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "FudeNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                openExternal(u.toString());
                return true;
            }
        });

        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
    }

    /** Ponte JavaScript ↔ Android usato dalla pagina. */
    private class Bridge {
        @JavascriptInterface public int versionCode() {
            try {
                PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
                return Build.VERSION.SDK_INT >= 28 ? (int) p.getLongVersionCode() : p.versionCode;
            } catch (Exception e) { return 0; }
        }

        @JavascriptInterface public boolean ttsReady() { return ttsReady; }

        @JavascriptInterface public void speak(String text, double rate) {
            if (!ttsReady) return;
            tts.setSpeechRate((float) rate);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fude");
        }

        @JavascriptInterface public void openTtsSettings() {
            runOnUiThread(() -> {
                try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
                catch (Exception e) {
                    try { startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)); } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface public void openUrl(String url) { runOnUiThread(() -> openExternal(url)); }

        /** Attiva o disattiva il promemoria giornaliero all'ora indicata. */
        @JavascriptInterface public void setReminder(boolean on, int hour, int minute) {
            SharedPreferences p = Reminders.prefs(MainActivity.this);
            p.edit().putBoolean("on", on).putInt("h", hour).putInt("m", minute).apply();
            if (on) {
                if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7));
                }
                Reminders.schedule(MainActivity.this);
            } else {
                Reminders.cancel(MainActivity.this);
            }
        }

        /** Stato di oggi, così la notifica non arriva se l'obiettivo è già raggiunto. */
        @JavascriptInterface public void setStatus(int day, boolean done, int remaining, int streak) {
            Reminders.prefs(MainActivity.this).edit()
                    .putInt("day", day).putBoolean("done", done)
                    .putInt("left", remaining).putInt("streak", streak).apply();
        }
    }

    private void handleBack() {
        web.evaluateJavascript("window.fudeBack ? String(window.fudeBack()) : 'false'", result -> {
            if (result == null || !result.contains("true")) finish();
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() { handleBack(); }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
