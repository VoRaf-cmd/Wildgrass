package javaengine;

import java.awt.Rectangle;

import static javaengine.GameSettings.OPTIONS;
import static org.lwjgl.glfw.GLFW.*;

final class GameMenu {
    static final int DESIGN_WIDTH = 960;
    static final int DESIGN_HEIGHT = 540;
    static final int TRACK_LEFT = 318;
    static final int TRACK_WIDTH = 118;
    static final int BACK = OPTIONS.length;
    static final int RESET = BACK + 1;

    enum Screen { TITLE, PLAYING, PAUSE, SETTINGS, INVENTORY }

    private final GameSettings settings;
    private final Inventory inventory;
    private int picked = -1;
    private Screen screen = Screen.TITLE;
    private Screen settingsFrom = Screen.TITLE;
    private int selected;
    private int hovered = -1;
    private int dragged = -1;
    private int revision;
    private boolean quit;

    GameMenu(GameSettings settings) {
        this(settings, new Inventory());
    }

    GameMenu(GameSettings settings, Inventory inventory) {
        this.settings = settings;
        this.inventory = inventory;
    }

    Inventory inventory() { return inventory; }
    int picked() { return picked; }

    GameSettings settings() {
        return settings;
    }

    Screen screen() {
        return screen;
    }

    boolean visible() {
        return screen != Screen.PLAYING;
    }

    boolean titleBackdrop() {
        return screen == Screen.TITLE || screen == Screen.SETTINGS && settingsFrom == Screen.TITLE;
    }

    boolean quitRequested() {
        return quit;
    }

    int revision() {
        return revision;
    }

    int selection() {
        return hovered >= 0 ? hovered : selected;
    }

    int rowCount() {
        if (screen == Screen.INVENTORY) return Inventory.CAPACITY + 3;
        return screen == Screen.SETTINGS ? RESET + 1 : 3;
    }

    Rectangle bounds(int row) {
        if (screen == Screen.INVENTORY) {
            if (row >= Inventory.CAPACITY) return new Rectangle(574, 152 + (row - Inventory.CAPACITY) * 71, 271, 58);
            return new Rectangle(92 + row % 7 * 61, row < 7 ? 144 : 245 + (row / 7 - 1) * 61, 53, 53);
        }
        if (screen != Screen.SETTINGS) {
            return new Rectangle(65, 245 + row * 55, 315, 44);
        }
        if (row == BACK || row == RESET) {
            return new Rectangle(row == BACK ? 65 : 260, 424, row == BACK ? 178 : 197, 42);
        }
        return new Rectangle(65, 112 + row * 49, 392, 42);
    }

    void key(int key, int action) {
        if (action == GLFW_RELEASE) {
            return;
        }
        if (key == GLFW_KEY_E && action == GLFW_PRESS) {
            if (screen == Screen.PLAYING) show(Screen.INVENTORY, 0);
            else if (screen == Screen.INVENTORY) show(Screen.PLAYING, 0);
            return;
        }
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
            switch (screen) {
                case PLAYING -> show(Screen.PAUSE, 0);
                case PAUSE -> show(Screen.PLAYING, 0);
                case INVENTORY -> show(Screen.PLAYING, 0);
                case SETTINGS -> returnFromSettings();
                case TITLE -> { }
            }
            return;
        }
        if (!visible()) {
            return;
        }
        if (key == GLFW_KEY_UP || key == GLFW_KEY_DOWN || key == GLFW_KEY_TAB) {
            int next = Math.floorMod(selection() + (key == GLFW_KEY_UP ? -1 : 1), rowCount());
            hovered = -1;
            selected = next;
            revision++;
        } else if ((key == GLFW_KEY_LEFT || key == GLFW_KEY_RIGHT) && screen == Screen.SETTINGS && selection() < BACK) {
            var option = OPTIONS[selection()];
            set(option, settings.get(option) + (key == GLFW_KEY_LEFT ? -option.step : option.step));
            settings.save();
        } else if (action == GLFW_PRESS && (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER || key == GLFW_KEY_SPACE)) {
            activate(selection());
        }
    }

    void pause() {
        if (screen == Screen.PLAYING) {
            show(Screen.PAUSE, 0);
        }
        release();
    }

    void pointer(double windowX, double windowY, int width, int height, boolean press) {
        if (!visible() || width <= 0 || height <= 0) {
            return;
        }
        double scale = Math.min((double) width / DESIGN_WIDTH, (double) height / DESIGN_HEIGHT);
        double x = (windowX - (width - DESIGN_WIDTH * scale) * 0.5) / scale;
        double y = (windowY - (height - DESIGN_HEIGHT * scale) * 0.5) / scale;
        if (dragged >= 0) {
            slide(dragged, x);
            return;
        }
        int row = -1;
        for (int i = 0; i < rowCount(); i++) {
            if (bounds(i).contains(x, y)) {
                row = i;
                break;
            }
        }
        if (hovered != row) {
            hovered = row;
            revision++;
        }
        if (!press || row < 0) {
            return;
        }
        selected = row;
        if (screen == Screen.SETTINGS && row < GameSettings.Option.VSYNC.ordinal() && x >= TRACK_LEFT - 10) {
            dragged = row;
            slide(row, x);
        } else {
            activate(row);
        }
    }

    void release() {
        if (dragged >= 0) {
            dragged = -1;
            settings.save();
        }
    }

    private void slide(int row, double x) {
        var option = OPTIONS[row];
        double ratio = Math.clamp((x - TRACK_LEFT) / TRACK_WIDTH, 0.0, 1.0);
        set(option, option.min + (int) Math.round(ratio * (option.max - option.min)));
    }

    private void set(GameSettings.Option option, int value) {
        if (settings.set(option, value)) {
            revision++;
        }
    }

    private void activate(int row) {
        if (screen == Screen.INVENTORY) {
            if (row >= Inventory.CAPACITY) {
                inventory.craft(row == Inventory.CAPACITY ? Inventory.Item.ARROW
                        : row == Inventory.CAPACITY + 1 ? Inventory.Item.TORCH : Inventory.Item.CAMPFIRE);
                picked = -1;
            } else if (picked >= 0) {
                inventory.moveStack(picked, row);
                picked = -1;
            } else if (inventory.item(row) != null) {
                picked = row;
            }
            revision++;
            return;
        }
        if (screen == Screen.SETTINGS) {
            if (row == BACK) {
                returnFromSettings();
            } else if (row == RESET) {
                settings.restoreDefaults();
                settings.save();
                revision++;
            } else if (row == GameSettings.Option.VSYNC.ordinal()) {
                set(GameSettings.Option.VSYNC, 1 - settings.get(GameSettings.Option.VSYNC));
                settings.save();
            }
            return;
        }
        if (row == 0) {
            show(Screen.PLAYING, 0);
        } else if (row == 1) {
            settingsFrom = screen;
            show(Screen.SETTINGS, 0);
        } else {
            quit = true;
        }
    }

    private void returnFromSettings() {
        settings.save();
        show(settingsFrom, 1);
    }

    private void show(Screen next, int focus) {
        release();
        screen = next;
        selected = focus;
        hovered = -1;
        picked = -1;
        revision++;
    }
}
