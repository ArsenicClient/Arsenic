package arsenic.gui.demo;

import arsenic.gui.NVGScreen;
import arsenic.utils.misc.ScrollHelper;

import java.awt.*;

public class ScreenDemo extends NVGScreen {
    private ScrollHelper scroll = new ScrollHelper();
    @Override
    public void init() {

    }

    @Override
    public void render(float ticks, float mouseX, float mouseY) {
        scroll.setElementsHeight(1100);
        scroll.setMaxScroll(400);
        scroll.setSpeed(80);
        scroll.setStep(50);
        float sc = scroll.getScroll();

        ui.dropShadow(200,200,200,200,50,100,Color.RED,new Color(0,0,0,0), false);
        ui.line2colors(80,300,200,400,8,Color.BLUE, Color.GREEN);
        ui.circle(600,600, 50,Color.YELLOW);
        ui.text("NanoVG >>", 80, 80, "semibold", (System.currentTimeMillis()%1000)/2f, Color.WHITE);

        for (int i = 0; i < 10; i++) {
            ui.round(600,i*120+sc,300,100, 10, Color.GRAY);
        }
    }

    @Override
    public void click(int button, float mouseX, float mouseY) {

    }

    @Override
    public void release(int button, float mouseX, float mouseY) {

    }
}
