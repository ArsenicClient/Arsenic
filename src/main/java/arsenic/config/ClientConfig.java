package arsenic.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import arsenic.utils.interfaces.IConfig;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.ISerializable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import arsenic.main.Arsenic;

public class ClientConfig implements IConfig<ISerializable> {


    private final File config;
    private final List<ISerializable> contents = new ArrayList<>();

    public ClientConfig(File config) {
        this.config = config;
        contents.add(Arsenic.getArsenic().getConfigManager());
    }

    @Override
    public File getDirectory() {
        return config;
    }

    @Override
    public Collection<ISerializable> getContents() {
        return contents;
    }
}
