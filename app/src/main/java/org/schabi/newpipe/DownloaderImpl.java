package org.schabi.newpipe;

import android.os.Build;
import android.text.TextUtils;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.util.TLSSocketFactoryCompat;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import static org.schabi.newpipe.MainActivity.DEBUG;

public class DownloaderImpl extends Downloader {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:68.0) Gecko/20100101 Firefox/68.0";

    private static DownloaderImpl instance;
    private String mCookies;
    private OkHttpClient client;

    private DownloaderImpl(OkHttpClient.Builder builder) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            enableModernTLS(builder);
        }
        this.client = builder
                .readTimeout(30, TimeUnit.SECONDS)
                //.cache(new Cache(new File(context.getExternalCacheDir(), "okhttp"), 16 * 1024 * 1024))
                .build();
    }

    /**
     * It's recommended to call exactly once in the entire lifetime of the application.
     *
     * @param builder if null, default builder will be used
     */
    public static DownloaderImpl init(@Nullable OkHttpClient.Builder builder) {
        return instance = new DownloaderImpl(builder != null ? builder : new OkHttpClient.Builder());
    }

    public static DownloaderImpl getInstance() {
        return instance;
    }

    public String getCookies() {
        return mCookies;
    }

    public void setCookies(String cookies) {
        mCookies = cookies;
    }

    /**
     * Get the size of the content that the url is pointing by firing a HEAD request.
     *
     * @param url an url pointing to the content
     * @return the size of the content, in bytes
     */
    public long getContentLength(String url) throws IOException {
        try {
            final Response response = head(url);
            return Long.parseLong(response.getHeader("Content-Length"));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid content length", e);
        } catch (ReCaptchaException e) {
            throw new IOException(e);
        }
    }

    public InputStream stream(String siteUrl) throws IOException {
        try {
            final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                    .method("GET", null).url(siteUrl)
                    .addHeader("User-Agent", USER_AGENT);

            if (!TextUtils.isEmpty(mCookies)) {
                requestBuilder.addHeader("Cookie", mCookies);
            }

            final okhttp3.Request request = requestBuilder.build();
            final okhttp3.Response response = client.newCall(request).execute();
            final ResponseBody body = response.body();

            if (response.code() == 429) {
                throw new ReCaptchaException("reCaptcha Challenge requested", siteUrl);
            }

            if (body == null) {
                response.close();
                return null;
            }

            return body.byteStream();
        } catch (ReCaptchaException e) {
            throw new IOException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        final String httpMethod = request.httpMethod();
        final String url = request.url();
        final Map<String, List<String>> headers = request.headers();
        final byte[] dataToSend = request.dataToSend();

        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(null, dataToSend);
        }

        final okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody).url(url)
                .addHeader("User-Agent", USER_AGENT);

        if (!TextUtils.isEmpty(mCookies)) {
            requestBuilder.addHeader("Cookie", mCookies);
        }

        for (Map.Entry<String, List<String>> pair : headers.entrySet()) {
            final String headerName = pair.getKey();
            final List<String> headerValueList = pair.getValue();

            if (headerValueList.size() > 1) {
                requestBuilder.removeHeader(headerName);
                for (String headerValue : headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue);
                }
            } else if (headerValueList.size() == 1) {
                requestBuilder.header(headerName, headerValueList.get(0));
            }

        }

        final okhttp3.Response response = client.newCall(requestBuilder.build()).execute();

        if (response.code() == 429) {
            response.close();

            throw new ReCaptchaException("reCaptcha Challenge requested", url);
        }

        final ResponseBody body = response.body();
        String responseBodyToReturn = null;

        if (body != null) {
            responseBodyToReturn = body.string();
        }

        return new Response(response.code(), response.message(), response.headers().toMultimap(), responseBodyToReturn);
    }

    /**
     * Enable TLS 1.2 and 1.1 on Android Kitkat. This function is mostly taken from the documentation of
     * OkHttpClient.Builder.sslSocketFactory(_,_)
     * <p>
     * If there is an error, the function will safely fall back to doing nothing and printing the error to the console.
     *
     * @param builder The HTTPClient Builder on which TLS is enabled on (will be modified in-place)
     */
    private static void enableModernTLS(OkHttpClient.Builder builder) {
        try {
            // get the default TrustManager
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:"
                        + Arrays.toString(trustManagers));
            }
            X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

            // insert our own TLSSocketFactory
            SSLSocketFactory sslSocketFactory = TLSSocketFactoryCompat.getInstance();

            builder.sslSocketFactory(sslSocketFactory, trustManager);

            // This will try to enable all modern CipherSuites(+2 more) that are supported on the device.
            // Necessary because some servers (e.g. Framatube.org) don't support the old cipher suites.
            // https://github.com/square/okhttp/issues/4053#issuecomment-402579554
            List<CipherSuite> cipherSuites = new ArrayList<>();
            cipherSuites.addAll(ConnectionSpec.MODERN_TLS.cipherSuites());
            cipherSuites.add(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA);
            cipherSuites.add(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA);
            ConnectionSpec legacyTLS = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .cipherSuites(cipherSuites.toArray(new CipherSuite[0]))
                    .build();

            builder.connectionSpecs(Arrays.asList(legacyTLS, ConnectionSpec.CLEARTEXT));
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            if (DEBUG) e.printStackTrace();
        }
    }
}
