package arsenic.main;
import arsenic.config.LaunchID;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static spark.Spark.*;


public class Auth {

    private final Executor executor = Executors.newSingleThreadExecutor();

    public void init() {
        final Logger logger = Arsenic.getArsenic().getLogger();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                URI url = new URI("https://discord.com/oauth2/authorize?client_id=1271352455984316437&response_type=code&redirect_uri=http%3A%2F%2F140.238.204.221%3A5001%2Fcallback&scope=identify");
                desktop.browse(url);
            } else {
                System.out.println("Desktop is not supported on this platform.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        port(4567);
        get("/", (req, res) -> {
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet get = new HttpGet("http://140.238.204.221:5001/get");
                String ret = EntityUtils.toString(client.execute(get).getEntity());
                System.out.println("Look here:" + ret);
            } catch (Exception e) {
                logger.info("Your not allowed to use this app ");
                e.printStackTrace();
            }
            return "You can close this window now";
        });
        executor.execute(() -> {
            LaunchID launchID = Arsenic.getArsenic().getLaunchID();
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("http://140.238.204.221:5001/log");
                post.setEntity(new StringEntity(launchID.getLaunchID()));
                System.out.println(client.execute(post));
                logger.info("Logged Launch with ID | " + launchID.getLaunchID());
            } catch (Exception e) {
                logger.info("Launch Logger Broke :( | " + launchID.getLaunchID());
                e.printStackTrace();
            }
            Arsenic.getArsenic().getConfigManager().saveClientConfig();
        });
    }

}
