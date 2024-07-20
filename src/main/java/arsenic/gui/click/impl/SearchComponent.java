package arsenic.gui.click.impl;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.render.RenderInfo;
import org.lwjgl.input.Keyboard;

import java.util.stream.Collectors;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

public class SearchComponent extends ModuleCategoryComponent implements IAlwaysKeyboardInput {

    private StringBuilder inp = new StringBuilder();

    public SearchComponent(ModuleCategory category) {
        super(category);
    }

    @Override
    protected void clickComponent(int mouseX, int mouseY, int mouseButton) {
        Arsenic.getArsenic().getClickGuiScreen().setCmcc(this);
        Arsenic.getArsenic().getClickGuiScreen().setAlwaysInputComponent(this);
    }

    @Override
    protected float drawComponent(RenderInfo ri) {
        ri.getFr().drawString(inp.toString(), x2 * 2, midPointY, getEnabledColor(), ri.getFr().CENTREY);
        return super.drawComponent(ri);
    }

    @Override
    public void setNotAlwaysRecieveInput() {

    }

    @Override
    public boolean recieveInput(int key) {
        String keyName = Keyboard.getKeyName(key);
        if(keyName.equals("BACK")) {
            if(inp.length() >= 1)
                inp.deleteCharAt(inp.length() - 1);
        } else {
            inp.append(keyName);
        }

        contentsL.clear();
        contentsR.clear();
        contents.stream().filter(m -> m.getName().toLowerCase().startsWith(inp.toString().toLowerCase())).collect(Collectors.toList()).forEach(module -> {
            if ((contentsL.size() + contentsR.size()) % 2 == 0) {
                contentsL.add(module);
            } else {
                contentsR.add(module);
            }
        });

        return false;
    }
}
