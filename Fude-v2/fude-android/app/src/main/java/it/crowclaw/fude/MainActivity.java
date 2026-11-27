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

import android.database.Cursor;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String START_URL = "file:///android_asset/www/index.html";
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private static final int PICK_VIDEO = 41;
    private static final long MAX_VIDEO = 300L * 1024 * 1024;

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
        s.setAllowFileAccessFromFileURLs(false);
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

        /** Apre la galleria per scegliere un video da usare nelle pause. */
        @JavascriptInterface public void pickVideo() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*");
                try { startActivityForResult(i, PICK_VIDEO); } catch (Exception e) { notifyVideos("Non riesco ad aprire la galleria"); }
            });
        }

        /** Elenco dei video importati (salvati nella memoria privata dell'app). */
        @JavascriptInterface public String listVideos() {
            JSONArray a = new JSONArray();
            File[] fs = videoDir().listFiles();
            if (fs != null) {
                java.util.Arrays.sort(fs, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
                for (File f : fs) {
                    try {
                        String n = f.getName();
                        String title = n.replaceFirst("^\\d+_", "").replaceFirst("\\.[^.]+$", "").replace('_', ' ');
                        a.put(new JSONObject().put("name", n).put("title", title)
                                .put("path", "file://" + f.getAbsolutePath()).put("size", f.length()));
                    } catch (Exception ignored) {}
                }
            }
            return a.toString();
        }

        @JavascriptInterface public void deleteVideo(String name) {
            File f = new File(videoDir(), new File(name).getName());
            if (f.exists()) f.delete();
        }

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

    private File videoDir() {
        File d = new File(getFilesDir(), "videos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void notifyVideos(String msg) {
        String m = msg == null ? "" : msg.replace("\\", "").replace("'", "\\'");
        runOnUiThread(() -> web.evaluateJavascript("window.fudeVideosChanged && fudeVideosChanged('" + m + "')", null));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_VIDEO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            String name = "video";
            long size = -1;
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME), si = c.getColumnIndex(OpenableColumns.SIZE);
                    if (ni >= 0) name = c.getString(ni);
                    if (si >= 0) size = c.getLong(si);
                }
            } catch (Exception ignored) {}
            if (size > MAX_VIDEO) { notifyVideos("Video troppo grande (massimo 300 MB)"); return; }
            String safe = name.replaceAll("[^\\p{L}\\p{N}._ -]", "").replace(' ', '_');
            if (safe.isEmpty()) safe = "video.mp4";
            File out = new File(videoDir(), System.currentTimeMillis() + "_" + safe);
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[1 << 16];
                long tot = 0;
                int r;
                while ((r = in.read(buf)) > 0) {
                    tot += r;
                    if (tot > MAX_VIDEO) throw new Exception("big");
                    os.write(buf, 0, r);
                }
                notifyVideos("Video aggiunto alle pause");
            } catch (Exception e) {
                out.delete();
                notifyVideos("Importazione non riuscita");
            }
        }).start();
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
