package me.xujichang.util.retrofit;

import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import me.xujichang.util.tool.LogTool;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * TODO  需重新封装
 * 对Retrofit做进一步封装，请求时 更方便
 *
 * @author xjc
 * Created by xujichang on 2017/4/13.
 */

public class RetrofitManager {

    public static final int TOKEN_IN_QUERY = 1;
    public static final int TOKEN_IN_HEADER = 2;
    public static final String URL_EXTEND = "extend_url";
    private String tokenKey;
    private static RetrofitManager ourInstance = null;
    private static Retrofit retrofit;

    private String token;
    private static String reactUrl;
    private Retrofit tempRetrofit;
    private static String BASE_URL;
    private int tokenPos;
    private HashMap<String, String> extendUrls;
    /**
     * Cookie值
     */
    private String cookies;
    /**
     * cookies对应的key
     */
    private String cookieKey;

    public static RetrofitManager getOurInstance() {
        if (null == ourInstance) {
            ourInstance = new RetrofitManager();
        }
        reactUrl = null;
        return ourInstance;
    }

    public static RetrofitManager getOurInstance(String url) {
        if (null == ourInstance) {
            ourInstance = new RetrofitManager();
        }
        reactUrl = url;
        return ourInstance;
    }

    public static RetrofitManager getOurInstance(OkHttpClient client) {
        if (null == ourInstance) {
            ourInstance = new RetrofitManager();
        }
        return ourInstance;
    }

    private Retrofit initRetrofit(String url) {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(new RequestCheckIntercept());
        builder.addInterceptor(new CookieIntercept());
        //添加日志打印
        builder.addInterceptor(loggingInterceptor);

        //连接超时 时间
        builder.connectTimeout(30, TimeUnit.SECONDS);
        builder.readTimeout(120, TimeUnit.SECONDS);
        builder.writeTimeout(90, TimeUnit.SECONDS);
        //重试
        builder.retryOnConnectionFailure(true);

        OkHttpClient client = builder.build();

        return new Retrofit
                .Builder()
                .baseUrl(url)
                //使用Gson转换
                .addConverterFactory(GsonConverterFactory.create())
                //使用RxJava返回格式
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(client)
                .build();
    }

    private RetrofitManager() {
        initRetrofit();
    }

    protected RetrofitManager(String vo) {
        this();
    }

    public void refreshToken(String newToken) {
        token = newToken;
    }

    public void refreshCookie(String cookie) {
        cookies = cookie;
    }

    /**
     * 初始化
     */
    private void initRetrofit() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addInterceptor(new RequestCheckIntercept());
        //添加日志打印
        builder.addInterceptor(loggingInterceptor);
        //连接超时 时间
        builder.connectTimeout(30, TimeUnit.SECONDS);
        builder.readTimeout(120, TimeUnit.SECONDS);
        builder.writeTimeout(90, TimeUnit.SECONDS);
        //重试
        builder.retryOnConnectionFailure(true);

        OkHttpClient client = builder.build();

        retrofit = new Retrofit
                .Builder()
                .baseUrl(BASE_URL)
                //使用Gson转换
                .addConverterFactory(GsonConverterFactory.create())
                //使用RxJava返回格式
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(client)
                .build();
        tempRetrofit = retrofit;
    }

    public RetrofitManager withNewClient(OkHttpClient client) {
        retrofit.newBuilder().client(client);
        return this;
    }

    /**
     * 对请求链接进行处理
     */
    private class RequestCheckIntercept implements Interceptor {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Request newRequest = dealRequest(request);
            return chain.proceed(newRequest);
        }
    }

    private Request dealRequest(Request request) {
        Request.Builder requestBuilder = request.newBuilder();
        HttpUrl.Builder urlBuilder = request.url().newBuilder();
        Headers.Builder headersBuilder = request.headers().newBuilder();
        headersBuilder.set("Connection", "close");
        //处理Token
        if (!TextUtils.isEmpty(token)) {
            //如果不存在Token
            if (tokenPos == TOKEN_IN_QUERY) {
                urlBuilder.addQueryParameter(tokenKey, token);
            } else if (tokenPos == TOKEN_IN_HEADER) {
                headersBuilder.add(tokenKey, token);
            }
        }
        //处理请求链接 主要是区别于baseUrl
        String extendUrlKey = headersBuilder.get(URL_EXTEND);
        if (!TextUtils.isEmpty(extendUrlKey)) {
            if (null != extendUrls && !extendUrls.isEmpty()) {
                if (!extendUrls.containsKey(extendUrlKey)) {
                    throw new RuntimeException(
                            "the key | " + extendUrlKey + " | is not exist in extends Url Map");
                }
                patchUrl(urlBuilder, extendUrls.get(extendUrlKey));
            } else {
                throw new RuntimeException(
                        "the extends Url Map is null or size = 0");
            }
        }
        return requestBuilder.headers(headersBuilder.build()).url(urlBuilder.build()).build();
    }

    /**
     * 用第二个Url替换第一Url的部分内容
     */
    private void patchUrl(HttpUrl.Builder builder, String extendUrl) {
        HttpUrl extend = HttpUrl.parse(extendUrl);
        if (null == extend) {
            return;
        }
        builder
                .scheme(extend.scheme())
                .host(extend.host())
                .port(extend.port());
        //替换掉"scheme://host:port"
        ArrayList<String> strings = new ArrayList<>(builder.build().pathSegments());
        try {
            for (int i = 0; i < strings.size(); i++) {
                builder.removePathSegment(0);
            }
            strings.addAll(0, extend.pathSegments());
            for (String string : strings) {
                builder.addPathSegment(string);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public <T> T createReq(Class<T> apiService) {
        if (!TextUtils.isEmpty(reactUrl)) {
            retrofit = initRetrofit(reactUrl);
        } else {
            retrofit = tempRetrofit;
        }
        return retrofit.create(apiService);
    }

    public static class Builder {
        /**
         * 连接超时时间
         */
        private int connectTimeOut = 30;
        /**
         * 读取超时时间
         */
        private int readTimeOut = 120;
        /**
         * 写数据超时时间
         */
        private int writeTimeOut = 90;
        private String cookieKey = "Cookie";
        private String cookie;
        private String baseUrl;
        private String token;
        private String tokenKey = "token";
        private int tokenPos = TOKEN_IN_QUERY;
        private HashMap<String, String> extendUrlMap = new HashMap<>();

        public Builder baseUrl(String url) {
            baseUrl = url;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder tokenKey(String tokenKey) {
            this.tokenKey = tokenKey;
            return this;
        }

        public Builder token(String token, String tokenKey) {
            token(token);
            tokenKey(tokenKey);
            return this;
        }

        public Builder token(int tokenPos, String token, String tokenKey) {
            token(token);
            tokenKey(tokenKey);
            tokenPos(tokenPos);
            return this;
        }

        public Builder tokenPos(int tokenPos) {
            this.tokenPos = tokenPos;
            return this;
        }

        public Builder cookie(String cookie) {
            this.cookie = cookie;
            return this;
        }

        public Builder cookie(String cookieKey, String cookie) {
            this.cookie = cookie;
            this.cookieKey = cookieKey;
            return this;
        }

        public Builder connectTimeOut(int time) {
            connectTimeOut = time;
            return this;
        }

        public Builder readTimeOut(int time) {
            readTimeOut = time;
            return this;
        }

        public Builder writeTimeOut(int time) {
            writeTimeOut = time;
            return this;
        }

        public Builder addExtendUrl(String tag, String url) {
            if (extendUrlMap.containsKey(tag)) {
                throw new RuntimeException("the key is already in Map");
            }
            extendUrlMap.put(tag, url);
            return this;
        }

        public Builder addExtendUrlMap(HashMap<String, String> hashMap) {
            extendUrlMap.clear();
            extendUrlMap.putAll(hashMap);
            return this;
        }

        public void build() {
            BASE_URL = baseUrl;
            getOurInstance().initWithBuilder(this);
        }
    }

    private void initWithBuilder(Builder builder) {
        token = builder.token;
        tokenKey = builder.tokenKey;
        tokenPos = builder.tokenPos;
        extendUrls = builder.extendUrlMap;
        cookieKey = builder.cookieKey;
        cookies = builder.cookie;
    }

    private class CookieIntercept implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request.Builder builder = chain.request().newBuilder();
            //添加cookie
            if (!TextUtils.isEmpty(cookies)) {
                builder.addHeader(cookieKey, cookies);
            }
            Response response = chain.proceed(builder.build());
            //取Cookie
            if (!response.headers("Set-Cookie").isEmpty()) {
                StringBuilder strBuilder = new StringBuilder();
                //取cookie
                for (String cookie : response.headers("Set-Cookie")) {
                    String[] value = cookie.split(";");
                    strBuilder.append(value[0]).append(";");
                }
                cookies = strBuilder.toString();
                LogTool.d("Get Cookie[" + cookies + "]");
            }
            return response;
        }
    }
}

