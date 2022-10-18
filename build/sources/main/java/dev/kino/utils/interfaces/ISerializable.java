package dev.kino.utils.interfaces;

import com.google.gson.JsonObject;

public interface ISerializable {

    void loadFromJson(JsonObject obj);
    JsonObject safeToJson();
    String getJsonKey();

}
