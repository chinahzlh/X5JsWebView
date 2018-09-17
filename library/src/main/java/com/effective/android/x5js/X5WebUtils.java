package com.effective.android.x5js;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.export.external.extension.interfaces.IX5WebViewExtension;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsListener;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;

import java.net.URL;
import java.util.HashMap;

/**
 * 提供的扩展
 * 1. hookKeyCode 一般可用于页面的返回键拦截
 * 2. destroyWebView 销毁 webview
 * Created by yummyLau on 18-09-17
 * Email: yummyl.lau@gmail.com
 * blog: yummylau.com
 */
public class X5WebUtils {

    private static final String TAG = "X5WebUtils";
    private static final String APP_CACHE_PATH = "";
    private static final String DATA_BASE_PATH = "";


    /**
     * 拦截页面返回键
     *
     * @param webView
     * @param keyCode
     * @return
     */
    public static boolean hookKeyCode(WebView webView, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return false;
    }

    /**
     * 销毁 webview
     *
     * @param webView
     */
    public static void destroyWebView(WebView webView) {
        if (webView != null) {
            ViewParent parent = webView.getParent();
            if (parent != null) {
                ((ViewGroup) parent).removeView(webView);
            }
            webView.stopLoading();
            // 退出时调用此方法，移除绑定的服务，否则某些特定系统会报错
            webView.getSettings().setJavaScriptEnabled(false);
            webView.clearCache(true);
            webView.freeMemory();
            webView.removeAllViews();
            webView.destroy();
        }
    }

    /**
     * 设置 webview
     *
     * @param webView
     */
    public static void setDefaultWebViewSetting(WebView webView) {
        if (webView == null) {
            return;
        }
        //初始化setting
        WebSettings settings = webView.getSettings();
        settings.setAllowFileAccess(true);
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setUseWideViewPort(true);
        settings.setLightTouchEnabled(true);
        settings.setSavePassword(true);

        /**
         * 混合网页,比如https链接中嵌入http页面
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        settings.setLoadWithOverviewMode(true);
        settings.setSaveFormData(false);
        settings.setGeolocationEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        IX5WebViewExtension webViewExtension = webView.getX5WebViewExtension();
        if (webViewExtension != null) {
            webViewExtension.setScrollBarFadingEnabled(false);
        }
        /**
         * 设置webview缓存
         */
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        /**
         * 存储一些简单的key/value数据，Session Storage（页面关闭就消失） 和 Local Storage（本地存储，永不过期）
         */
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setDatabasePath(APP_CACHE_PATH);


        /**
         * Application Caches 缓存目录， 能够缓存web浏览器中所有东西，从页面图片到脚本css等。大小通常为5m
         * webkie使用一个db文件保存appcache缓存的数据
         */
        settings.setAppCacheEnabled(false);
        settings.setAppCachePath(DATA_BASE_PATH);
//        settings.setAppCacheMaxSize();

        /**
         * WebView先不要自动加载图片，等页面finish后再发起图片加载
         */
        if (Build.VERSION.SDK_INT >= 19) {
            settings.setLoadsImagesAutomatically(true);
        } else {
            settings.setLoadsImagesAutomatically(false);
        }
    }

    /**
     * 多进程初始化方案
     * 在AndroidManifest.xml中增加内核首次加载时优化Service声明；    该Service仅在TBS内核首次Dex加载时触发并执行dex2oat任务，任务完成后自动结束。
     * <service
     * android:name="com.tencent.smtt.export.external.DexClassLoaderProviderService"
     * android:label="dexopt"
     * android:process=":dexopt" ></service>
     * <p>
     * 优先去共享手q微信的内核，如果没有则会下载，通过setTasListener监听下载状态
     *
     * @param context
     */
    public static void initX5Webkit(Context context) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        QbSdk.initTbsSettings(map);
        //监听下载进度
        QbSdk.setTbsListener(new TbsListener() {
            @Override
            public void onDownloadFinish(int i) {
                Log.d(TAG, "initX5Webkit - onDownloadFinish : " + i);
            }

            @Override
            public void onInstallFinish(int i) {
                Log.d(TAG, "initX5Webkit - onInstallFinish : " + i);
            }

            @Override
            public void onDownloadProgress(int i) {
                Log.d(TAG, "initX5Webkit - onDownloadProgress : " + i);
            }
        });
        //qbsdk与加载，需要在wifi条件下下载x5内核，耗时90秒。如果打开webview是没有x5内核则默认使用系统内核
        QbSdk.initX5Environment(context, new QbSdk.PreInitCallback() {
            @Override
            public void onCoreInitFinished() {
                Log.d(TAG, "initX5Webkit - onCoreInitFinished");
            }

            @Override
            public void onViewInitFinished(boolean b) {
                Log.d(TAG, "initX5Webkit - onViewInitFinished : " + b);
            }
        });
    }

    /**
     * 1、WebView:postUrl()前检测url的合法性
     * 2、Js调用Native方法前检测当前界面url的合法性
     *
     * @param url
     * @return
     */
    public static boolean isTrustUrl(String url) {
        if (!TextUtils.isEmpty(url)) {
            try {
                URL realUrl = new URL(url);
                //可自由扩展
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public static boolean isTrustUrl(X5JsWebView webView) {
        String url = webView != null ? webView.getUrl() : null;
        return isTrustUrl(url);
    }
}
