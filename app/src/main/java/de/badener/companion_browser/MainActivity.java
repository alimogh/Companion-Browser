package de.badener.companion_browser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.badener.companion_browser.utils.AdBlocking;

public class MainActivity extends AppCompatActivity {

    private static final String startPage = "https://www.google.com/";

    private WebView webView;
    private FrameLayout bottomBarContainer;
    private ImageButton webViewControlButton;
    private ImageButton openDefaultAppButton;
    private TextInputEditText searchTextInput;
    private ImageButton clearSearchTextButton;
    private ImageButton menuButton;
    private ProgressBar progressBar;
    private FrameLayout fullScreen;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    private String snackbarText;
    private String shortcutTitle;
    private IconCompat shortcutIcon;

    private boolean isDefaultAppAvailable;
    private boolean isAdBlockingEnabled;
    private boolean isFullScreen;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        bottomBarContainer = findViewById(R.id.bottomBarContainer);
        webViewControlButton = findViewById(R.id.webViewControlButton);
        openDefaultAppButton = findViewById(R.id.openDefaultAppButton);
        searchTextInput = findViewById(R.id.searchTextInput);
        clearSearchTextButton = findViewById(R.id.clearSearchTextButton);
        menuButton = findViewById(R.id.menuButton);
        progressBar = findViewById(R.id.progressBar);
        fullScreen = findViewById(R.id.fullScreenContainer);

        // Change WebView settings
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setGeolocationEnabled(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setAppCachePath(getApplicationContext().getCacheDir().getAbsolutePath());

        // Initialize ad blocking
        AdBlocking.init(this);
        sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        isAdBlockingEnabled = sharedPreferences.getBoolean("ad_blocking", true);

        // Handle "WebView control button" in the search field
        webViewControlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (webView.getProgress() == 100) {
                    webView.reload();
                } else {
                    webView.stopLoading();
                }
            }
        });

        // Handle "open default app button" in the search field
        openDefaultAppButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
                startActivity(Intent.createChooser(intent, getString(R.string.chooser_open_app)));
            }
        });

        // Handle focus changes of the search field
        searchTextInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    webViewControlButton.setVisibility(View.GONE);
                    openDefaultAppButton.setVisibility(View.GONE);
                    clearSearchTextButton.setVisibility(View.VISIBLE);
                    menuButton.setVisibility(View.GONE);
                } else {
                    webViewControlButton.setVisibility(View.VISIBLE);
                    if (isDefaultAppAvailable) openDefaultAppButton.setVisibility(View.VISIBLE);
                    searchTextInput.setText(webView.getUrl());
                    clearSearchTextButton.setVisibility(View.GONE);
                    menuButton.setVisibility(View.VISIBLE);
                }
            }
        });

        // Handle the "go button" shown in the keyboard
        searchTextInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_GO) {
                    if (Objects.requireNonNull(searchTextInput.getText()).toString().trim().isEmpty()) {
                        searchTextInput.setText(webView.getUrl());
                    } else {
                        String input = searchTextInput.getText().toString().trim();
                        String url;
                        if (URLUtil.isValidUrl(input)) {
                            // Input is a valid URL
                            url = input;
                        } else if (input.contains(" ") || !input.contains(".")) {
                            // Input is obviously no URL, start Google search
                            url = "https://www.google.com/search?q=" + input;
                        } else {
                            // Try to guess URL
                            url = URLUtil.guessUrl(input);
                        }
                        webView.loadUrl(url);
                    }
                    searchTextInput.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    Objects.requireNonNull(imm).toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                    return true;
                }
                return false;
            }
        });

        // Handle "clear search text button" in the search field
        clearSearchTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Objects.requireNonNull(searchTextInput.getText()).clear();
            }
        });

        // Handle "menu button" in bottom bar
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPopupMenu();
            }
        });

        // Handle downloads
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                // Check if storage permission is granted and start download if applicable
                if (isStoragePermissionGranted()) {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        request.allowScanningByMediaScanner();
                    }
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    request.setMimeType(MimeTypeMap.getSingleton().
                            getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url)));
                    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    Objects.requireNonNull(downloadManager).enqueue(request);
                    snackbarText = getString(R.string.download_started);
                    showSnackbar();
                }
            }
        });

        // Handle intents
        Intent intent = getIntent();
        Uri uri = intent.getData();
        String url;
        url = (uri != null ? uri.toString() : startPage);
        // Load either the start page or the URL provided by an intent
        webView.loadUrl(url);

        webView.setWebChromeClient(new WebChromeClient() {

            // Update the progress bar and other ui elements according to WebView progress
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar.setProgress(newProgress, true);
                } else {
                    progressBar.setProgress(newProgress);
                }
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    webViewControlButton.setImageDrawable(getDrawable(R.drawable.ic_cancel));
                } else {
                    progressBar.setVisibility(View.GONE);
                    webViewControlButton.setImageDrawable(getDrawable(R.drawable.ic_reload));
                }
            }

            // Enter fullscreen
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                bottomBarContainer.setVisibility(View.GONE);
                fullScreen.setVisibility(View.VISIBLE);
                fullScreen.addView(view);
                enableFullScreen();
                isFullScreen = true;
            }

            // Exit fullscreen
            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                bottomBarContainer.setVisibility(View.VISIBLE);
                fullScreen.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                int isLightThemeEnabled = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (isLightThemeEnabled == Configuration.UI_MODE_NIGHT_NO) {
                    // Light theme is enabled, restore light status bar and nav bar
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        View decorView = getWindow().getDecorView();
                        decorView.setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                    } else {
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                    }
                }
                isFullScreen = false;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            // Ad blocking feature
            private final Map<String, Boolean> loadedUrls = new HashMap<>();

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                boolean ad;
                String url = request.getUrl().toString();
                if (isAdBlockingEnabled) {
                    if (!loadedUrls.containsKey(url)) {
                        ad = AdBlocking.isAd(url);
                        loadedUrls.put(url, ad);
                    } else {
                        ad = loadedUrls.get(url);
                    }
                    return (ad ? AdBlocking.createEmptyResource() : super.shouldInterceptRequest(view, request));
                }
                return super.shouldInterceptRequest(view, request);
            }

            // Update the URL displayed in the bottom bar and check for default apps
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                if (!searchTextInput.hasFocus()) searchTextInput.setText(webView.getUrl());
                checkDefaultApps();
                super.doUpdateVisitedHistory(view, url, isReload);
            }

            // Handle external links
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!URLUtil.isValidUrl(url)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    PackageManager packageManager = getPackageManager();
                    if (intent.resolveActivity(packageManager) != null) {
                        // There is at least one app that can handle the link
                        startActivity(Intent.createChooser(intent, getString(R.string.chooser_open_app)));
                    } else {
                        // There is no app
                        snackbarText = getString(R.string.url_cannot_be_loaded);
                        showSnackbar();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    // Show and handle the popup menu
    private void showPopupMenu() {
        PopupMenu popupMenu = new PopupMenu(this, menuButton);
        popupMenu.setGravity(Gravity.END);
        popupMenu.inflate(R.menu.menu_main);
        popupMenu.getMenu().findItem(R.id.action_toggle_ad_blocking).setChecked(isAdBlockingEnabled);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {

                    case R.id.action_share:
                        // Share URL
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.chooser_share)));
                        return true;

                    case R.id.action_new_window:
                        // Open new window
                        Intent newWindowIntent = new Intent(MainActivity.this, MainActivity.class);
                        newWindowIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        startActivity(newWindowIntent);
                        return true;

                    case R.id.action_add_shortcut:
                        // Pin website shortcut to launcher if supported
                        if (ShortcutManagerCompat.isRequestPinShortcutSupported(MainActivity.this)) {
                            pinShortcut();
                        } else {
                            snackbarText = getString(R.string.shortcuts_not_supported);
                            showSnackbar();
                        }
                        return true;

                    case R.id.action_toggle_ad_blocking:
                        // Toggle ad blocking
                        isAdBlockingEnabled = !isAdBlockingEnabled;
                        editor = sharedPreferences.edit();
                        editor.putBoolean("ad_blocking", isAdBlockingEnabled);
                        editor.apply();
                        snackbarText = getString(isAdBlockingEnabled ? R.string.ad_blocking_enabled : R.string.ad_blocking_disabled);
                        showSnackbar();
                        item.setChecked(isAdBlockingEnabled);
                        webView.reload();
                        return true;

                    case R.id.action_clear_data:
                        // Clear browsing data
                        clearBrowsingData();
                        return true;

                    case R.id.action_close_window:
                        // Close window
                        finishAndRemoveTask();
                        return true;

                    default:
                        return false;
                }
            }
        });
        popupMenu.show();
    }

    // Pin website shortcut to launcher
    private void pinShortcut() {
        // Ask for the shortcut title first
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setBackground(getDrawable(R.drawable.background_round_corners));
        builder.setTitle(R.string.action_add_shortcut);
        @SuppressLint("InflateParams") View viewInflated = LayoutInflater.from(this).inflate(R.layout.add_shortcut_layout, null);
        final TextInputEditText textInput = viewInflated.findViewById(R.id.TextInput);
        textInput.setText(webView.getTitle());
        builder.setView(viewInflated);

        builder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                // Get the title for the shortcut
                shortcutTitle = (Objects.requireNonNull(textInput.getText()).toString().trim().isEmpty() ?
                        webView.getTitle() : textInput.getText().toString().trim());
                // Create the icon for the shortcut
                createShortcutIcon();
                // Create the shortcut
                Intent pinShortcutIntent = new Intent(MainActivity.this, MainActivity.class);
                pinShortcutIntent.setData(Uri.parse(webView.getUrl()));
                pinShortcutIntent.setAction(Intent.ACTION_MAIN);
                ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(MainActivity.this, shortcutTitle)
                        .setShortLabel(shortcutTitle)
                        .setLongLabel(shortcutTitle)
                        .setIcon(shortcutIcon)
                        .setIntent(pinShortcutIntent)
                        .build();
                ShortcutManagerCompat.requestPinShortcut(MainActivity.this, shortcutInfo, null);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    snackbarText = getString(R.string.shortcut_added);
                    showSnackbar();
                }
            }
        });
        // Cancel creating shortcut
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }

    // Create launcher icons for shortcuts
    private void createShortcutIcon() {
        // Draw background
        Bitmap icon = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        canvas.drawColor(ContextCompat.getColor(getApplicationContext(), R.color.colorShortcuts));
        // Get the first one or two characters of the shortcut title
        String iconText;
        iconText = (shortcutTitle.length() >= 2 ? shortcutTitle.substring(0, 2) : shortcutTitle.substring(0, 1));
        // Draw the first one or two characters on the background
        Paint paintText = new Paint();
        paintText.setAntiAlias(true);
        paintText.setColor(ContextCompat.getColor(getApplicationContext(), android.R.color.white));
        paintText.setTextSize(128);
        paintText.setFakeBoldText(true);
        paintText.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(iconText, canvas.getWidth() / 2f,
                canvas.getHeight() / 2f - (paintText.descent() + paintText.ascent()) / 2f, paintText);
        // Create icon
        shortcutIcon = IconCompat.createWithAdaptiveBitmap(icon);
    }

    // Clear browsing data
    private void clearBrowsingData() {
        new MaterialAlertDialogBuilder(this)
                .setBackground(getDrawable(R.drawable.background_round_corners))
                .setTitle(R.string.action_clear_data)
                .setIcon(R.drawable.ic_delete_outline)
                .setMessage(R.string.clear_data_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        webView.clearCache(true);
                        CookieManager.getInstance().removeAllCookies(null);
                        WebStorage.getInstance().deleteAllData();
                        webView.loadUrl(startPage);
                        snackbarText = getString(R.string.clear_data_confirmation);
                        showSnackbar();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .show();
    }

    // Check if there is a default app to open the current link
    private void checkDefaultApps() {
        String packageName = "de.badener.companion_browser";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : list) {
            packageName = info.activityInfo.packageName;
        }
        if (packageName.equals("de.badener.companion_browser")) {
            isDefaultAppAvailable = false;
            openDefaultAppButton.setVisibility(View.GONE);
        } else {
            isDefaultAppAvailable = true;
            openDefaultAppButton.setVisibility(View.VISIBLE);
        }
    }

    // Restore fullscreen after losing and gaining focus
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullScreen) enableFullScreen();
    }

    // Hide status bar and nav bar when entering fullscreen
    private void enableFullScreen() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Check if permission is granted to write on storage for downloading files
    private boolean isStoragePermissionGranted() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted
            return true;
        } else {
            // Ask for permission because it is not granted yet
            snackbarText = getString(R.string.storage_permission_needed);
            showSnackbar();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return false;
        }
    }

    // Show snackbar with custom background and text color
    private void showSnackbar() {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.coordinatorLayout), snackbarText, Snackbar.LENGTH_SHORT);
        View view = snackbar.getView();
        DrawableCompat.setTint(view.getBackground(), ContextCompat.getColor(this, R.color.colorPrimaryVariant));
        TextView textView = view.findViewById(R.id.snackbar_text);
        textView.setTextColor(ContextCompat.getColor(this, R.color.colorText));
        snackbar.show();
    }

    // Prevent the back button from closing the app
    @Override
    public void onBackPressed() {
        if (searchTextInput.hasFocus()) {
            searchTextInput.clearFocus();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finishAndRemoveTask();
        }
    }
}
