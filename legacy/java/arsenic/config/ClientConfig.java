package arsenic.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import arsenic.utils.interfaces.IConfig;
import arsenic.utils.interfaces.ISerializable;

import arsenic.main.Arsenic;

/** clientConfig.json: what is shared by every config (the selected config, theme, GUI style, friends, ...). */
public class ClientConfig implements IConfig<ISerializable> {

    private final File config;
    private final List<ISerializable> contents = new ArrayList<>();

    public ClientConfig(File config) {
        this.config = config;
        // the config manager only remembers which config was selected here; it is loaded after everything else
        contents.add(Arsenic.getArsenic().getConfigManager());
        contents.add(Arsenic.getInstance().getThemeManager());
        contents.add(arsenic.gui.click.GuiStyle.get());
        contents.add(Arsenic.getInstance().getFriendManager());
        contents.add(Arsenic.getInstance().getErrorOverlay());
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
