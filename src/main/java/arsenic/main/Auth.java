package arsenic.main;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import spark.Spark;

import java.awt.*;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static spark.Spark.get;
import static spark.Spark.port;

public class Auth {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private boolean authorised = false;

    public void init() {
        if(allowed()) {
            setAuthorised();
            request(new HttpGet("http://140.238.204.221:5001/launch"));;
            return;
        }
        port(4567);
        openWebsite();
        get("/", (req, res) -> {
            if(allowed()) {
                setAuthorised();
                request(new HttpGet("http://140.238.204.221:5001/launch"));
                Spark.stop();
                return "Success. You can close this window now";
            }
            openWebsite();
            return "Fail... Try again";
        });
    }

    private <T extends HttpRequestBase> String request(T t) {
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            String ret = EntityUtils.toString(client.execute(t).getEntity());
            client.close();
            return ret;
        } catch (Exception ignored) {}
        return null;
    }



    private boolean allowed() {
        String ret = request(new HttpGet("http://140.238.204.221:5001/get"));
        return ret.equals("Success");
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
        if(authorised)
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

}
