package arsenic.gui.click.impl;

import arsenic.main.Arsenic;
import arsenic.module.ModuleCategory;
import arsenic.utils.interfaces.IAlwaysKeyboardInput;
import arsenic.utils.render.RenderInfo;
import org.lwjgl.input.Keyboard;

import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

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
        } else if (keyName.length() == 1){
            inp.append(keyName);
        } else {
            return false;
        }

        contentsL.clear();
        contentsR.clear();
        contents.stream()
                .map(module -> new AbstractMap.SimpleEntry<>(module, levenshteinDistance(module.getName().toLowerCase(), inp.toString().toLowerCase())))
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList()).forEach(module -> {
            if ((contentsL.size() + contentsR.size()) % 2 == 0) {
                contentsL.add(module);
            } else {
                contentsR.add(module);
            }
        });

        return false;
    }

    public int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1));
                }
            }
        }

        return dp[a.length()][b.length()];
    }
}
