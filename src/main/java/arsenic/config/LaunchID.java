package arsenic.config;

import arsenic.utils.interfaces.ISerializable;
import com.google.gson.JsonObject;
import org.lwjgl.Sys;

import java.security.SecureRandom;

public class LaunchID implements ISerializable {

    private String launchID;
    private final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void loadFromJson(JsonObject obj) {
        String LID = obj.get("ID").getAsString();
        setLaunchID(LID);
        System.out.println("LID1: " + LID + " " + launchID);
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        obj.addProperty("ID", getLaunchID());
        return obj;
    }

    @Override
    public String getJsonKey() {
        return "LID";
    }

    public String getLaunchID() {
        System.out.println("LID: " + launchID);
        if(!isValidID(launchID))
            launchID = generateID();
        return launchID;
    }

    public void setLaunchID(String launchID) {
        if(!isValidID(launchID))
            launchID = generateID();
        this.launchID = launchID;
    }

    public String generateID() {
        int LENGTH = 8;
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            int index = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

    private static boolean isValidID(String content) {
        return content != null && content.matches("^[a-zA-Z0-9]{8}$");
    }
}
