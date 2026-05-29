package arsenic.utils.discord;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import arsenic.main.Arsenic;
import net.minecraft.client.Minecraft;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordRPCManager {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final long appId;
    private IPCClient ipcClient;
    private boolean running;
    private final OffsetDateTime timestamp = OffsetDateTime.now();
    private ScheduledExecutorService executor;

    private boolean showServer = true;
    private boolean showName = true;
    private boolean showHealth = false;
    private boolean showModuleCount = false;

    public DiscordRPCManager(long appId) {
        this.appId = appId;
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void start() {
        if (running) return;
        running = true;

        try {
            ipcClient = new IPCClient(appId);
            ipcClient.setListener(new IPCListener() {
                @Override
                public void onReady(IPCClient client) {
                    executor = Executors.newSingleThreadScheduledExecutor();
                    executor.scheduleAtFixedRate(DiscordRPCManager.this::update, 0, 2, TimeUnit.SECONDS);
                }

                @Override
                public void onClose(IPCClient client, org.json.JSONObject json) {
                    running = false;
                }
            });
            ipcClient.connect();
        } catch (Exception e) {
            Arsenic.getArsenic().getLogger().error("Failed to initialize Discord RPC", e);
            running = false;
        }
    }

    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        if (ipcClient != null && ipcClient.getStatus() == PipeStatus.CONNECTED) {
            try {
                ipcClient.close();
            } catch (Exception e) {
                Arsenic.getArsenic().getLogger().error("Failed to close Discord RPC", e);
            }
        }
        ipcClient = null;
    }

    private void update() {
        if (!running || ipcClient == null || ipcClient.getStatus() != PipeStatus.CONNECTED) return;

        try {
            StringBuilder state = new StringBuilder();

            if (showServer && mc.getCurrentServerData() != null) {
                state.append("Server: ").append(mc.getCurrentServerData().serverIP).append("\n");
            }

            if (showName && mc.thePlayer != null) {
                state.append("IGN: ").append(mc.thePlayer.getName()).append("\n");
            }

            if (showHealth && mc.thePlayer != null) {
                state.append("HP: ").append(String.format("%.1f", mc.thePlayer.getHealth())).append("\n");
            }

            if (showModuleCount) {
                int enabled = Arsenic.getArsenic().getModuleManager().getEnabledModules().size();
                int total = Arsenic.getArsenic().getModuleManager().getModules().size();
                state.append("Modules: ").append(enabled).append("/").append(total).append("\n");
            }

            String stateStr = state.length() > 0 ? state.toString().trim() : null;
            String details = "Arsenic " + Arsenic.getArsenic().getClientVersionString();

            ipcClient.sendRichPresence(new RichPresenceWithButtons(
                    stateStr, details, timestamp, null,
                    null, null, null, null,
                    null, 0, 0, null, null, null, false,
                    new String[]{"Download Arsenic", "Arsenic Discord"},
                    new String[]{"https://github.com/ArsenicClient/Arsenic", "https://discord.com/invite/UqJ8ngteud"}));
        } catch (Exception e) {
            Arsenic.getArsenic().getLogger().error("Failed to update Discord RPC", e);
        }
    }

    public void setShowServer(boolean show) { this.showServer = show; }
    public void setShowName(boolean show) { this.showName = show; }
    public void setShowHealth(boolean show) { this.showHealth = show; }
    public void setShowModuleCount(boolean show) { this.showModuleCount = show; }

    private static class RichPresenceWithButtons extends RichPresence {
        private final String[] buttonLabels;
        private final String[] buttonUrls;

        RichPresenceWithButtons(String state, String details, OffsetDateTime startTimestamp,
                                OffsetDateTime endTimestamp, String largeImageKey, String largeImageText,
                                String smallImageKey, String smallImageText, String partyId, int partySize,
                                int partyMax, String matchSecret, String joinSecret, String spectateSecret,
                                boolean instance, String[] buttonLabels, String[] buttonUrls) {
            super(state, details, startTimestamp, endTimestamp, largeImageKey, largeImageText,
                  smallImageKey, smallImageText, partyId, partySize, partyMax, matchSecret,
                  joinSecret, spectateSecret, instance);
            this.buttonLabels = buttonLabels;
            this.buttonUrls = buttonUrls;
        }

        @Override
        public JSONObject toJson() {
            JSONObject json = super.toJson();
            JSONArray buttons = new JSONArray();
            if (buttonLabels != null && buttonUrls != null) {
                for (int i = 0; i < Math.min(buttonLabels.length, Math.min(buttonUrls.length, 2)); i++) {
                    if (buttonLabels[i] != null && buttonUrls[i] != null) {
                        buttons.put(new JSONObject().put("label", buttonLabels[i]).put("url", buttonUrls[i]));
                    }
                }
            }
            if (buttons.length() > 0) {
                json.put("buttons", buttons);
            }
            return json;
        }
    }
}
