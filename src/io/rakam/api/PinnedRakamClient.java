package io.rakam.api;

import android.content.Context;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import okhttp3.OkHttpClient;
import okio.Buffer;
import okio.ByteString;

/**
 * <h1>PinnedRakamClient</h1>
 * This is a version of the RakamClient that supports SSL pinning for encrypted requests.
 * Please contact <a href="mailto:support@rakam.io">Rakam Support</a> before you ship any
 * products with SSL pinning enabled so that we are aware and can provide documentation
 * and implementation help.
 */
public class PinnedRakamClient extends RakamClient {

    /**
     * The class identifier tag used in logging. TAG = {@code "PinnedRakamClient";}
     */
    public static final String TAG = "PinnedRakamClient";
    private static final RakamLog logger = RakamLog.getLogger();

    /**
     * Pinned certificate chain for api.rakam.io.
     */
    protected static final SSLContextBuilder SSL_CONTEXT_API_RAKAM_IO = new SSLContextBuilder()
            // 3 s:/C=US/ST=Arizona/L=Scottsdale/O=Starfield Technologies, Inc./CN=Starfield Services Root Certificate Authority - G2
            // i:/C=US/O=Starfield Technologies, Inc./OU=Starfield Class 2 Certification Authority
            .addCertificate(""
                    + "MIIEdTCCA12gAwIBAgIJAKcOSkw0grd/MA0GCSqGSIb3DQEBCwUAMGgxCzAJBgNV" +
                    "BAYTAlVTMSUwIwYDVQQKExxTdGFyZmllbGQgVGVjaG5vbG9naWVzLCBJbmMuMTIw" +
                    "MAYDVQQLEylTdGFyZmllbGQgQ2xhc3MgMiBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0" +
                    "eTAeFw0wOTA5MDIwMDAwMDBaFw0zNDA2MjgxNzM5MTZaMIGYMQswCQYDVQQGEwJV" +
                    "UzEQMA4GA1UECBMHQXJpem9uYTETMBEGA1UEBxMKU2NvdHRzZGFsZTElMCMGA1UE" +
                    "ChMcU3RhcmZpZWxkIFRlY2hub2xvZ2llcywgSW5jLjE7MDkGA1UEAxMyU3RhcmZp" +
                    "ZWxkIFNlcnZpY2VzIFJvb3QgQ2VydGlmaWNhdGUgQXV0aG9yaXR5IC0gRzIwggEi" +
                    "MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDVDDrEKvlO4vW+GZdfjohTsR8/" +
                    "y8+fIBNtKTrID30892t2OGPZNmCom15cAICyL1l/9of5JUOG52kbUpqQ4XHj2C0N" +
                    "Tm/2yEnZtvMaVq4rtnQU68/7JuMauh2WLmo7WJSJR1b/JaCTcFOD2oR0FMNnngRo" +
                    "Ot+OQFodSk7PQ5E751bWAHDLUu57fa4657wx+UX2wmDPE1kCK4DMNEffud6QZW0C" +
                    "zyyRpqbn3oUYSXxmTqM6bam17jQuug0DuDPfR+uxa40l2ZvOgdFFRjKWcIfeAg5J" +
                    "Q4W2bHO7ZOphQazJ1FTfhy/HIrImzJ9ZVGif/L4qL8RVHHVAYBeFAlU5i38FAgMB" +
                    "AAGjgfAwge0wDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAYYwHQYDVR0O" +
                    "BBYEFJxfAN+qAdcwKziIorhtSpzyEZGDMB8GA1UdIwQYMBaAFL9ft9HO3R+G9FtV" +
                    "rNzXEMIOqYjnME8GCCsGAQUFBwEBBEMwQTAcBggrBgEFBQcwAYYQaHR0cDovL28u" +
                    "c3MyLnVzLzAhBggrBgEFBQcwAoYVaHR0cDovL3guc3MyLnVzL3guY2VyMCYGA1Ud" +
                    "HwQfMB0wG6AZoBeGFWh0dHA6Ly9zLnNzMi51cy9yLmNybDARBgNVHSAECjAIMAYG" +
                    "BFUdIAAwDQYJKoZIhvcNAQELBQADggEBACMd44pXyn3pF3lM8R5V/cxTbj5HD9/G" +
                    "VfKyBDbtgB9TxF00KGu+x1X8Z+rLP3+QsjPNG1gQggL4+C/1E2DUBc7xgQjB3ad1" +
                    "l08YuW3e95ORCLp+QCztweq7dp4zBncdDQh/U90bZKuCJ/Fp1U1ervShw3WnWEQt" +
                    "8jxwmKy6abaVd38PMV4s/KCHOkdp8Hlf9BRUpJVeEXgSYCfOn8J3/yNTd126/+pZ" +
                    "59vPr5KW7ySaNRB6nJHGDn2Z9j8Z3/VyVOEVqQdZe4O/Ui5GjLIAZHYcSNPYeehu" +
                    "VsyuLAOQ1xk4meTKCRlb/weWsKh/NEnfVqn3sF/tM+2MR7cwA130A4w=")
            // 2 s:/C=US/O=Amazon/CN=Amazon Root CA 1
            // i:/C=US/ST=Arizona/L=Scottsdale/O=Starfield Technologies, Inc./CN=Starfield Services Root Certificate Authority - G2
            .addCertificate(""
                    + "MIIEkjCCA3qgAwIBAgITBn+USionzfP6wq4rAfkI7rnExjANBgkqhkiG9w0BAQsF" +
                    "ADCBmDELMAkGA1UEBhMCVVMxEDAOBgNVBAgTB0FyaXpvbmExEzARBgNVBAcTClNj" +
                    "b3R0c2RhbGUxJTAjBgNVBAoTHFN0YXJmaWVsZCBUZWNobm9sb2dpZXMsIEluYy4x" +
                    "OzA5BgNVBAMTMlN0YXJmaWVsZCBTZXJ2aWNlcyBSb290IENlcnRpZmljYXRlIEF1" +
                    "dGhvcml0eSAtIEcyMB4XDTE1MDUyNTEyMDAwMFoXDTM3MTIzMTAxMDAwMFowOTEL" +
                    "MAkGA1UEBhMCVVMxDzANBgNVBAoTBkFtYXpvbjEZMBcGA1UEAxMQQW1hem9uIFJv" +
                    "b3QgQ0EgMTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALJ4gHHKeNXj" +
                    "ca9HgFB0fW7Y14h29Jlo91ghYPl0hAEvrAIthtOgQ3pOsqTQNroBvo3bSMgHFzZM" +
                    "9O6II8c+6zf1tRn4SWiw3te5djgdYZ6k/oI2peVKVuRF4fn9tBb6dNqcmzU5L/qw" +
                    "IFAGbHrQgLKm+a/sRxmPUDgH3KKHOVj4utWp+UhnMJbulHheb4mjUcAwhmahRWa6" +
                    "VOujw5H5SNz/0egwLX0tdHA114gk957EWW67c4cX8jJGKLhD+rcdqsq08p8kDi1L" +
                    "93FcXmn/6pUCyziKrlA4b9v7LWIbxcceVOF34GfID5yHI9Y/QCB/IIDEgEw+OyQm" +
                    "jgSubJrIqg0CAwEAAaOCATEwggEtMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/" +
                    "BAQDAgGGMB0GA1UdDgQWBBSEGMyFNOy8DJSULghZnMeyEE4KCDAfBgNVHSMEGDAW" +
                    "gBScXwDfqgHXMCs4iKK4bUqc8hGRgzB4BggrBgEFBQcBAQRsMGowLgYIKwYBBQUH" +
                    "MAGGImh0dHA6Ly9vY3NwLnJvb3RnMi5hbWF6b250cnVzdC5jb20wOAYIKwYBBQUH" +
                    "MAKGLGh0dHA6Ly9jcnQucm9vdGcyLmFtYXpvbnRydXN0LmNvbS9yb290ZzIuY2Vy" +
                    "MD0GA1UdHwQ2MDQwMqAwoC6GLGh0dHA6Ly9jcmwucm9vdGcyLmFtYXpvbnRydXN0" +
                    "LmNvbS9yb290ZzIuY3JsMBEGA1UdIAQKMAgwBgYEVR0gADANBgkqhkiG9w0BAQsF" +
                    "AAOCAQEAYjdCXLwQtT6LLOkMm2xF4gcAevnFWAu5CIw+7bMlPLVvUOTNNWqnkzSW" +
                    "MiGpSESrnO09tKpzbeR/FoCJbM8oAxiDR3mjEH4wW6w7sGDgd9QIpuEdfF7Au/ma" +
                    "eyKdpwAJfqxGF4PcnCZXmTA5YpaP7dreqsXMGz7KQ2hsVxa81Q4gLv7/wmpdLqBK" +
                    "bRRYh5TmOTFffHPLkIhqhBGWJ6bt2YFGpn6jcgAKUj6DiAdjd4lpFw85hdKrCEVN" +
                    "0FE6/V1dN2RMfjCyVSRCnTawXZwXgWHxyvkQAiSr6w10kY17RSlQOYiypok1JR4U" +
                    "akcjMS9cmvqtmg5iUaQqqcT5NJ0hGA==");

    /**
     * SSl context builder, used to generate the SSL context.
     */
    protected static class SSLContextBuilder {
        private final List<String> certificateBase64s = new ArrayList<String>();

        /**
         * Add certificate ssl context builder.
         *
         * @param certificateBase64 the certificate base 64
         * @return the ssl context builder
         */
        public SSLContextBuilder addCertificate(String certificateBase64) {
            certificateBase64s.add(certificateBase64);
            return this;
        }

        /**
         * Build ssl context.
         *
         * @return the ssl context
         */
        public SSLContext build() {
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null); // Use a null input stream + password to create an empty key store.

                // Decode the certificates and add 'em to the key store.
                int nextName = 1;
                for (String certificateBase64 : certificateBase64s) {
                    Buffer certificateBuffer = new Buffer().write(ByteString.decodeBase64(certificateBase64));
                    X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(
                            certificateBuffer.inputStream());
                    keyStore.setCertificateEntry(Integer.toString(nextName++), certificate);
                }

                // Create an SSL context that uses these certificates as its trust store.
                trustManagerFactory.init(keyStore);
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
                return sslContext;
            } catch (GeneralSecurityException e) {
                logger.e(TAG, e.getMessage(), e);
            } catch (IOException e) {
                logger.e(TAG, e.getMessage(), e);
            }
            return null;
        }
    }

    /**
     * The default instance.
     */
    protected static PinnedRakamClient instance = new PinnedRakamClient();

    /**
     * Gets the default instance. Call SDK method on the default instance.
     *
     * @return the default instance
     */
    public static PinnedRakamClient getInstance() {
        return instance;
    }

    /**
     * The SSl socket factory.
     */
    protected SSLSocketFactory sslSocketFactory;

    /**
     * Instantiates a new Pinned rakam client.
     */
    public PinnedRakamClient() {
        super();
    }

    /**
     * The Initialized ssl socket factory.
     */
    protected boolean initializedSSLSocketFactory = false;

    @Override
    public synchronized RakamClient initialize(Context context, String apiKey, String userId) {
        super.initialize(context, apiKey, userId);
        if (!initializedSSLSocketFactory) {
            SSLSocketFactory factory = getPinnedCertSslSocketFactory();
            if (factory != null) {
                this.httpClient = new OkHttpClient.Builder().sslSocketFactory(factory).build();
            } else {
                logger.e(TAG, "Unable to pin SSL as requested. Will send data without SSL pinning.");
            }
            initializedSSLSocketFactory = true;
        }
        return this;
    }

    /**
     * Gets pinned cert ssl socket factory.
     *
     * @return the pinned cert ssl socket factory
     */
    protected SSLSocketFactory getPinnedCertSslSocketFactory() {
        return sslSocketFactory != null ? sslSocketFactory : getPinnedCertSslSocketFactory(SSL_CONTEXT_API_RAKAM_IO);
    }

    /**
     * Sets pinned cert ssl certicate
     *
     * @return the pinned cert ssl socket factory
     */
    protected void setPinnedCertSslSocketFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    /**
     * Gets pinned cert ssl socket factory.
     *
     * @param context the context
     * @return the pinned cert ssl socket factory
     */
    protected SSLSocketFactory getPinnedCertSslSocketFactory(SSLContextBuilder context) {
        if (context == null) {
            return null;
        }
        if (sslSocketFactory == null) {
            try {
                sslSocketFactory = context.build().getSocketFactory();
                logger.i(TAG, "Pinning SSL session using Comodo CA Cert");
            } catch (Exception e) {
                logger.e(TAG, e.getMessage(), e);
            }
        }
        return sslSocketFactory;
    }
}
