package com.reactnativecommunity.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewFeature;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.CatalystInstance;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.common.build.ReactBuildConfig;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.ContentSizeChangeEvent;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.views.scroll.OnScrollDispatchHelper;
import com.facebook.react.views.scroll.ScrollEvent;
import com.facebook.react.views.scroll.ScrollEventType;
import com.reactnativecommunity.webview.RNCWebViewModule.ShouldOverrideUrlLoadingLock.ShouldOverrideCallbackState;
import com.reactnativecommunity.webview.events.TopHttpErrorEvent;
import com.reactnativecommunity.webview.events.TopLoadingErrorEvent;
import com.reactnativecommunity.webview.events.TopLoadingFinishEvent;
import com.reactnativecommunity.webview.events.TopLoadingProgressEvent;
import com.reactnativecommunity.webview.events.TopLoadingStartEvent;
import com.reactnativecommunity.webview.events.TopMessageEvent;
import com.reactnativecommunity.webview.events.TopRenderProcessGoneEvent;
import com.reactnativecommunity.webview.events.TopShouldStartLoadWithRequestEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages instances of {@link WebView}
 * <p>
 * Can accept following commands:
 * - GO_BACK
 * - GO_FORWARD
 * - RELOAD
 * - LOAD_URL
 * <p>
 * {@link WebView} instances could emit following direct events:
 * - topLoadingFinish
 * - topLoadingStart
 * - topLoadingStart
 * - topLoadingProgress
 * - topShouldStartLoadWithRequest
 * <p>
 * Each event will carry the following properties:
 * - target - view's react tag
 * - url - url set for the webview
 * - loading - whether webview is in a loading state
 * - title - title of the current page
 * - canGoBack - boolean, whether there is anything on a history stack to go back
 * - canGoForward - boolean, whether it is possible to request GO_FORWARD command
 */
@ReactModule(name = RNCWebViewManager.REACT_CLASS)
public class RNCWebViewManager extends SimpleViewManager<RNCWebViewContainer> {
  private static final String TAG = "RNCWebViewManager";

  public static final int COMMAND_GO_BACK = 1;
  public static final int COMMAND_GO_FORWARD = 2;
  public static final int COMMAND_RELOAD = 3;
  public static final int COMMAND_STOP_LOADING = 4;
  public static final int COMMAND_POST_MESSAGE = 5;
  public static final int COMMAND_INJECT_JAVASCRIPT = 6;
  public static final int COMMAND_LOAD_URL = 7;
  public static final int COMMAND_FOCUS = 8;

  // commands added by Discord
  public static final int COMMAND_RELEASE = 4001;

  // android commands
  public static final int COMMAND_CLEAR_FORM_DATA = 1000;
  public static final int COMMAND_CLEAR_CACHE = 1001;
  public static final int COMMAND_CLEAR_HISTORY = 1002;

  protected static final String REACT_CLASS = "RNCWebViewContainer";
  protected static final String HTML_ENCODING = "UTF-8";
  protected static final String HTML_MIME_TYPE = "text/html";
  protected static final String JAVASCRIPT_INTERFACE = "ReactNativeWebView";
  protected static final String HTTP_METHOD_POST = "POST";
  // Use `webView.loadUrl("about:blank")` to reliably reset the view
  // state and release page resources (including any running JavaScript).
  protected static final String BLANK_URL = "about:blank";
  protected static final int SHOULD_OVERRIDE_URL_LOADING_TIMEOUT = 250;
  protected static final String DEFAULT_DOWNLOADING_MESSAGE = "Downloading";
  protected static final String DEFAULT_LACK_PERMISSION_TO_DOWNLOAD_MESSAGE =
    "Cannot download files as permission was denied. Please provide permission to write to storage, in order to download files.";
  protected WebViewConfig mWebViewConfig;

  protected RNCWebChromeClient mWebChromeClient = null;
  protected boolean mAllowsFullscreenVideo = false;
  protected boolean mAllowsProtectedMedia = false;
  protected @Nullable String mUserAgent = null;
  protected @Nullable String mUserAgentWithApplicationName = null;
  protected @Nullable String mDownloadingMessage = null;
  protected @Nullable String mLackPermissionToDownloadMessage = null;

  Set<String> assetLoaderHandlerTypes = new HashSet<>(Arrays.asList("assets", "internal", "resources"));

  public RNCWebViewManager() {
    mWebViewConfig = new WebViewConfig() {
      public void configWebView(WebView webView) {
      }
    };
  }

  public RNCWebViewManager(WebViewConfig webViewConfig) {
    mWebViewConfig = webViewConfig;
  }

  @Override
  public String getName() {
    return REACT_CLASS;
  }

  protected RNCWebView createRNCWebViewInstance(ThemedReactContext reactContext) {
    return new RNCWebView(reactContext);
  }

  @Override
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  protected RNCWebViewContainer createViewInstance(ThemedReactContext reactContext) {
    RNCWebViewContainer wrapper = new RNCWebViewContainer(reactContext);
    RNCWebView webView = createRNCWebViewInstance(reactContext);
    wrapper.attachWebView(webView);
    RNCWebViewMapManager.INSTANCE.getViewIdMap().put(webView.getId(), wrapper.getId());

    setupWebChromeClient(reactContext, webView);
    reactContext.addLifecycleEventListener(webView);
    mWebViewConfig.configWebView(webView);
    WebSettings settings = webView.getSettings();
    settings.setBuiltInZoomControls(true);
    settings.setDisplayZoomControls(false);
    settings.setDomStorageEnabled(true);
    settings.setSupportMultipleWindows(true);

    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      settings.setAllowFileAccessFromFileURLs(false);
      setAllowUniversalAccessFromFileURLs(wrapper, false);
    }
    setMixedContentMode(wrapper, "never");

    boolean isDebug = ((reactContext.getApplicationInfo().flags &
      ApplicationInfo.FLAG_DEBUGGABLE) != 0);

    if (isDebug && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      WebView.setWebContentsDebuggingEnabled(true);
    }

    webView.setDownloadListener(new DownloadListener() {
      public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        webView.setIgnoreErrFailedForThisURL(url);

        RNCWebViewModule module = getModule(reactContext);

        DownloadManager.Request request;
        try {
          request = new DownloadManager.Request(Uri.parse(url));
        } catch (IllegalArgumentException e) {
          Log.w(TAG, "Unsupported URI, aborting download", e);
          return;
        }

        String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
        String downloadMessage = "Downloading " + fileName;

        //Attempt to add cookie, if it exists
        URL urlObj = null;
        try {
          urlObj = new URL(url);
          String baseUrl = urlObj.getProtocol() + "://" + urlObj.getHost();
          String cookie = CookieManager.getInstance().getCookie(baseUrl);
          request.addRequestHeader("Cookie", cookie);
        } catch (MalformedURLException e) {
          Log.w(TAG, "Error getting cookie for DownloadManager", e);
        }

        //Finish setting up request
        request.addRequestHeader("User-Agent", userAgent);
        request.setTitle(fileName);
        request.setDescription(downloadMessage);
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        module.setDownloadRequest(request);

        if (module.grantFileDownloaderPermissions(getDownloadingMessage(), getLackPermissionToDownloadMessage())) {
          module.downloadFile(getDownloadingMessage());
        }
      }
    });

    return wrapper;
  }

  private String getDownloadingMessage() {
    return  mDownloadingMessage == null ? DEFAULT_DOWNLOADING_MESSAGE : mDownloadingMessage;
  }

  private String getLackPermissionToDownloadMessage() {
    return  mDownloadingMessage == null ? DEFAULT_LACK_PERMISSION_TO_DOWNLOAD_MESSAGE : mLackPermissionToDownloadMessage;
  }

  @ReactProp(name = "javaScriptEnabled")
  public void setJavaScriptEnabled(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setJavaScriptEnabled(enabled));
  }

  @ReactProp(name = "setBuiltInZoomControls")
  public void setBuiltInZoomControls(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setBuiltInZoomControls(enabled));
  }

  @ReactProp(name = "setDisplayZoomControls")
  public void setDisplayZoomControls(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setDisplayZoomControls(enabled));
  }

  @ReactProp(name = "setSupportMultipleWindows")
  public void setSupportMultipleWindows(RNCWebViewContainer view, boolean enabled){
    view.ifHasRNCWebView(webView -> webView.getSettings().setSupportMultipleWindows(enabled));
  }

  @ReactProp(name = "webViewKey")
  public void setWebViewKey(RNCWebViewContainer view, String webViewKey) {
    Map<String, WebView> rncWebViewMap = RNCWebViewMapManager.INSTANCE.getRncWebViewMap();

    if (rncWebViewMap.containsKey(webViewKey)) {
      RNCWebView webView = (RNCWebView) rncWebViewMap.get(webViewKey);

      ViewGroup webViewParent = (ViewGroup)webView.getParent();

      // If the RNCWebView is attached to an existing RNCWebViewContainer, first detach
      // it from the existing RNCWebViewContainer.
      if (webViewParent != null && webViewParent instanceof RNCWebViewContainer) {
        RNCWebViewContainer existingRncWebViewContainer = (RNCWebViewContainer) webView.getParent();
        existingRncWebViewContainer.detachWebView();

        // The chrome client was originally setup on instance creation but might be pointing to the wrong webview
        // so it's reset here.
        // Not entirely sure why there is a single instance of the webchrome client for all webviews?
        setupWebChromeClient((ThemedReactContext) existingRncWebViewContainer.getContext(), webView);
      }

      // The webview might be attached to the temporary parent; if so, remove it first.
      if (webViewParent != null) {
        webViewParent.removeView(webView);
      }

      view.attachWebView(webView);
    }

    // Update all maps with the view + set/update key
    // This means an existing webview can update it's own key
    view.ifHasRNCWebView(webView -> {
      webView.setWebViewKey(webViewKey);
      RNCWebViewMapManager.INSTANCE.getViewIdMap().put(webView.getId(), view.getId());
      rncWebViewMap.put(webViewKey, webView);
    });
  }

  @ReactProp(name = "temporaryParentNodeTag")
  public void setTemporaryParentNodeTag(RNCWebViewContainer view, int nodeTag) {
    view.temporaryParentNodeTag = nodeTag;
  }

  @ReactProp(name = "showsHorizontalScrollIndicator")
  public void setShowsHorizontalScrollIndicator(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.setHorizontalScrollBarEnabled(enabled));
  }

  @ReactProp(name = "showsVerticalScrollIndicator")
  public void setShowsVerticalScrollIndicator(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.setVerticalScrollBarEnabled(enabled));
  }

  @ReactProp(name = "downloadingMessage")
  public void setDownloadingMessage(WebView view, String message) {
    mDownloadingMessage = message;
  }

  @ReactProp(name = "lackPermissionToDownloadMessage")
  public void setLackPermissionToDownlaodMessage(WebView view, String message) {
    mLackPermissionToDownloadMessage = message;
  }

  @ReactProp(name = "cacheEnabled")
  public void setCacheEnabled(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> {
      webView.getSettings().setCacheMode(enabled ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_NO_CACHE);
    });
  }

  @ReactProp(name = "cacheMode")
  public void setCacheMode(RNCWebViewContainer view, String cacheModeString) {
    Integer cacheMode;
    switch (cacheModeString) {
      case "LOAD_CACHE_ONLY":
        cacheMode = WebSettings.LOAD_CACHE_ONLY;
        break;
      case "LOAD_CACHE_ELSE_NETWORK":
        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK;
        break;
      case "LOAD_NO_CACHE":
        cacheMode = WebSettings.LOAD_NO_CACHE;
        break;
      case "LOAD_DEFAULT":
      default:
        cacheMode = WebSettings.LOAD_DEFAULT;
        break;
    }
    view.ifHasRNCWebView(webView -> webView.getSettings().setCacheMode(cacheMode));
  }

  @ReactProp(name = "androidHardwareAccelerationDisabled")
  public void setHardwareAccelerationDisabled(RNCWebViewContainer view, boolean disabled) {
    if (disabled) {
      view.ifHasRNCWebView(webView -> webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null));
    }
  }

  @ReactProp(name = "androidLayerType")
  public void setLayerType(RNCWebViewContainer view, String layerTypeString) {
    final int layerType;
    switch (layerTypeString) {
        case "hardware":
          layerType = View.LAYER_TYPE_HARDWARE;
          break;
        case "software":
          layerType = View.LAYER_TYPE_SOFTWARE;
          break;
      default:
        layerType = View.LAYER_TYPE_NONE;
    }

    view.ifHasRNCWebView(webView -> webView.setLayerType(layerType, null));
  }

  @ReactProp(name = "androidAssetLoaderConfig")
  public void setAssetLoaderConfig(RNCWebViewContainer view, @Nullable ReadableMap config) {
    WebViewAssetLoader.Builder builder = new WebViewAssetLoader.Builder();

    String domain = config.getString("domain");
    if (domain != null) {
      builder.setDomain(domain);
    }

    if (config.hasKey("httpAllowed")) {
      builder.setHttpAllowed(config.getBoolean("httpAllowed"));
    }

    ReadableArray handlers = config.getArray("pathHandlers");
    if (handlers != null && handlers.size() > 0) {
      for (int i = 0; i < handlers.size(); i++) {
        final ReadableMap handler = handlers.getMap(i);
        String handlerType = handler.getString("type");

        if (handlerType == null) {
          FLog.w(TAG, "WebViewAssetLoader error. Path Handler type is null.");
          continue;
        }

        if (!assetLoaderHandlerTypes.contains(handlerType)) {
          FLog.w(TAG, "WebViewAssetLoader error. Skipping Path Handler. Unexpected handler type: " + handlerType + ". Path Handler type must be one of " + assetLoaderHandlerTypes);
          continue;
        }

        String handlerPath = handler.getString("path");

        if (handlerPath == null) {
          FLog.w(TAG, "WebViewAssetLoader error. Skipping Path Handler. Handler path is missing");
          continue;
        }

        if (handlerType.equals("resources")) {
          builder.addPathHandler(handlerPath, new WebViewAssetLoader.ResourcesPathHandler(view.getContext()));
        } else if (handlerType.equals("assets")) {
          builder.addPathHandler(handlerPath, new WebViewAssetLoader.AssetsPathHandler(view.getContext()));
        } else if (handlerType.equals("internal")) {
          String directory = handler.getString("directory");
          if (directory == null) {
            FLog.w(TAG, "WebViewAssetLoader error. Skipping Path Handler. Directory is missing for internal handler path");
            continue;
          }
          builder.addPathHandler(handlerPath, new WebViewAssetLoader.InternalStoragePathHandler(view.getContext(), new File(directory)));
        }
      }
    } else {
      FLog.w(TAG, "WebViewAssetLoader error. No Path Handlers found.");
    }

    WebViewAssetLoader assetLoader = builder.build();
    view.ifHasRNCWebView(webView -> webView.setWebViewAssetLoader(assetLoader));
  }


  @ReactProp(name = "overScrollMode")
  public void setOverScrollMode(RNCWebViewContainer view, String overScrollModeString) {
    final Integer overScrollMode;
    switch (overScrollModeString) {
      case "never":
        overScrollMode = View.OVER_SCROLL_NEVER;
        break;
      case "content":
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS;
        break;
      case "always":
      default:
        overScrollMode = View.OVER_SCROLL_ALWAYS;
        break;
    }
    view.ifHasRNCWebView(webView -> webView.setOverScrollMode(overScrollMode));
  }

  @ReactProp(name = "nestedScrollEnabled")
  public void setNestedScrollEnabled(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.setNestedScrollEnabled(enabled));
  }

  @ReactProp(name = "thirdPartyCookiesEnabled")
  public void setThirdPartyCookiesEnabled(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, enabled);
      }
    });
  }

  @ReactProp(name = "textZoom")
  public void setTextZoom(RNCWebViewContainer view, int value) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setTextZoom(value));
  }

  @ReactProp(name = "scalesPageToFit")
  public void setScalesPageToFit(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> {
      webView.getSettings().setLoadWithOverviewMode(enabled);
      webView.getSettings().setUseWideViewPort(enabled);
    });
  }

  @ReactProp(name = "domStorageEnabled")
  public void setDomStorageEnabled(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setDomStorageEnabled(enabled));
  }

  @ReactProp(name = "userAgent")
  public void setUserAgent(RNCWebViewContainer view, @Nullable String userAgent) {
    if (userAgent != null) {
      mUserAgent = userAgent;
    } else {
      mUserAgent = null;
    }
    view.ifHasRNCWebView(this::setUserAgentString);
  }

  @ReactProp(name = "applicationNameForUserAgent")
  public void setApplicationNameForUserAgent(RNCWebViewContainer view, @Nullable String applicationName) {
    if(applicationName != null) {
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        String defaultUserAgent = WebSettings.getDefaultUserAgent(view.getContext());
        mUserAgentWithApplicationName = defaultUserAgent + " " + applicationName;
      }
    } else {
      mUserAgentWithApplicationName = null;
    }
    view.ifHasRNCWebView(this::setUserAgentString);
  }

  protected void setUserAgentString(WebView view) {
    if(mUserAgent != null) {
      view.getSettings().setUserAgentString(mUserAgent);
    } else if(mUserAgentWithApplicationName != null) {
      view.getSettings().setUserAgentString(mUserAgentWithApplicationName);
    } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      // handle unsets of `userAgent` prop as long as device is >= API 17
      view.getSettings().setUserAgentString(WebSettings.getDefaultUserAgent(view.getContext()));
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  @ReactProp(name = "mediaPlaybackRequiresUserAction")
  public void setMediaPlaybackRequiresUserAction(RNCWebViewContainer view, boolean requires) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setMediaPlaybackRequiresUserGesture(requires));
  }

  @ReactProp(name = "javaScriptCanOpenWindowsAutomatically")
  public void setJavaScriptCanOpenWindowsAutomatically(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(enabled));
  }

  @ReactProp(name = "allowFileAccessFromFileURLs")
  public void setAllowFileAccessFromFileURLs(RNCWebViewContainer view, boolean allow) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setAllowFileAccessFromFileURLs(allow));
  }

  @ReactProp(name = "allowUniversalAccessFromFileURLs")
  public void setAllowUniversalAccessFromFileURLs(RNCWebViewContainer view, boolean allow) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setAllowUniversalAccessFromFileURLs(allow));
  }

  @ReactProp(name = "saveFormDataDisabled")
  public void setSaveFormDataDisabled(RNCWebViewContainer view, boolean disable) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setSaveFormData(!disable));
  }

  @ReactProp(name = "injectedJavaScript")
  public void setInjectedJavaScript(RNCWebViewContainer view, @Nullable String injectedJavaScript) {
    view.ifHasRNCWebView(webView -> webView.setInjectedJavaScript(injectedJavaScript));
  }

  @ReactProp(name = "injectedJavaScriptBeforeContentLoaded")
  public void setInjectedJavaScriptBeforeContentLoaded(RNCWebViewContainer view, @Nullable String injectedJavaScriptBeforeContentLoaded) {
    view.ifHasRNCWebView(webView -> webView.setInjectedJavaScriptBeforeContentLoaded(injectedJavaScriptBeforeContentLoaded));
  }

  @ReactProp(name = "injectedJavaScriptForMainFrameOnly")
  public void setInjectedJavaScriptForMainFrameOnly(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.setInjectedJavaScriptForMainFrameOnly(enabled));
  }

  @ReactProp(name = "injectedJavaScriptBeforeContentLoadedForMainFrameOnly")
  public void setInjectedJavaScriptBeforeContentLoadedForMainFrameOnly(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.setInjectedJavaScriptBeforeContentLoadedForMainFrameOnly(enabled));
  }

  @ReactProp(name = "messagingEnabled")
  public void setMessagingEnabled(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> webView.setMessagingEnabled(enabled));
  }

  @ReactProp(name = "messagingModuleName")
  public void setMessagingModuleName(RNCWebViewContainer view, String moduleName) {
    view.ifHasRNCWebView(webView -> webView.setMessagingModuleName(moduleName));
  }

  @ReactProp(name = "incognito")
  public void setIncognito(RNCWebViewContainer view, boolean enabled) {
    // Don't do anything when incognito is disabled
    if (!enabled) {
      return;
    }

    // Remove all previous cookies
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      CookieManager.getInstance().removeAllCookies(null);
    } else {
      CookieManager.getInstance().removeAllCookie();
    }

    view.ifHasRNCWebView(webView -> {
      // Disable caching
      webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
      webView.clearHistory();
      webView.clearCache(true);

      // No form data or autofill enabled
      webView.clearFormData();
      webView.getSettings().setSavePassword(false);
      webView.getSettings().setSaveFormData(false);
    });
  }

  @ReactProp(name = "source")
  public void setSource(RNCWebViewContainer view, @Nullable ReadableMap source) {
      view.ifHasRNCWebView(webView -> {

      // Do not reload reload webview if the source prop has not changed
      if (webView.webViewKey != null && !webView.isNewSource(source)) {
        return;
      }

      webView.setSource(source);

      if (source != null) {
        if (source.hasKey("html")) {
          String html = source.getString("html");
          String baseUrl = source.hasKey("baseUrl") ? source.getString("baseUrl") : "";
          webView.loadDataWithBaseURL(baseUrl, html, HTML_MIME_TYPE, HTML_ENCODING, null);
          return;
        }
        if (source.hasKey("uri")) {
          String url = source.getString("uri");
          String previousUrl = webView.getUrl();
          if (previousUrl != null && previousUrl.equals(url)) {
            return;
          }
          if (source.hasKey("method")) {
            String method = source.getString("method");
            if (method.equalsIgnoreCase(HTTP_METHOD_POST)) {
              byte[] postData = null;
              if (source.hasKey("body")) {
                String body = source.getString("body");
                try {
                  postData = body.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                  postData = body.getBytes();
                }
              }
              if (postData == null) {
                postData = new byte[0];
              }
              webView.postUrl(url, postData);
              return;
            }
          }
          HashMap<String, String> headerMap = new HashMap<>();
          if (source.hasKey("headers")) {
            ReadableMap headers = source.getMap("headers");
            ReadableMapKeySetIterator iter = headers.keySetIterator();
            while (iter.hasNextKey()) {
              String key = iter.nextKey();
              if ("user-agent".equals(key.toLowerCase(Locale.ENGLISH))) {
                if (webView.getSettings() != null) {
                  webView.getSettings().setUserAgentString(headers.getString(key));
                }
              } else {
                headerMap.put(key, headers.getString(key));
              }
            }
          }
          webView.loadUrl(url, headerMap);
          return;
        }
      }
      webView.loadUrl(BLANK_URL);
    });
  }

  @ReactProp(name = "basicAuthCredential")
  public void setBasicAuthCredential(RNCWebViewContainer view, @Nullable ReadableMap credential) {
    view.ifHasRNCWebView(webView -> {
      @Nullable BasicAuthCredential basicAuthCredential = null;
      if (credential != null) {
        if (credential.hasKey("username") && credential.hasKey("password")) {
          String username = credential.getString("username");
          String password = credential.getString("password");
          basicAuthCredential = new BasicAuthCredential(username, password);
        }
      }
      webView.setBasicAuthCredential(basicAuthCredential);
    });
  }

  @ReactProp(name = "onContentSizeChange")
  public void setOnContentSizeChange(RNCWebViewContainer view, boolean sendContentSizeChangeEvents) {
    view.ifHasRNCWebView(webView -> webView.setSendContentSizeChangeEvents(sendContentSizeChangeEvents));
  }

  @ReactProp(name = "mixedContentMode")
  public void setMixedContentMode(RNCWebViewContainer view, @Nullable String mixedContentMode) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (mixedContentMode == null || "never".equals(mixedContentMode)) {
        view.ifHasRNCWebView(webView -> webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW));
      } else if ("always".equals(mixedContentMode)) {
        view.ifHasRNCWebView(webView -> webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW));
      } else if ("compatibility".equals(mixedContentMode)) {
        view.ifHasRNCWebView(webView -> webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE));
      }
    }
  }

  @ReactProp(name = "urlPrefixesForDefaultIntent")
  public void setUrlPrefixesForDefaultIntent(
    RNCWebViewContainer view,
    @Nullable ReadableArray urlPrefixesForDefaultIntent) {
    view.ifHasRNCWebView(webView -> {
      RNCWebViewClient client = webView.getRNCWebViewClient();
      if (client != null && urlPrefixesForDefaultIntent != null) {
        client.setUrlPrefixesForDefaultIntent(urlPrefixesForDefaultIntent);
      }
    });
  }

  @ReactProp(name = "allowsFullscreenVideo")
  public void setAllowsFullscreenVideo(
    RNCWebViewContainer view,
    @Nullable Boolean allowsFullscreenVideo) {
    mAllowsFullscreenVideo = allowsFullscreenVideo != null && allowsFullscreenVideo;
    view.ifHasRNCWebView(webView -> setupWebChromeClient((ReactContext)view.getContext(), webView));
  }

  @ReactProp(name = "allowFileAccess")
  public void setAllowFileAccess(
    RNCWebViewContainer view,
    @Nullable Boolean allowFileAccess) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setAllowFileAccess(allowFileAccess != null && allowFileAccess));
  }

  @ReactProp(name = "geolocationEnabled")
  public void setGeolocationEnabled(
    RNCWebViewContainer view,
    @Nullable Boolean isGeolocationEnabled) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setGeolocationEnabled(isGeolocationEnabled != null && isGeolocationEnabled));
  }

  @ReactProp(name = "onScroll")
  public void setOnScroll(RNCWebViewContainer view, boolean hasScrollEvent) {
    view.ifHasRNCWebView(webView -> webView.setHasScrollEvent(hasScrollEvent));
  }

  @ReactProp(name = "forceDarkOn")
  public void setForceDarkOn(RNCWebViewContainer view, boolean enabled) {
    view.ifHasRNCWebView(webView -> {
      // Only Android 10+ support dark mode
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        // Switch WebView dark mode
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
          int forceDarkMode = enabled ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_OFF;
          WebSettingsCompat.setForceDark(webView.getSettings(), forceDarkMode);
        }

        // Set how WebView content should be darkened.
        // PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING:  checks for the "color-scheme" <meta> tag.
        // If present, it uses media queries. If absent, it applies user-agent (automatic)
        // More information about Force Dark Strategy can be found here:
        // https://developer.android.com/reference/androidx/webkit/WebSettingsCompat#setForceDarkStrategy(android.webkit.WebSettings)
        if (enabled && WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
          WebSettingsCompat.setForceDarkStrategy(webView.getSettings(), WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
        }
      }
    });
  }

  @ReactProp(name = "minimumFontSize")
  public void setMinimumFontSize(RNCWebViewContainer view, int fontSize) {
    view.ifHasRNCWebView(webView -> webView.getSettings().setMinimumFontSize(fontSize));
  }

  @ReactProp(name = "allowsProtectedMedia")
  public void setAllowsProtectedMedia(WebView view, boolean enabled) {
    // This variable is used to keep consistency
    // in case a new WebChromeClient is created
    // (eg. when mAllowsFullScreenVideo changes)
    mAllowsProtectedMedia = enabled;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WebChromeClient client = view.getWebChromeClient();
        if (client != null && client instanceof RNCWebChromeClient) {
            ((RNCWebChromeClient) client).setAllowsProtectedMedia(enabled);
        }
    }
  }

  @Override
  protected void addEventEmitters(ThemedReactContext reactContext, RNCWebViewContainer view) {
    // Do not register default touch emitter and let WebView implementation handle touches
    view.ifHasRNCWebView(webView -> webView.setWebViewClient(new RNCWebViewClient()));
  }

  @Override
  public Map getExportedCustomDirectEventTypeConstants() {
    Map export = super.getExportedCustomDirectEventTypeConstants();
    if (export == null) {
      export = MapBuilder.newHashMap();
    }
    // Default events but adding them here explicitly for clarity
    export.put(TopLoadingStartEvent.EVENT_NAME, MapBuilder.of("registrationName", "onLoadingStart"));
    export.put(TopLoadingFinishEvent.EVENT_NAME, MapBuilder.of("registrationName", "onLoadingFinish"));
    export.put(TopLoadingErrorEvent.EVENT_NAME, MapBuilder.of("registrationName", "onLoadingError"));
    export.put(TopMessageEvent.EVENT_NAME, MapBuilder.of("registrationName", "onMessage"));
    // !Default events but adding them here explicitly for clarity

    export.put(TopLoadingProgressEvent.EVENT_NAME, MapBuilder.of("registrationName", "onLoadingProgress"));
    export.put(TopShouldStartLoadWithRequestEvent.EVENT_NAME, MapBuilder.of("registrationName", "onShouldStartLoadWithRequest"));
    export.put(ScrollEventType.getJSEventName(ScrollEventType.SCROLL), MapBuilder.of("registrationName", "onScroll"));
    export.put(TopHttpErrorEvent.EVENT_NAME, MapBuilder.of("registrationName", "onHttpError"));
    export.put(TopRenderProcessGoneEvent.EVENT_NAME, MapBuilder.of("registrationName", "onRenderProcessGone"));
    return export;
  }

  @Override
  public @Nullable
  Map<String, Integer> getCommandsMap() {
    return MapBuilder.<String, Integer>builder()
      .put("goBack", COMMAND_GO_BACK)
      .put("goForward", COMMAND_GO_FORWARD)
      .put("reload", COMMAND_RELOAD)
      .put("stopLoading", COMMAND_STOP_LOADING)
      .put("postMessage", COMMAND_POST_MESSAGE)
      .put("injectJavaScript", COMMAND_INJECT_JAVASCRIPT)
      .put("loadUrl", COMMAND_LOAD_URL)
      .put("requestFocus", COMMAND_FOCUS)
      .put("clearFormData", COMMAND_CLEAR_FORM_DATA)
      .put("clearCache", COMMAND_CLEAR_CACHE)
      .put("clearHistory", COMMAND_CLEAR_HISTORY)
      .put("release", COMMAND_RELEASE)
      .build();
  }

  @Override
  public void receiveCommand(@NonNull RNCWebViewContainer view, String commandId, @Nullable ReadableArray args) {
    view.ifHasRNCWebView(root -> {
      switch (commandId) {
        case "goBack":
          root.goBack();
          break;
        case "goForward":
          root.goForward();
          break;
        case "reload":
          root.reload();
          break;
        case "stopLoading":
          root.stopLoading();
          break;
        case "postMessage":
          try {
            RNCWebView reactWebView = (RNCWebView) root;
            JSONObject eventInitDict = new JSONObject();
            eventInitDict.put("data", args.getString(0));
            reactWebView.evaluateJavascriptWithFallback("(function () {" +
              "var event;" +
              "var data = " + eventInitDict.toString() + ";" +
              "try {" +
              "event = new MessageEvent('message', data);" +
              "} catch (e) {" +
              "event = document.createEvent('MessageEvent');" +
              "event.initMessageEvent('message', true, true, data.data, data.origin, data.lastEventId, data.source);" +
              "}" +
              "document.dispatchEvent(event);" +
              "})();");
          } catch (JSONException e) {
            throw new RuntimeException(e);
          }
          break;
        case "injectJavaScript":
          RNCWebView reactWebView = (RNCWebView) root;
          reactWebView.evaluateJavascriptWithFallback(args.getString(0));
          break;
        case "loadUrl":
          if (args == null) {
            throw new RuntimeException("Arguments for loading an url are null!");
          }
          ((RNCWebView) root).progressChangedFilter.setWaitingForCommandLoadUrl(false);
          root.loadUrl(args.getString(0));
          break;
        case "requestFocus":
          root.requestFocus();
          break;
        case "clearFormData":
          root.clearFormData();
          break;
        case "clearCache":
          boolean includeDiskFiles = args != null && args.getBoolean(0);
          root.clearCache(includeDiskFiles);
          break;
        case "clearHistory":
          root.clearHistory();
          break;
        case "release":
          // no-op for now
          break;
      }
    });
    super.receiveCommand(view, commandId, args);
  }

  @Override
  public void onDropViewInstance(RNCWebViewContainer view) {
    super.onDropViewInstance(view);

    // The internal webview can be null since the view may have been already reattached
    if (view.getWebView() == null) {
      return;
    }

    view.ifHasRNCWebView(webView -> {
      if (webView.webViewKey == null) {
        ((ThemedReactContext) webView.getContext()).removeLifecycleEventListener(webView);
        webView.cleanupCallbacksAndDestroy();
        mWebChromeClient = null;
      } else {
        view.removeWebViewFromParent();
        RNCWebViewMapManager.INSTANCE.getViewIdMap().remove(webView.getId());

        if (view.temporaryParentNodeTag != 0) {
          // Re-attach the internal webview to the temporary parent.
          UIManagerModule uiManagerModule = ((ReactContext) view.getContext()).getNativeModule(UIManagerModule.class);
          ViewGroup temporaryParentView = (ViewGroup)uiManagerModule.resolveView(view.temporaryParentNodeTag);
          temporaryParentView.addView(webView);

          // Resize view to match parent
          webView.measure(
            View.MeasureSpec.makeMeasureSpec(temporaryParentView.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(temporaryParentView.getMeasuredHeight(), View.MeasureSpec.EXACTLY)
          );

          webView.layout(0, 0, webView.getMeasuredWidth(), webView.getMeasuredHeight());
        }
      }
    });
  }

  public static RNCWebViewModule getModule(ReactContext reactContext) {
    return reactContext.getNativeModule(RNCWebViewModule.class);
  }

  protected void setupWebChromeClient(ReactContext reactContext, WebView webView) {
    Activity activity = reactContext.getCurrentActivity();

    if (mAllowsFullscreenVideo && activity != null) {
      int initialRequestedOrientation = activity.getRequestedOrientation();

      mWebChromeClient = new RNCWebChromeClient(reactContext, webView) {
        @Override
        public Bitmap getDefaultVideoPoster() {
          return Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
          if (mVideoView != null) {
            callback.onCustomViewHidden();
            return;
          }

          mVideoView = view;
          mCustomViewCallback = callback;

          activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mVideoView.setSystemUiVisibility(FULLSCREEN_SYSTEM_UI_VISIBILITY);
            activity.getWindow().setFlags(
              WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
              WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );
          }

          mVideoView.setBackgroundColor(Color.BLACK);

          // Since RN's Modals interfere with the View hierarchy
          // we will decide which View to hide if the hierarchy
          // does not match (i.e., the WebView is within a Modal)
          // NOTE: We could use `mWebView.getRootView()` instead of `getRootView()`
          // but that breaks the Modal's styles and layout, so we need this to render
          // in the main View hierarchy regardless
          ViewGroup rootView = getRootView();
          rootView.addView(mVideoView, FULLSCREEN_LAYOUT_PARAMS);

          // Different root views, we are in a Modal
          if (rootView.getRootView() != mWebView.getRootView()) {
            mWebView.getRootView().setVisibility(View.GONE);
          } else {
            // Same view hierarchy (no Modal), just hide the WebView then
            mWebView.setVisibility(View.GONE);
          }

          mReactContext.addLifecycleEventListener(this);
        }

        @Override
        public void onHideCustomView() {
          if (mVideoView == null) {
            return;
          }

          // Same logic as above
          ViewGroup rootView = getRootView();

          if (rootView.getRootView() != mWebView.getRootView()) {
            mWebView.getRootView().setVisibility(View.VISIBLE);
          } else {
            // Same view hierarchy (no Modal)
            mWebView.setVisibility(View.VISIBLE);
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
          }

          rootView.removeView(mVideoView);
          mCustomViewCallback.onCustomViewHidden();

          mVideoView = null;
          mCustomViewCallback = null;

          activity.setRequestedOrientation(initialRequestedOrientation);

          mReactContext.removeLifecycleEventListener(this);
        }
      };
    } else {
      if (mWebChromeClient != null) {
        mWebChromeClient.onHideCustomView();
      }

      mWebChromeClient = new RNCWebChromeClient(reactContext, webView) {
        @Override
        public Bitmap getDefaultVideoPoster() {
          return Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        }
      };
    }
    mWebChromeClient.setAllowsProtectedMedia(mAllowsProtectedMedia);
    webView.setWebChromeClient(mWebChromeClient);
  }

  protected static class RNCWebViewClient extends WebViewClient {

    protected boolean mLastLoadFailed = false;
    protected @Nullable
    ReadableArray mUrlPrefixesForDefaultIntent;
    protected RNCWebView.ProgressChangedFilter progressChangedFilter = null;
    protected @Nullable String ignoreErrFailedForThisURL = null;
    protected @Nullable BasicAuthCredential basicAuthCredential = null;
    protected @Nullable WebViewAssetLoader webViewAssetLoader;

    public void setWebViewAssetLoader(@Nullable WebViewAssetLoader assetLoader) {
      webViewAssetLoader = assetLoader;
    }

    public void setIgnoreErrFailedForThisURL(@Nullable String url) {
      ignoreErrFailedForThisURL = url;
    }

    public void setBasicAuthCredential(@Nullable BasicAuthCredential credential) {
      basicAuthCredential = credential;
    }

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
      if (webViewAssetLoader == null) {
        return super.shouldInterceptRequest(view, request);
      }

      return webViewAssetLoader.shouldInterceptRequest(request.getUrl());
    }

    @Override
    public void onPageFinished(WebView webView, String url) {
      super.onPageFinished(webView, url);

      if (!mLastLoadFailed) {
        RNCWebView reactWebView = (RNCWebView) webView;

        reactWebView.callInjectedJavaScript();

        emitFinishEvent(webView, url);
      }
    }

    @Override
    public void onPageStarted(WebView webView, String url, Bitmap favicon) {
      super.onPageStarted(webView, url, favicon);
      mLastLoadFailed = false;

      RNCWebView reactWebView = (RNCWebView) webView;
      reactWebView.callInjectedJavaScriptBeforeContentLoaded();

      ((RNCWebView) webView).dispatchEvent(
        webView,
        new TopLoadingStartEvent(
          RNCWebViewContainer.getRNCWebViewId(webView),
          createWebViewEvent(webView, url)));
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      final RNCWebView RNCWebView = (RNCWebView) view;
      final boolean isJsDebugging = ((ReactContext) view.getContext()).getJavaScriptContextHolder().get() == 0;

      if (!isJsDebugging && RNCWebView.mCatalystInstance != null) {
        final Pair<Integer, AtomicReference<ShouldOverrideCallbackState>> lock = RNCWebViewModule.shouldOverrideUrlLoadingLock.getNewLock();
        final int lockIdentifier = lock.first;
        final AtomicReference<ShouldOverrideCallbackState> lockObject = lock.second;

        final WritableMap event = createWebViewEvent(view, url);
        event.putInt("lockIdentifier", lockIdentifier);
        RNCWebView.sendDirectMessage("onShouldStartLoadWithRequest", event);

        try {
          assert lockObject != null;
          synchronized (lockObject) {
            final long startTime = SystemClock.elapsedRealtime();
            while (lockObject.get() == ShouldOverrideCallbackState.UNDECIDED) {
              if (SystemClock.elapsedRealtime() - startTime > SHOULD_OVERRIDE_URL_LOADING_TIMEOUT) {
                FLog.w(TAG, "Did not receive response to shouldOverrideUrlLoading in time, defaulting to allow loading.");
                RNCWebViewModule.shouldOverrideUrlLoadingLock.removeLock(lockIdentifier);
                return false;
              }
              lockObject.wait(SHOULD_OVERRIDE_URL_LOADING_TIMEOUT);
            }
          }
        } catch (InterruptedException e) {
          FLog.e(TAG, "shouldOverrideUrlLoading was interrupted while waiting for result.", e);
          RNCWebViewModule.shouldOverrideUrlLoadingLock.removeLock(lockIdentifier);
          return false;
        }

        final boolean shouldOverride = lockObject.get() == ShouldOverrideCallbackState.SHOULD_OVERRIDE;
        RNCWebViewModule.shouldOverrideUrlLoadingLock.removeLock(lockIdentifier);

        return shouldOverride;
      } else {
        FLog.w(TAG, "Couldn't use blocking synchronous call for onShouldStartLoadWithRequest due to debugging or missing Catalyst instance, falling back to old event-and-load.");
        progressChangedFilter.setWaitingForCommandLoadUrl(true);
        ((RNCWebView) view).dispatchEvent(
          view,
          new TopShouldStartLoadWithRequestEvent(
            RNCWebViewContainer.getRNCWebViewId(view),
            createWebViewEvent(view, url)));
        return true;
      }
    }

    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
      final String url = request.getUrl().toString();
      return this.shouldOverrideUrlLoading(view, url);
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
      if (basicAuthCredential != null) {
        handler.proceed(basicAuthCredential.username, basicAuthCredential.password);
        return;
      }
      super.onReceivedHttpAuthRequest(view, handler, host, realm);
    }

    @Override
    public void onReceivedSslError(final WebView webView, final SslErrorHandler handler, final SslError error) {
        // onReceivedSslError is called for most requests, per Android docs: https://developer.android.com/reference/android/webkit/WebViewClient#onReceivedSslError(android.webkit.WebView,%2520android.webkit.SslErrorHandler,%2520android.net.http.SslError)
        // WebView.getUrl() will return the top-level window URL.
        // If a top-level navigation triggers this error handler, the top-level URL will be the failing URL (not the URL of the currently-rendered page).
        // This is desired behavior. We later use these values to determine whether the request is a top-level navigation or a subresource request.
        String topWindowUrl = webView.getUrl();
        String failingUrl = error.getUrl();

        // Cancel request after obtaining top-level URL.
        // If request is cancelled before obtaining top-level URL, undesired behavior may occur.
        // Undesired behavior: Return value of WebView.getUrl() may be the current URL instead of the failing URL.
        handler.cancel();

        if (!topWindowUrl.equalsIgnoreCase(failingUrl)) {
          // If error is not due to top-level navigation, then do not call onReceivedError()
          Log.w(TAG, "Resource blocked from loading due to SSL error. Blocked URL: "+failingUrl);
          return;
        }

        int code = error.getPrimaryError();
        String description = "";
        String descriptionPrefix = "SSL error: ";

        // https://developer.android.com/reference/android/net/http/SslError.html
        switch (code) {
          case SslError.SSL_DATE_INVALID:
            description = "The date of the certificate is invalid";
            break;
          case SslError.SSL_EXPIRED:
            description = "The certificate has expired";
            break;
          case SslError.SSL_IDMISMATCH:
            description = "Hostname mismatch";
            break;
          case SslError.SSL_INVALID:
            description = "A generic error occurred";
            break;
          case SslError.SSL_NOTYETVALID:
            description = "The certificate is not yet valid";
            break;
          case SslError.SSL_UNTRUSTED:
            description = "The certificate authority is not trusted";
            break;
          default:
            description = "Unknown SSL Error";
            break;
        }

        description = descriptionPrefix + description;

        this.onReceivedError(
          webView,
          code,
          description,
          failingUrl
        );
    }

    @Override
    public void onReceivedError(
      WebView webView,
      int errorCode,
      String description,
      String failingUrl) {

      if (ignoreErrFailedForThisURL != null
          && failingUrl.equals(ignoreErrFailedForThisURL)
          && errorCode == -1
          && description.equals("net::ERR_FAILED")) {

        // This is a workaround for a bug in the WebView.
        // See these chromium issues for more context:
        // https://bugs.chromium.org/p/chromium/issues/detail?id=1023678
        // https://bugs.chromium.org/p/chromium/issues/detail?id=1050635
        // This entire commit should be reverted once this bug is resolved in chromium.
        setIgnoreErrFailedForThisURL(null);
        return;
      }

      super.onReceivedError(webView, errorCode, description, failingUrl);
      mLastLoadFailed = true;

      // In case of an error JS side expect to get a finish event first, and then get an error event
      // Android WebView does it in the opposite way, so we need to simulate that behavior
      emitFinishEvent(webView, failingUrl);

      WritableMap eventData = createWebViewEvent(webView, failingUrl);
      eventData.putDouble("code", errorCode);
      eventData.putString("description", description);

      ((RNCWebView) webView).dispatchEvent(
        webView,
        new TopLoadingErrorEvent(RNCWebViewContainer.getRNCWebViewId(webView), eventData));
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onReceivedHttpError(
      WebView webView,
      WebResourceRequest request,
      WebResourceResponse errorResponse) {
      super.onReceivedHttpError(webView, request, errorResponse);

      if (request.isForMainFrame()) {
        WritableMap eventData = createWebViewEvent(webView, request.getUrl().toString());
        eventData.putInt("statusCode", errorResponse.getStatusCode());
        eventData.putString("description", errorResponse.getReasonPhrase());

        ((RNCWebView) webView).dispatchEvent(
          webView,
          new TopHttpErrorEvent(RNCWebViewContainer.getRNCWebViewId(webView), eventData));
      }
    }

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail detail) {
        // WebViewClient.onRenderProcessGone was added in O.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        super.onRenderProcessGone(webView, detail);

        if(detail.didCrash()){
          Log.e(TAG, "The WebView rendering process crashed.");
        }
        else{
          Log.w(TAG, "The WebView rendering process was killed by the system.");
        }

        // if webView is null, we cannot return any event
        // since the view is already dead/disposed
        // still prevent the app crash by returning true.
        if(webView == null){
          return true;
        }

        WritableMap event = createWebViewEvent(webView, webView.getUrl());
        event.putBoolean("didCrash", detail.didCrash());

      ((RNCWebView) webView).dispatchEvent(
          webView,
          new TopRenderProcessGoneEvent(RNCWebViewContainer.getRNCWebViewId(webView), event)
        );

        // returning false would crash the app.
        return true;
    }

    protected void emitFinishEvent(WebView webView, String url) {
      ((RNCWebView) webView).dispatchEvent(
        webView,
        new TopLoadingFinishEvent(
          RNCWebViewContainer.getRNCWebViewId(webView),
          createWebViewEvent(webView, url)));
    }

    protected WritableMap createWebViewEvent(WebView webView, String url) {
      WritableMap event = Arguments.createMap();
      event.putDouble("target", RNCWebViewContainer.getRNCWebViewId(webView));
      // Don't use webView.getUrl() here, the URL isn't updated to the new value yet in callbacks
      // like onPageFinished
      event.putString("url", url);
      event.putBoolean("loading", !mLastLoadFailed && webView.getProgress() != 100);
      event.putString("title", webView.getTitle());
      event.putBoolean("canGoBack", webView.canGoBack());
      event.putBoolean("canGoForward", webView.canGoForward());
      return event;
    }

    public void setUrlPrefixesForDefaultIntent(ReadableArray specialUrls) {
      mUrlPrefixesForDefaultIntent = specialUrls;
    }

    public void setProgressChangedFilter(RNCWebView.ProgressChangedFilter filter) {
      progressChangedFilter = filter;
    }
  }

  protected static class RNCWebChromeClient extends WebChromeClient implements LifecycleEventListener {
    protected static final FrameLayout.LayoutParams FULLSCREEN_LAYOUT_PARAMS = new FrameLayout.LayoutParams(
      LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER);

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    protected static final int FULLSCREEN_SYSTEM_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
      View.SYSTEM_UI_FLAG_FULLSCREEN |
      View.SYSTEM_UI_FLAG_IMMERSIVE |
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    protected static final int COMMON_PERMISSION_REQUEST = 3;

    protected ReactContext mReactContext;
    protected View mWebView;

    protected View mVideoView;
    protected WebChromeClient.CustomViewCallback mCustomViewCallback;

    /*
     * - Permissions -
     * As native permissions are asynchronously handled by the PermissionListener, many fields have
     * to be stored to send permissions results to the webview
     */

    // Webview camera & audio permission callback
    protected PermissionRequest permissionRequest;
    // Webview camera & audio permission already granted
    protected List<String> grantedPermissions;

    // Webview geolocation permission callback
    protected GeolocationPermissions.Callback geolocationPermissionCallback;
    // Webview geolocation permission origin callback
    protected String geolocationPermissionOrigin;

    // true if native permissions dialog is shown, false otherwise
    protected boolean permissionsRequestShown = false;
    // Pending Android permissions for the next request
    protected List<String> pendingPermissions = new ArrayList<>();

    protected RNCWebView.ProgressChangedFilter progressChangedFilter = null;

    // True if protected media should be allowed, false otherwise
    protected boolean mAllowsProtectedMedia = false;

    public RNCWebChromeClient(ReactContext reactContext, WebView webView) {
      this.mReactContext = reactContext;
      this.mWebView = webView;
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {

      final WebView newWebView = new WebView(view.getContext());
      final WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
      transport.setWebView(newWebView);
      resultMsg.sendToTarget();

      return true;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage message) {
      if (ReactBuildConfig.DEBUG) {
        return super.onConsoleMessage(message);
      }
      // Ignore console logs in non debug builds.
      return true;
    }

    @Override
    public void onProgressChanged(WebView webView, int newProgress) {
      super.onProgressChanged(webView, newProgress);
      final String url = webView.getUrl();
      if (progressChangedFilter.isWaitingForCommandLoadUrl()) {
        return;
      }
      WritableMap event = Arguments.createMap();
      event.putDouble("target", RNCWebViewContainer.getRNCWebViewId(webView));
      event.putString("title", webView.getTitle());
      event.putString("url", url);
      event.putBoolean("canGoBack", webView.canGoBack());
      event.putBoolean("canGoForward", webView.canGoForward());
      event.putDouble("progress", (float) newProgress / 100);
      ((RNCWebView) webView).dispatchEvent(
        webView,
        new TopLoadingProgressEvent(
          RNCWebViewContainer.getRNCWebViewId(webView),
          event));
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPermissionRequest(final PermissionRequest request) {

      grantedPermissions = new ArrayList<>();

      ArrayList<String> requestedAndroidPermissions = new ArrayList<>();
      for (String requestedResource : request.getResources()) {
        String androidPermission = null;

        if (requestedResource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
          androidPermission = Manifest.permission.RECORD_AUDIO;
        } else if (requestedResource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
          androidPermission = Manifest.permission.CAMERA;
        } else if(requestedResource.equals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
          if (mAllowsProtectedMedia) {
              grantedPermissions.add(requestedResource);
          } else {
              /**
               * Legacy handling (Kept in case it was working under some conditions (given Android version or something))
               *
               * Try to ask user to grant permission using Activity.requestPermissions
               *
               * Find more details here: https://github.com/react-native-webview/react-native-webview/pull/2732
               */
              androidPermission = PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID;
          }
        }
        // TODO: RESOURCE_MIDI_SYSEX.

        if (androidPermission != null) {
          if (ContextCompat.checkSelfPermission(mReactContext, androidPermission) == PackageManager.PERMISSION_GRANTED) {
            grantedPermissions.add(requestedResource);
          } else {
            requestedAndroidPermissions.add(androidPermission);
          }
        }
      }

      // If all the permissions are already granted, send the response to the WebView synchronously
      if (requestedAndroidPermissions.isEmpty()) {
        request.grant(grantedPermissions.toArray(new String[0]));
        grantedPermissions = null;
        return;
      }

      // Otherwise, ask to Android System for native permissions asynchronously

      this.permissionRequest = request;

      requestPermissions(requestedAndroidPermissions);
    }


    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {

      if (ContextCompat.checkSelfPermission(mReactContext, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {

        /*
         * Keep the trace of callback and origin for the async permission request
         */
        geolocationPermissionCallback = callback;
        geolocationPermissionOrigin = origin;

        requestPermissions(Collections.singletonList(Manifest.permission.ACCESS_FINE_LOCATION));

      } else {
        callback.invoke(origin, true, false);
      }
    }

    private PermissionAwareActivity getPermissionAwareActivity() {
      Activity activity = mReactContext.getCurrentActivity();
      if (activity == null) {
        throw new IllegalStateException("Tried to use permissions API while not attached to an Activity.");
      } else if (!(activity instanceof PermissionAwareActivity)) {
        throw new IllegalStateException("Tried to use permissions API but the host Activity doesn't implement PermissionAwareActivity.");
      }
      return (PermissionAwareActivity) activity;
    }

    private synchronized void requestPermissions(List<String> permissions) {

      /*
       * If permissions request dialog is displayed on the screen and another request is sent to the
       * activity, the last permission asked is skipped. As a work-around, we use pendingPermissions
       * to store next required permissions.
       */

      if (permissionsRequestShown) {
        pendingPermissions.addAll(permissions);
        return;
      }

      PermissionAwareActivity activity = getPermissionAwareActivity();
      permissionsRequestShown = true;

      activity.requestPermissions(
        permissions.toArray(new String[0]),
        COMMON_PERMISSION_REQUEST,
        webviewPermissionsListener
      );

      // Pending permissions have been sent, the list can be cleared
      pendingPermissions.clear();
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private PermissionListener webviewPermissionsListener = (requestCode, permissions, grantResults) -> {

      permissionsRequestShown = false;

      /*
       * As a "pending requests" approach is used, requestCode cannot help to define if the request
       * came from geolocation or camera/audio. This is why shouldAnswerToPermissionRequest is used
       */
      boolean shouldAnswerToPermissionRequest = false;

      for (int i = 0; i < permissions.length; i++) {

        String permission = permissions[i];
        boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;

        if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)
          && geolocationPermissionCallback != null
          && geolocationPermissionOrigin != null) {

          if (granted) {
            geolocationPermissionCallback.invoke(geolocationPermissionOrigin, true, false);
          } else {
            geolocationPermissionCallback.invoke(geolocationPermissionOrigin, false, false);
          }

          geolocationPermissionCallback = null;
          geolocationPermissionOrigin = null;
        }

        if (permission.equals(Manifest.permission.RECORD_AUDIO)) {
          if (granted && grantedPermissions != null) {
            grantedPermissions.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
          }
          shouldAnswerToPermissionRequest = true;
        }

        if (permission.equals(Manifest.permission.CAMERA)) {
          if (granted && grantedPermissions != null) {
            grantedPermissions.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
          }
          shouldAnswerToPermissionRequest = true;
        }

        if (permission.equals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
          if (granted && grantedPermissions != null) {
            grantedPermissions.add(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
          }
          shouldAnswerToPermissionRequest = true;
        }
      }

      if (shouldAnswerToPermissionRequest
        && permissionRequest != null
        && grantedPermissions != null) {
        permissionRequest.grant(grantedPermissions.toArray(new String[0]));
        permissionRequest = null;
        grantedPermissions = null;
      }

      if (!pendingPermissions.isEmpty()) {
        requestPermissions(pendingPermissions);
        return false;
      }

      return true;
    };

    protected void openFileChooser(ValueCallback<Uri> filePathCallback, String acceptType) {
      getModule(mReactContext).startPhotoPickerIntent(filePathCallback, acceptType);
    }

    protected void openFileChooser(ValueCallback<Uri> filePathCallback) {
      getModule(mReactContext).startPhotoPickerIntent(filePathCallback, "");
    }

    protected void openFileChooser(ValueCallback<Uri> filePathCallback, String acceptType, String capture) {
      getModule(mReactContext).startPhotoPickerIntent(filePathCallback, acceptType);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
      String[] acceptTypes = fileChooserParams.getAcceptTypes();
      boolean allowMultiple = fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
      return getModule(mReactContext).startPhotoPickerIntent(filePathCallback, acceptTypes, allowMultiple);
    }

    @Override
    public void onHostResume() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && mVideoView != null && mVideoView.getSystemUiVisibility() != FULLSCREEN_SYSTEM_UI_VISIBILITY) {
        mVideoView.setSystemUiVisibility(FULLSCREEN_SYSTEM_UI_VISIBILITY);
      }
    }

    @Override
    public void onHostPause() { }

    @Override
    public void onHostDestroy() { }

    protected ViewGroup getRootView() {
      return (ViewGroup) mReactContext.getCurrentActivity().findViewById(android.R.id.content);
    }

    public void setProgressChangedFilter(RNCWebView.ProgressChangedFilter filter) {
      progressChangedFilter = filter;
    }

    /**
     * Set whether or not protected media should be allowed
     * /!\ Setting this to false won't revoke permission already granted to the current webpage.
     * In order to do so, you'd need to reload the page /!\
     */
    public void setAllowsProtectedMedia(boolean enabled) {
        mAllowsProtectedMedia = enabled;
    }
  }

  /**
   * Subclass of {@link WebView} that implements {@link LifecycleEventListener} interface in order
   * to call {@link WebView#destroy} on activity destroy event and also to clear the client
   */
  protected static class RNCWebView extends WebView implements LifecycleEventListener {
    protected @Nullable
    String injectedJS;
    protected @Nullable
    String injectedJSBeforeContentLoaded;

    /**
     * android.webkit.WebChromeClient fundamentally does not support JS injection into frames other
     * than the main frame, so these two properties are mostly here just for parity with iOS & macOS.
     */
    protected boolean injectedJavaScriptForMainFrameOnly = true;
    protected boolean injectedJavaScriptBeforeContentLoadedForMainFrameOnly = true;

    protected boolean messagingEnabled = false;

    protected @Nullable
    String webViewKey;


    protected @Nullable
    String messagingModuleName;
    protected @Nullable
    RNCWebViewClient mRNCWebViewClient;
    protected @Nullable
    CatalystInstance mCatalystInstance;
    protected boolean sendContentSizeChangeEvents = false;
    private OnScrollDispatchHelper mOnScrollDispatchHelper;
    protected boolean hasScrollEvent = false;
    protected boolean nestedScrollEnabled = false;
    protected ProgressChangedFilter progressChangedFilter;

    protected ReadableMap source;

    /**
     * WebView must be created with an context of the current activity
     * <p>
     * Activity Context is required for creation of dialogs internally by WebView
     * Reactive Native needed for access to ReactNative internal system functionality
     */
    public RNCWebView(ThemedReactContext reactContext) {
      super(reactContext);
      this.createCatalystInstance();
      progressChangedFilter = new ProgressChangedFilter();

      /**
       * Default the background color to transparent to avoid flashing a white frame
       * when initializing the WebView. The React CSS background color will get applied
       * to the RNCWebView whose background should show behind this transparent background
       * until the WebView has non-transparent content.
       */
      setBackgroundColor(Color.TRANSPARENT);
    }

    public void setIgnoreErrFailedForThisURL(String url) {
      mRNCWebViewClient.setIgnoreErrFailedForThisURL(url);
    }

    public void setBasicAuthCredential(BasicAuthCredential credential) {
      mRNCWebViewClient.setBasicAuthCredential(credential);
    }

    public void setWebViewAssetLoader(WebViewAssetLoader webViewAssetLoader) {
      mRNCWebViewClient.setWebViewAssetLoader(webViewAssetLoader);
    }

    public void setSendContentSizeChangeEvents(boolean sendContentSizeChangeEvents) {
      this.sendContentSizeChangeEvents = sendContentSizeChangeEvents;
    }

    public void setHasScrollEvent(boolean hasScrollEvent) {
      this.hasScrollEvent = hasScrollEvent;
    }

    public void setNestedScrollEnabled(boolean nestedScrollEnabled) {
      this.nestedScrollEnabled = nestedScrollEnabled;
    }

    public void setSource(ReadableMap source) {
      this.source = source;
    }

    public boolean isNewSource(@Nullable ReadableMap newSource) {
      if (source == null || newSource == null) {
        return true;
      }

      // Check if any of the following string values have changed
      String[] sourceKeys = {"uri", "method", "body", "html", "baseUrl"};

      for (String key : sourceKeys) {
        String value = source.getString(key);
        String newValue = newSource.getString(key);
        if (newValue != null && !newValue.equals(value)) {
          return true;
        }
      }

      // Check if headers changed
      ReadableMap headersMap =  source.getMap("headers");
      ReadableMap newHeadersMap = newSource.getMap("headers");
      Map<String, Object> headers = headersMap == null ? Collections.emptyMap() : headersMap.toHashMap();
      Map<String, Object> newHeaders = newHeadersMap == null ? Collections.emptyMap() : newHeadersMap.toHashMap();

      return !headers.equals(newHeaders);
    }

    @Override
    public void onHostResume() {
      // do nothing
    }

    @Override
    public void onHostPause() {
      // do nothing
    }

    @Override
    public void onHostDestroy() {
      cleanupCallbacksAndDestroy();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      if (this.nestedScrollEnabled) {
        requestDisallowInterceptTouchEvent(true);
      }
      return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
      super.onSizeChanged(w, h, ow, oh);

      if (sendContentSizeChangeEvents) {
        dispatchEvent(
          this,
          new ContentSizeChangeEvent(
            RNCWebViewContainer.getRNCWebViewId(this),
            w,
            h
          )
        );
      }
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
      super.setWebViewClient(client);
      if (client instanceof RNCWebViewClient) {
        mRNCWebViewClient = (RNCWebViewClient) client;
        mRNCWebViewClient.setProgressChangedFilter(progressChangedFilter);
      }
    }

    WebChromeClient mWebChromeClient;
    @Override
    public void setWebChromeClient(WebChromeClient client) {
      this.mWebChromeClient = client;
      super.setWebChromeClient(client);
      if (client instanceof RNCWebChromeClient) {
        ((RNCWebChromeClient) client).setProgressChangedFilter(progressChangedFilter);
      }
    }

    public @Nullable
    RNCWebViewClient getRNCWebViewClient() {
      return mRNCWebViewClient;
    }

    public void setInjectedJavaScript(@Nullable String js) {
      injectedJS = js;
    }

    public void setInjectedJavaScriptBeforeContentLoaded(@Nullable String js) {
      injectedJSBeforeContentLoaded = js;
    }

    public void setInjectedJavaScriptForMainFrameOnly(boolean enabled) {
      injectedJavaScriptForMainFrameOnly = enabled;
    }

    public void setInjectedJavaScriptBeforeContentLoadedForMainFrameOnly(boolean enabled) {
      injectedJavaScriptBeforeContentLoadedForMainFrameOnly = enabled;
    }

    public void setWebViewKey(String webViewKey) {
      this.webViewKey = webViewKey;
    }

    protected RNCWebViewBridge createRNCWebViewBridge(RNCWebView webView) {
      return new RNCWebViewBridge(webView);
    }

    protected void createCatalystInstance() {
      ReactContext reactContext = (ReactContext) this.getContext();

      if (reactContext != null) {
        mCatalystInstance = reactContext.getCatalystInstance();
      }
    }

    @SuppressLint("AddJavascriptInterface")
    public void setMessagingEnabled(boolean enabled) {
      if (messagingEnabled == enabled) {
        return;
      }

      messagingEnabled = enabled;

      if (enabled) {
        addJavascriptInterface(createRNCWebViewBridge(this), JAVASCRIPT_INTERFACE);
      } else {
        removeJavascriptInterface(JAVASCRIPT_INTERFACE);
      }
    }

    public void setMessagingModuleName(String moduleName) {
      messagingModuleName = moduleName;
    }

    protected void evaluateJavascriptWithFallback(String script) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        evaluateJavascript(script, null);
        return;
      }

      try {
        loadUrl("javascript:" + URLEncoder.encode(script, "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        // UTF-8 should always be supported
        throw new RuntimeException(e);
      }
    }

    public void callInjectedJavaScript() {
      if (getSettings().getJavaScriptEnabled() &&
        injectedJS != null &&
        !TextUtils.isEmpty(injectedJS)) {
        evaluateJavascriptWithFallback("(function() {\n" + injectedJS + ";\n})();");
      }
    }

    public void callInjectedJavaScriptBeforeContentLoaded() {
      if (getSettings().getJavaScriptEnabled() &&
      injectedJSBeforeContentLoaded != null &&
      !TextUtils.isEmpty(injectedJSBeforeContentLoaded)) {
        evaluateJavascriptWithFallback("(function() {\n" + injectedJSBeforeContentLoaded + ";\n})();");
      }
    }

    public void onMessage(String message) {
      ReactContext reactContext = (ReactContext) this.getContext();
      RNCWebView mContext = this;
      WebView webView = this;

      if (webViewKey != null && mRNCWebViewClient != null) {
        reactContext.runOnUiQueueThread(() -> {
          WritableMap data = mRNCWebViewClient.createWebViewEvent(webView, webView.getUrl());
          data.putString("webViewKey", webViewKey);
          data.putString("data", message);
          reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("ReactNativeWebViewOnMessageWithWebViewKey", data);
        });
      } else if (mRNCWebViewClient != null) {
        webView.post(new Runnable() {
          @Override
          public void run() {
            if (mRNCWebViewClient == null) {
              return;
            }
            WritableMap data = mRNCWebViewClient.createWebViewEvent(webView, webView.getUrl());
            data.putString("data", message);

            if (mCatalystInstance != null) {
              mContext.sendDirectMessage("onMessage", data);
            } else {
              dispatchEvent(webView, new TopMessageEvent(RNCWebViewContainer.getRNCWebViewId(webView), data));
            }
          }
        });
      } else {
        WritableMap eventData = Arguments.createMap();
        eventData.putString("data", message);

        if (mCatalystInstance != null) {
          this.sendDirectMessage("onMessage", eventData);
        } else {
          dispatchEvent(this, new TopMessageEvent(RNCWebViewContainer.getRNCWebViewId(webView), eventData));
        }
      }
    }

    protected void sendDirectMessage(final String method, WritableMap data) {
      WritableNativeMap event = new WritableNativeMap();
      event.putMap("nativeEvent", data);

      WritableNativeArray params = new WritableNativeArray();
      params.pushMap(event);

      mCatalystInstance.callFunction(messagingModuleName, method, params);
    }

    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
      super.onScrollChanged(x, y, oldX, oldY);

      if (!hasScrollEvent) {
        return;
      }

      if (mOnScrollDispatchHelper == null) {
        mOnScrollDispatchHelper = new OnScrollDispatchHelper();
      }

      if (mOnScrollDispatchHelper.onScrollChanged(x, y)) {
        ScrollEvent event = ScrollEvent.obtain(
                RNCWebViewContainer.getRNCWebViewId(this),
                ScrollEventType.SCROLL,
                x,
                y,
                mOnScrollDispatchHelper.getXFlingVelocity(),
                mOnScrollDispatchHelper.getYFlingVelocity(),
                this.computeHorizontalScrollRange(),
                this.computeVerticalScrollRange(),
                this.getWidth(),
                this.getHeight());

        dispatchEvent(this, event);
      }
    }

    protected void dispatchEvent(WebView webView, Event event) {
      if (event.getViewTag() == RNCWebViewContainer.INVALID_VIEW_ID) {
        FLog.w(TAG, "Unable to dispatch event: ", event.getEventName() + "due to RNCWebView not being attached.");
        return;
      }

      ReactContext reactContext = (ReactContext) webView.getContext();
      EventDispatcher eventDispatcher =
        reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
      eventDispatcher.dispatchEvent(event);
    }

    protected void cleanupCallbacksAndDestroy() {
      setWebViewClient(null);
      destroy();
    }

    @Override
    public void destroy() {
      if (mWebChromeClient != null) {
        mWebChromeClient.onHideCustomView();
      }
      super.destroy();
    }

    protected class RNCWebViewBridge {
      RNCWebView mContext;

      RNCWebViewBridge(RNCWebView c) {
        mContext = c;
      }

      /**
       * This method is called whenever JavaScript running within the web view calls:
       * - window[JAVASCRIPT_INTERFACE].postMessage
       */
      @JavascriptInterface
      public void postMessage(String message) {
        mContext.onMessage(message);
      }
    }

    protected static class ProgressChangedFilter {
      private boolean waitingForCommandLoadUrl = false;

      public void setWaitingForCommandLoadUrl(boolean isWaiting) {
        waitingForCommandLoadUrl = isWaiting;
      }

      public boolean isWaitingForCommandLoadUrl() {
        return waitingForCommandLoadUrl;
      }
    }
  }
}

class BasicAuthCredential {
  String username;
  String password;

  BasicAuthCredential(String username, String password) {
    this.username = username;
    this.password = password;
  }
}
