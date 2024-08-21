package arsenic.main;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

public class Auth {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private boolean authorised = false;
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY = "BqW5keg2/o3Tn8HWBGNc5drtnPdmKSWgW0glH1LYK1Q=";
    private HttpServer server;

    public void init() {
        if (allowed()) {
            setAuthorised();
            request(new HttpGet("http://140.238.204.221:5001/launch"));
            return;
        }
        startServer();
        openWebsite();
    }

    private void startServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(4567), 0);
            server.createContext("/", new AuthHandler());
            server.setExecutor(executor);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private <T extends HttpRequestBase> String request(T t) {
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            String ret = EntityUtils.toString(client.execute(t).getEntity());
            client.close();
            return ret;
        } catch (Exception ignored) {}
        return "";
    }

    private boolean allowed() {
        JsonObject jsonResponse = new JsonParser().parse(request(new HttpGet("http://140.238.204.221:5001/get"))).getAsJsonObject();
        System.out.println(jsonResponse);
        return decrypt(jsonResponse.get("keyToken").getAsString(), jsonResponse.get("iv").getAsString()).equals("Allowed");
    }

    public static String decrypt(String encrypted, String encodedIV) {
        try {
            byte[] ivBytes = Base64.getDecoder().decode(encodedIV);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            byte[] decodedKey = Base64.getDecoder().decode(KEY);
            SecretKeySpec skeySpec = new SecretKeySpec(decodedKey, "AES");

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));

            return new String(original);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "";
    }

    private void openWebsite() {
        System.out.println("Opening website");
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                URI url = new URI("https://discord.com/oauth2/authorize?client_id=1271352455984316437&response_type=code&redirect_uri=http%3A%2F%2F140.238.204.221%3A5001%2Fcallback&scope=guilds+identify+guilds.members.read");
                desktop.browse(url);
            } else {
                System.out.println("Desktop is not supported on this platform.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isAuthorised() {
        return authorised;
    }

    public void setAuthorised() {
        if (authorised)
            return;
        Logger logger = Arsenic.getArsenic().getLogger();
        authorised = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.execute(() -> {
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                    HttpGet get = new HttpGet("http://140.238.204.221:5001/close");
                    logger.info("Closed Launch | " + EntityUtils.toString(client.execute(get).getEntity()));
                } catch (Exception e) {
                    logger.info("Launch Logger Broke :(");
                    e.printStackTrace();
                }
            });
        }));
    }

    private class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response;
            if (allowed()) {
                setAuthorised();
                request(new HttpGet("http://140.238.204.221:5001/launch"));
                response = "Success. You can close this window now";
                server.stop(0);
            } else {
                openWebsite();
                response = "Fail... Try again";
            }
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
