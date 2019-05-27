package ru.dz.pay.system;

import com.google.gson.GsonBuilder;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

public class Client {

    private static final Logger log = LogManager.getLogger(Client.class);

    private static final Configuration configuration = new Configuration();
    private static final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    private final CloseableHttpClient client;

    private boolean initComplete = false;
    private static int threadCount = 1;
    private static int messageCount = 6;
    private static Thread[] threads = new Thread[threadCount];

    //private HttpResp response = null;

    private final static AtomicInteger count = new AtomicInteger(0);
    private final int threadNumber = count.incrementAndGet();
    private final String name = "T" + threadNumber + "_";

    public Client() {
        cm.setMaxTotal(100);
        client = HttpClients.custom()
                .disableAutomaticRetries()
                .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy())
                .build();
    }

    public synchronized void init() {
        log.info("Thread #" + threadNumber + " Initiate SyncLoader! State: " + initComplete);
        if (!initComplete) {
            configuration.readConfig();

            if (configuration.getThreads() > 0) threadCount = configuration.getThreads();
            if (configuration.getCount() > 0) messageCount = configuration.getCount();

            initComplete = true;
        }
        log.info("Thread #" + threadNumber + " Initiate Client complete!");
    }

    public HttpPost createPost(String url, String body) {
        HttpPost post = new HttpPost(configuration.getUrl() + "/" + url);
        body += "\r\n";

        RequestConfig config = RequestConfig.custom().setConnectTimeout(configuration.connectTimeout)
                .setSocketTimeout(configuration.socketTimeout).build();
        post.setConfig(config);
        post.addHeader("Accept", "application/json, application/*+json");
        post.addHeader("Content-Type", "application/json");
        post.addHeader("Accept-Charset", "utf-8");

        HttpEntity entity = new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8));
        post.setEntity(entity);

        return post;
    }

    public HttpGet createGet(String url) {
        HttpGet get = new HttpGet(configuration.getUrl() + "/" + url);
        RequestConfig config = RequestConfig.custom().setConnectTimeout(configuration.connectTimeout)
                .setSocketTimeout(configuration.socketTimeout).build();
        get.setConfig(config);
        get.addHeader("Accept", "application/json, application/*+json");
        get.addHeader("Content-Type", "application/json");
        get.addHeader("Accept-Charset", "utf-8");

        return get;
    }

    public String printResponse(HttpResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(response.getStatusLine().toString()).append("\n");
        for (Header header : response.getAllHeaders()) {
            sb.append(header.toString()).append("\n");
        }
        return sb.toString();
    }

    public HttpResp getResponse(HttpUriRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        HttpResp resp;
        long connectTime = System.currentTimeMillis();

        try (CloseableHttpResponse httpResp = client.execute(request)) {

            int code = httpResp.getStatusLine().getStatusCode();

            log.info(format("Thread #" + threadNumber + " Response for %s %s -> Code: %s", request.getMethod(), request.getURI().getPath(), code));

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpResp.getEntity().getContent()));

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            resp = new HttpResp(httpResp, sb.toString(), code);
            resp.setConnectTime(connectTime);

            resp.setOk(httpResp.getStatusLine().getStatusCode() == 200);

            log.debug(printResponse(httpResp) + sb.toString());

        }

        return resp;
    }

    public HttpResp send(MessageType type, String url, TransactionRequest request) throws IOException {
        HttpResp response = null;
        switch (type) {
            case GET:
                HttpGet get = createGet(url);
                response = getResponse(get);
                break;
            case POST:
                HttpPost post = createPost(url, new GsonBuilder().setPrettyPrinting().create().toJson(request));
                response = getResponse(post);
        }
        return response;
    }

}
