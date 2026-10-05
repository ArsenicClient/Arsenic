package arsenic.config;

import arsenic.utils.interfaces.ISerializable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FriendManager implements ISerializable {

    private final Map<String, String> friends = new LinkedHashMap<>();

    public boolean add(String name) {
        return friends.putIfAbsent(key(name), name) == null;
    }

    public boolean remove(String name) {
        return friends.remove(key(name)) != null;
    }

    public void clear() {
        friends.clear();
    }

    public boolean isFriend(String name) {
        return name != null && friends.containsKey(key(name));
    }

    public boolean isFriend(EntityPlayer player) {
        return player != null && isFriend(player.getName());
    }

    public List<String> getFriends() {
        return new ArrayList<>(friends.values());
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        friends.clear();
        JsonElement list = obj.get("names");
        if (list == null || !list.isJsonArray())
            return;
        for (JsonElement e : list.getAsJsonArray())
            add(e.getAsString());
    }

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        JsonArray list = new JsonArray();
        friends.values().forEach(name -> list.add(new JsonPrimitive(name)));
        obj.add("names", list);
        return obj;
    }

    @Override
    public String getJsonKey() {
        return "friends";
    }
}
