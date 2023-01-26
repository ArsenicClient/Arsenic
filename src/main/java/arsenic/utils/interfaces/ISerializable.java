package arsenic.utils.interfaces;

import com.google.gson.JsonObject;

public interface ISerializable {

    void loadFromJson(JsonObject obj);
    JsonObject saveInfoToJson(JsonObject obj);
    JsonObject addToJson(JsonObject obj);
    String getJsonKey();

}
