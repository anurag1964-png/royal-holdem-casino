package com.playzonestudio.royalholdem;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;

public class MainActivity extends AppCompatActivity implements IUnityAdsInitializationListener {

    private static final String UNITY_GAME_ID = "80043681";
    private static final String REWARDED_PLACEMENT_ID = "Rewarded_Android";
    private static final String INTERSTITIAL_PLACEMENT_ID = "Interstitial_Android";
    private static final boolean TEST_MODE = false;

    private WebView webView;
    private boolean isRewardedReady = false;
    private boolean isInterstitialReady = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new UnityAdsBridge(), "AndroidUnityAds");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectSmartAdHooks();
            }
        });

        // Initialize Unity Ads SDK
        UnityAds.initialize(getApplicationContext(), UNITY_GAME_ID, TEST_MODE, this);

        // Load user's index.html from assets
        webView.loadUrl("file:///android_asset/index.html");
    }

    // Automatically hooks Lobby 'Watch Video' & all 5 Tables 'FREE CHIPS' buttons without editing index.html
    private void injectSmartAdHooks() {
        String js = "(function() {" +
                "  if (window._unityHooked) return;" +
                "  window._unityHooked = true;" +
                "  window._pendingRewardBtn = null;" +
                "  window.onUnityAdRewardEarned = function() {" +
                "    if (window._pendingRewardBtn) {" +
                "      var btn = window._pendingRewardBtn;" +
                "      window._pendingRewardBtn = null;" +
                "      btn.dataset.unityBypass = '1';" +
                "      btn.click();" +
                "    }" +
                "  };" +
                "  document.addEventListener('click', function(e) {" +
                "    var target = e.target;" +
                "    while (target && target !== document.body) {" +
                "      if (target.dataset && target.dataset.unityBypass === '1') {" +
                "        delete target.dataset.unityBypass;" +
                "        return;" +
                "      }" +
                "      var txt = (target.innerText || target.textContent || '').toUpperCase();" +
                "      if (txt.includes('WATCH VIDEO') || txt.includes('FREE 6.00M') || txt.includes('FREE CHIPS')) {" +
                "        if (window.AndroidUnityAds) {" +
                "          e.stopImmediatePropagation();" +
                "          e.preventDefault();" +
                "          window._pendingRewardBtn = target;" +
                "          window.AndroidUnityAds.showRewardedAd();" +
                "          return;" +
                "        }" +
                "      }" +
                "      target = target.parentElement;" +
                "    }" +
                "  }, true);" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    @Override
    public void onInitializationComplete() {
        loadRewardedAd();
        loadInterstitialAd();
    }

    @Override
    public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
        // Retry silently if network recovers
    }

    private void loadRewardedAd() {
        UnityAds.load(REWARDED_PLACEMENT_ID, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                isRewardedReady = true;
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                isRewardedReady = false;
            }
        });
    }

    private void loadInterstitialAd() {
        UnityAds.load(INTERSTITIAL_PLACEMENT_ID, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                isInterstitialReady = true;
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                isInterstitialReady = false;
            }
        });
    }

    public class UnityAdsBridge {
        @JavascriptInterface
        public void showRewardedAd() {
            runOnUiThread(() -> {
                if (isRewardedReady) {
                    isRewardedReady = false;
                    UnityAds.show(MainActivity.this, REWARDED_PLACEMENT_ID, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
                        @Override
                        public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                            Toast.makeText(MainActivity.this, "Ad loading, please try again...", Toast.LENGTH_SHORT).show();
                            loadRewardedAd();
                        }

                        @Override
                        public void onUnityAdsShowStart(String placementId) {}

                        @Override
                        public void onUnityAdsShowClick(String placementId) {}

                        @Override
                        public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                webView.evaluateJavascript("if(window.onUnityAdRewardEarned){window.onUnityAdRewardEarned();}", null);
                            }
                            loadRewardedAd();
                        }
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Loading Video Ad... Click again in 3 seconds", Toast.LENGTH_SHORT).show();
                    loadRewardedAd();
                }
            });
        }

        @JavascriptInterface
        public void showInterstitialAd() {
            runOnUiThread(() -> {
                if (isInterstitialReady) {
                    isInterstitialReady = false;
                    UnityAds.show(MainActivity.this, INTERSTITIAL_PLACEMENT_ID, new UnityAdsShowOptions(), new IUnityAdsShowListener() {
                        @Override
                        public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                            loadInterstitialAd();
                        }
                        @Override
                        public void onUnityAdsShowStart(String placementId) {}
                        @Override
                        public void onUnityAdsShowClick(String placementId) {}
                        @Override
                        public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                            loadInterstitialAd();
                        }
                    });
                } else {
                    loadInterstitialAd();
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
