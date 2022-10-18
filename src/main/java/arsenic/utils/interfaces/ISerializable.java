package arsenic.utils.interfaces;

import com.google.gson.JsonObject;

public interface ISerializable {

    void loadFromJson(JsonObject obj);
    JsonObject saveToJson();
    String getJsonKey();

}
