package lk.kgtraders.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;

/**
 * KG Traders - hosts the single-file web app (assets/index.html) in a WebView.
 * Bridges: AndroidWA.sendBill(...) -> share the bill image to WhatsApp,
 *          window.print()          -> Android print / Save as PDF.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";

    private WebView web;
    private long lastBack = 0L;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // dark icons on the light status / navigation bar
        Window win = getWindow();
        View decor = win.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(0xFFE9F1FF);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage = the app's saved data
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openExternal(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // make window.print() open the Android print dialog
                view.evaluateJavascript("window.print=function(){AndroidPrint.print()};", null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // window.open(...) (e.g. https://wa.me/...) -> open in the phone's own app / browser
                final WebView temp = new WebView(MainActivity.this);
                temp.setWebViewClient(new WebViewClient() {
                    private boolean done = false;

                    private void go(Uri u) {
                        if (done) return;
                        done = true;
                        openExternal(u);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        go(r.getUrl());
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                        if (url != null && !url.startsWith("about:")) {
                            go(Uri.parse(url));
                            v.stopLoading();
                        }
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }
        });

        web.addJavascriptInterface(new WaBridge(), "AndroidWA");
        web.addJavascriptInterface(new PrintBridge(), "AndroidPrint");

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** The web app pushes history entries for every screen, so Back = go to the previous screen. */
    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000L) {
            super.onBackPressed();
        } else {
            lastBack = now;
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
    }

    /** Returns false for the app's own pages (load inside WebView); opens everything else outside. */
    private boolean openExternal(Uri uri) {
        if (uri == null) return true;
        if (HOST.equals(uri.getHost())) return false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private boolean hasPackage(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** window.print() -> Android print dialog (choose a printer or "Save as PDF"). */
    private class PrintBridge {
        @JavascriptInterface
        public void print() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (web == null) return;
                    PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    PrintDocumentAdapter adapter = web.createPrintDocumentAdapter("KG Traders Bill");
                    pm.print("KG Traders Bill", adapter, new PrintAttributes.Builder().build());
                }
            });
        }
    }

    /** AndroidWA.sendBill(base64Png, phoneDigitsWith94, customerName) -> WhatsApp share. */
    private class WaBridge {
        @JavascriptInterface
        public void sendBill(final String base64, final String num, final String name) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "bills");
                if (!dir.exists()) dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) {
                    long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
                    for (File f : old) {
                        if (f.lastModified() < cutoff) f.delete();
                    }
                }
                File file = new File(dir, "bill_" + System.currentTimeMillis() + ".png");
                FileOutputStream out = new FileOutputStream(file);
                out.write(data);
                out.close();
                final Uri uri = FileProvider.getUriForFile(MainActivity.this,
                        getPackageName() + ".fileprovider", file);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        shareToWhatsApp(uri, num);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Could not share the bill", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void shareToWhatsApp(Uri uri, String num) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri("bill", uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        boolean wa = hasPackage("com.whatsapp");
        boolean biz = hasPackage("com.whatsapp.w4b");
        try {
            if (wa != biz) {                       // exactly one WhatsApp installed -> open it directly
                i.setPackage(wa ? "com.whatsapp" : "com.whatsapp.w4b");
                if (num != null && num.length() > 0) {
                    i.putExtra("jid", num + "@s.whatsapp.net");   // opens that customer's chat
                }
                startActivity(i);
            } else {                               // both or none -> let the user choose
                Intent chooser = Intent.createChooser(i, "Send bill");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(chooser);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show();
        }
    }
}
