package ir.hamsayeban.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int SAVE_DOCUMENT = 4101;
    private static final int PICK_RECEIPT = 4102;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingText;
    private String pendingMime;
    private String pendingFilename;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(15, 73, 112));
        getWindow().setNavigationBarColor(Color.rgb(248, 250, 252));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("utf-8");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith("file:///android_asset/");
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) Toast.makeText(MainActivity.this, "خطا در باز کردن برنامه", Toast.LENGTH_LONG).show();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, PICK_RECEIPT);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "انتخاب تصویر ممکن نشد", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("بله", (d,w) -> result.confirm())
                    .setNegativeButton("خیر", (d,w) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel())
                    .show();
                return true;
            }
            @Override public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this)
                    .setMessage(message).setPositiveButton("باشه", (d,w) -> result.confirm()).show();
                return true;
            }
        });
        webView.addJavascriptInterface(new ExportBridge(), "HamsayebanBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class ExportBridge {
        @JavascriptInterface public void saveText(String filename, String text, String mime) {
            runOnUiThread(() -> {
                pendingText = text;
                pendingFilename = (filename == null || filename.isEmpty()) ? "hamsayeban-export.txt" : filename;
                pendingMime = (mime == null || mime.isEmpty()) ? "text/plain" : mime;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(pendingMime.split(";")[0]);
                intent.putExtra(Intent.EXTRA_TITLE, pendingFilename);
                try { startActivityForResult(intent, SAVE_DOCUMENT); }
                catch (Exception ex) { Toast.makeText(MainActivity.this, "ذخیره فایل ممکن نشد", Toast.LENGTH_LONG).show(); }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_RECEIPT) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        } else if (requestCode == SAVE_DOCUMENT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingText != null) {
                try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                    if (output == null) throw new Exception("no output");
                    output.write(pendingText.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    Toast.makeText(this, "فایل ذخیره شد", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "خطا در ذخیره فایل", Toast.LENGTH_LONG).show();
                }
            }
            pendingText = null;
            pendingFilename = null;
            pendingMime = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("HamsayebanBridge");
            webView.destroy();
        }
        super.onDestroy();
    }
}
