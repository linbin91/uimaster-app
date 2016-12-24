package org.shaolin.uimaster.app.context;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.webkit.WebView;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.MySSLSocketFactory;
import com.loopj.android.http.PersistentCookieStore;


import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.params.HttpProtocolParams;
import cz.msebera.android.httpclient.protocol.HttpContext;
import org.kymjs.kjframe.KJBitmap;
import org.kymjs.kjframe.bitmap.BitmapConfig;
import org.kymjs.kjframe.http.HttpConfig;
import org.kymjs.kjframe.utils.KJLoger;
import org.shaolin.uimaster.app.BuildConfig;
import org.shaolin.uimaster.app.R;
import org.shaolin.uimaster.app.api.HttpClientService;
import org.shaolin.uimaster.app.base.BaseApplication;
import org.shaolin.uimaster.app.bean.Constants;
import org.shaolin.uimaster.app.bean.User;
import org.shaolin.uimaster.app.cache.DataCleanManager;
import org.shaolin.uimaster.app.ui.MainActivity;
import org.shaolin.uimaster.app.ui.NavigationDrawerFragment;
import org.shaolin.uimaster.app.util.CyptoUtils;
import org.shaolin.uimaster.app.util.MethodsCompat;
import org.shaolin.uimaster.app.util.StringUtils;
import org.shaolin.uimaster.app.util.TLog;
import org.shaolin.uimaster.app.util.UIHelper;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import cz.msebera.android.httpclient.conn.ssl.SSLSocketFactory;

import static org.shaolin.uimaster.app.context.AppConfig.KEY_FRITST_START;
import static org.shaolin.uimaster.app.context.AppConfig.KEY_LOAD_IMAGE;
import static org.shaolin.uimaster.app.context.AppConfig.KEY_NIGHT_MODE_SWITCH;

/**
 *
 */
public class AppContext extends BaseApplication {

    public static final int PAGE_SIZE = 20;// 默认分页大小

    private static AppContext instance;

    private int loginUid;

    private boolean login;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Thread.setDefaultUncaughtExceptionHandler(AppException
                .getAppExceptionHandler(this));
        try {
            init();
            initLogin();
            UIHelper.sendBroadcastForNotice(this);
        } catch (Exception e) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private void init() throws Exception {
        // 初始化网络请求
        AsyncHttpClient client = new AsyncHttpClient();
        //client.setThreadPool(ExecutorService);
        client.setEnableRedirects(true);
        client.setSSLSocketFactory(getSocketFactory());
        PersistentCookieStore myCookieStore = new PersistentCookieStore(this);
        client.setCookieStore(myCookieStore);//keep session.
        HttpClientService.setHttpClient(client);
        HttpClientService.setCookie(HttpClientService.getCookie(this));

        // Log控制器
        KJLoger.openDebutLog(true);
        TLog.DEBUG = BuildConfig.DEBUG;

        // Bitmap缓存地址
        BitmapConfig.CACHEPATH = "uimaster/imagecache";
    }

    private SSLSocketFactory getSocketFactory() throws Exception {
        // We initialize a default Keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        // We load the KeyStore
        keyStore.load(null, null);
        // We initialize a new SSLSocketFacrory
        MySSLSocketFactory socketFactory = new MySSLSocketFactory(keyStore);
        // We set that all host names are allowed in the socket factory
        socketFactory.setHostnameVerifier(MySSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        return socketFactory;
    }

    private void initLogin() {
        User user = getLoginUser();
        if (null != user && user.getId() > 0) {
            login = true;
            loginUid = user.getId();
        } else {
            this.cleanLoginInfo();
        }
    }

    /**
     * 获得当前app运行的AppContext
     * 
     * @return
     */
    public static AppContext getInstance() {
        return instance;
    }

    public boolean containsProperty(String key) {
        Properties props = getProperties();
        return props.containsKey(key);
    }

    public void setProperties(Properties ps) {
        AppConfig.getAppConfig(this).set(ps);
    }

    public Properties getProperties() {
        return AppConfig.getAppConfig(this).get();
    }

    public void setProperty(String key, String value) {
        AppConfig.getAppConfig(this).set(key, value);
    }

    /**
     * 获取cookie时传AppConfig.CONF_COOKIE
     * 
     * @param key
     * @return
     */
    public String getProperty(String key) {
        String res = AppConfig.getAppConfig(this).get(key);
        return res;
    }

    public void removeProperty(String... key) {
        AppConfig.getAppConfig(this).remove(key);
    }

    /**
     * 获取App唯一标识
     * 
     * @return
     */
    public String getAppId() {
        String uniqueID = getProperty(AppConfig.CONF_APP_UNIQUEID);
        if (StringUtils.isEmpty(uniqueID)) {
            uniqueID = UUID.randomUUID().toString();
            setProperty(AppConfig.CONF_APP_UNIQUEID, uniqueID);
        }
        return uniqueID;
    }

    /**
     * 获取App安装包信息
     * 
     * @return
     */
    public PackageInfo getPackageInfo() {
        PackageInfo info = null;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace(System.err);
        }
        if (info == null)
            info = new PackageInfo();
        return info;
    }

    /**
     * 保存登录信息
     * 
     * @param user 用户信息
     */
    @SuppressWarnings("serial")
    public void saveUserInfo(final User user) {
        this.loginUid = user.getId();
        this.login = true;
        setProperties(new Properties() {
            {
                setProperty("user.uid", String.valueOf(user.getId()));
                setProperty("user.name", user.getName());
                //setProperty("user.face", user.getPortrait());// 用户头像-文件名
                setProperty("user.account", user.getAccount());
                setProperty("user.pwd", CyptoUtils.encode("uimasterApp", user.getPwd()));
                //setProperty("user.location", user.getLocation());
                setProperty("user.isRememberMe", String.valueOf(user.isRememberMe()));// 是否记住我的信息
            }
        });
    }

    /**
     * 更新用户信息
     * 
     * @param user
     */
    @SuppressWarnings("serial")
    public void updateUserInfo(final User user) {
        setProperties(new Properties() {
            {
                setProperty("user.name", user.getName());
                setProperty("user.face", user.getPortrait());// 用户头像-文件名
                //setProperty("user.followers",
                  //      String.valueOf(user.getFollowers()));
                //setProperty("user.fans", String.valueOf(user.getFans()));
                //setProperty("user.score", String.valueOf(user.getScore()));
                //setProperty("user.favoritecount",
                 //       String.valueOf(user.getFavoritecount()));
                setProperty("user.gender", String.valueOf(user.getGender()));
            }
        });
    }

    /**
     * 获得登录用户的信息
     * 
     * @return
     */
    public User getLoginUser() {
        User user = new User();
        user.setId(StringUtils.toInt(getProperty("user.uid"), 0));
        user.setName(getProperty("user.name"));
        user.setPortrait(getProperty("user.face"));
        user.setAccount(getProperty("user.account"));
        user.setLocation(getProperty("user.location"));
        user.setFollowers(StringUtils.toInt(getProperty("user.followers"), 0));
        user.setFans(StringUtils.toInt(getProperty("user.fans"), 0));
        user.setScore(StringUtils.toInt(getProperty("user.score"), 0));
        user.setFavoritecount(StringUtils.toInt(
                getProperty("user.favoritecount"), 0));
        user.setRememberMe(StringUtils.toBool(getProperty("user.isRememberMe")));
        user.setGender(getProperty("user.gender"));
        return user;
    }

    /**
     * 清除登录信息
     */
    public void cleanLoginInfo() {
        this.loginUid = 0;
        this.login = false;
        removeProperty("user.uid", "user.name", "user.face", "user.location",
                "user.followers", "user.fans", "user.score",
                "user.isRememberMe", "user.gender", "user.favoritecount");
    }

    public int getLoginUid() {
        return loginUid;
    }

    public boolean isLogin() {
        return login;
    }

    /**
     * 用户注销
     */
    public void Logout() {
        cleanLoginInfo();
        HttpClientService.cleanCookie();
        this.cleanCookie();
        this.login = false;
        this.loginUid = 0;

        Intent intent = new Intent(Constants.INTENT_ACTION_LOGOUT);
        sendBroadcast(intent);
    }

    public void keepUserSession() {
        AsyncHttpClient client = HttpClientService.getHttpClient();
        HttpContext httpContext = client.getHttpContext();
        CookieStore cookies = (CookieStore) httpContext .getAttribute(ClientContext.COOKIE_STORE);
        if (cookies != null) {
            int i = 0;
            int duplicatedSid = -1;
            int duplicatedSidCount = 0;
            String jsessionId = "";
            String tmpcookies = "";
            List<Cookie> cookies1 = cookies.getCookies();
            for (Cookie c : cookies1) {
                TLog.log(AppContext.class.getName(),
                        "cookie:" + c.getName() + " " + c.getValue());
                //TODO: the issue of session is unable to be kept!!!
//                if ("JSESSIONID".equals(c.getName())) {
//                    if (duplicatedSidCount == 0) {
//                        duplicatedSid = i;//find the first.
//                    }
//                    duplicatedSidCount++;
//                    jsessionId = c.getName() + "=" + c.getValue();//get the last one;
//                } else {
                    tmpcookies += (c.getName() + "=" + c.getValue()) + ";";
//                }
//                i++;
            }
//            if (duplicatedSidCount > 1) {
//                cookies.getCookies().remove(duplicatedSid);
//            }
//            tmpcookies = jsessionId + ";" + tmpcookies;
            TLog.log(AppContext.class.getName(), "cookies:" + tmpcookies);
            AppContext.getInstance().setProperty(AppConfig.CONF_COOKIE, tmpcookies);
            HttpClientService.setCookie(tmpcookies);
            HttpConfig.sCookie = tmpcookies;
        }
    }

    /**
     * 清除保存的缓存
     */
    public void cleanCookie() {
        removeProperty(AppConfig.CONF_COOKIE);
    }

    /**
     * 清除app缓存
     */
    public void clearAppCache() {
        DataCleanManager.cleanDatabases(this);
        // 清除数据缓存
        DataCleanManager.cleanInternalCache(this);
        // 2.2版本才有将应用缓存转移到sd卡的功能
        if (isMethodsCompat(android.os.Build.VERSION_CODES.FROYO)) {
            DataCleanManager.cleanCustomCache(MethodsCompat
                    .getExternalCacheDir(this));
        }
        // 清除编辑器保存的临时内容
        Properties props = getProperties();
        for (Object key : props.keySet()) {
            String _key = key.toString();
            if (_key.startsWith("temp"))
                removeProperty(_key);
        }
        new KJBitmap().cleanCache();
    }

    public static void setLoadImage(boolean flag) {
        set(KEY_LOAD_IMAGE, flag);
    }

    /**
     * 判断当前版本是否兼容目标版本的方法
     * 
     * @param VersionCode
     * @return
     */
    public static boolean isMethodsCompat(int VersionCode) {
        int currentVersion = android.os.Build.VERSION.SDK_INT;
        return currentVersion >= VersionCode;
    }

    public static boolean isFristStart() {
        return getPreferences().getBoolean(KEY_FRITST_START, true);
    }

    public static void setFristStart(boolean frist) {
        set(KEY_FRITST_START, frist);
    }

    //夜间模式
    public static boolean getNightModeSwitch() {
        return getPreferences().getBoolean(KEY_NIGHT_MODE_SWITCH, false);
    }

    // 设置夜间模式
    public static void setNightModeSwitch(boolean on) {
        set(KEY_NIGHT_MODE_SWITCH, on);
    }

    private NavigationDrawerFragment navigator;
    public void setNavtigation(NavigationDrawerFragment n) {
        navigator = n;
    }

    public NavigationDrawerFragment getNavigator() {
        return navigator;
    }
}
