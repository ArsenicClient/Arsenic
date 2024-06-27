package arsenic.gui.click;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IContainer;

public enum UICategory implements IContainer<ModuleCategory> {

    COMBAT("Combat", ModuleCategory.MOVEMENT, ModuleCategory.GHOST, ModuleCategory.BLATANT),
    VISUAL("Visual", ModuleCategory.PLAYER, ModuleCategory.CLIENT, ModuleCategory.WORLD),
    MISC("Misc", ModuleCategory.OTHER);

    private final String name;
    private final List<ModuleCategory> childCategories = new ArrayList<>();

    UICategory(String name, ModuleCategory... childCategories) {
        this.name = name;
        this.childCategories.addAll(Arrays.asList(childCategories));
    }

    @Override
    public String getName() { return name; }

    @Override
    public Collection<ModuleCategory> getContents() { return childCategories; }
}
