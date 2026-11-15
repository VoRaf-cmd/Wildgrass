package javaengine;

import static org.lwjgl.opengl.GL33C.*;

final class Hotbar {
    private static final float SELECT_DURATION = 0.18f;
    private static final float PICKUP_DURATION = 0.42f;
    // Cada caractere na silhueta aponta para uma cor da paleta.
    private static final int[] PALETTE = {
            0x30241C, 0x774727, 0xAB6F3D, 0xDDBC78, 0x73858F, 0xA8B9BB, 0x4D5C64
    };
    private static final String[] AXE = {
            "    0000   ",
            "   044550  ",
            "  05444560 ",
            " 055544360 ",
            " 055443000 ",
            " 0003320   ",
            "    0220   ",
            "   0220    ",
            "  0220     ",
            " 0210      ",
            " 010       ",
            " 00        "
    };
    private static final String[] LOG = {
            "           ",
            "     00000 ",
            "   0011120 ",
            " 001222210 ",
            "033012210  ",
            "032301100  ",
            "03230100   ",
            "033000     ",
            " 00        ",
            "           "
    };
    private static final String[] LIGHTER = {
            " 000000 ",
            "03666630",
            "03555530",
            "00000000",
            "02333210",
            "02332210",
            "02322210",
            "02222210",
            "02111110",
            " 000000 "
    };
    private static final String[] MACHETE = {
            "        00 ",
            "       0550",
            "      05550",
            "     04550 ",
            "    04550  ",
            "   04550   ",
            "  04550    ",
            " 04450     ",
            "  030      ",
            " 0210      ",
            " 210       ",
            " 00        "
    };
    private static final int[] DIGITS = {
            0b111_101_101_101_111, 0b010_011_010_010_111, 0b111_100_111_001_111, 0b111_100_111_100_111,
            0b101_101_111_100_100, 0b111_001_111_100_111, 0b111_001_111_101_111, 0b111_100_010_010_010,
            0b111_101_111_101_111, 0b111_101_111_100_111
    };

    private final int[] previousCounts = new int[Inventory.SLOT_COUNT];
    private final float[] pickupTime = new float[Inventory.SLOT_COUNT];
    private int selected = -1;
    private float selectionStart;
    private float selectionPosition;
    private float selectionTime;

    void draw(int width, int height, Inventory inventory, float delta) {
        update(inventory, delta);
        int gap = 2;
        int margin = 6;
        int slotSize = Math.min(18, (width - margin * 2 - gap * (Inventory.SLOT_COUNT - 1)) / Inventory.SLOT_COUNT);
        if (slotSize < 14 || height < slotSize + margin * 2) {
            return;
        }

        int stride = slotSize + gap;
        glEnable(GL_SCISSOR_TEST);
        for (int slot = 0; slot < Inventory.SLOT_COUNT; slot++) {
            int x = margin + slot * stride;
            float focus = Math.max(0.0f, 1.0f - Math.abs(slot - selectionPosition));
            int y = margin + Math.round(focus * 2.0f);
            float pulse = pickupTime[slot] / PICKUP_DURATION;
            drawSlot(x, y, slotSize, focus, pulse, inventory.item(slot), inventory.count(slot));
        }
        drawSelection(margin + Math.round(selectionPosition * stride), margin + 2, slotSize);
        glDisable(GL_SCISSOR_TEST);
    }

    private void update(Inventory inventory, float delta) {
        if (selected == -1) {
            selected = inventory.selected();
            selectionPosition = selected;
            selectionStart = selected;
            selectionTime = SELECT_DURATION;
            for (int slot = 0; slot < Inventory.SLOT_COUNT; slot++) {
                previousCounts[slot] = inventory.count(slot);
            }
        }
        if (selected != inventory.selected()) {
            selectionStart = selectionPosition;
            selected = inventory.selected();
            selectionTime = 0.0f;
        }
        selectionTime = Math.min(SELECT_DURATION, selectionTime + delta);
        float remaining = 1.0f - selectionTime / SELECT_DURATION;
        selectionPosition = selected + (selectionStart - selected) * remaining * remaining * remaining;

        for (int slot = 0; slot < Inventory.SLOT_COUNT; slot++) {
            if (inventory.count(slot) > previousCounts[slot]) {
                pickupTime[slot] = PICKUP_DURATION;
            } else {
                pickupTime[slot] = Math.max(0.0f, pickupTime[slot] - delta);
            }
            previousCounts[slot] = inventory.count(slot);
        }
    }

    private static void drawSlot(int x, int y, int size, float focus, float pulse, Inventory.Item item, int amount) {
        fill(x, y, size, size, 0x151A17);
        fill(x + 1, y + 1, size - 2, size - 2, mix(0x666A5C, 0xD9C18B, Math.max(focus, pulse)));
        fill(x + 2, y + 2, size - 4, size - 4, mix(mix(0x252C26, 0x3D3D31, focus), 0x73613A, pulse * pulse));
        if (item != null) {
            String[] icon = icon(item);
            int bounce = Math.round(3.0f * (float) Math.sin(Math.PI * pulse) * pulse);
            drawIcon(icon, x + (size - icon[0].length()) / 2, y + (size - icon.length) / 2 + bounce);
        }
        if (item != null && item.stackable) {
            int right = x + size - 5;
            int left = amount >= 10 ? right - 4 : right;
            fill(left - 1, y + 1, right - left + 5, 7, 0x151A17);
            if (amount >= 10) {
                digit(amount / 10, left, y + 2, 0xF4E6BF);
            }
            digit(amount % 10, right, y + 2, 0xF4E6BF);
        }
        if (pulse > 0.0f) {
            int lift = 1 + Math.round((1.0f - pulse) * 6.0f);
            int glow = mix(0x7F795E, 0xF6D78F, pulse);
            fill(x + 3, y + size + lift, 2, 1, glow);
            fill(x + size - 4, y + size + lift + 1, 1, 2, glow);
            fill(x + size / 2, y + size + lift + 4, 1, 1, glow);
        }
    }

    static String[] icon(Inventory.Item item) {
        return switch (item) {
            case AXE -> AXE;
            case LIGHTER -> LIGHTER;
            case MACHETE -> MACHETE;
            case LOG -> LOG;
            case BOW -> BOW;
            case ARROW -> ARROW;
            case STONE -> STONE;
            case TORCH -> TORCH;
            case STICK -> STICK;
            case FIBER -> FIBER;
            case DYNAMITE -> DYNAMITE;
            case CAMPFIRE -> CAMPFIRE;
        };
    }

    static int iconColor(char code) {
        return switch (code) {
            case 'r' -> 0xA83D32;
            case 'R' -> 0xE16D4B;
            default -> PALETTE[code - '0'];
        };
    }

    private static final String[] DYNAMITE = {
            "    33  ", "    3   ", " 00 30  ", "0RrrRr0 ", "0RrrRr0 ", "0222220 ", "0RrrRr0 ", " 00000  "
    };

    private static final String[] CAMPFIRE = {
            "  3333  ", " 322223 ", " 233332 ", " 332233 ", " 223322 ", "  3223  ", "   33   "
    };

    private static final String[] BOW = {"  300  ","  3320 ","   0320","   0320","   0320","  0320 "," 0320  ","3000   "};
    private static final String[] ARROW = {"     55","    455","   24  ","  22   "," 22    ","56     ","6      "};
    private static final String[] STONE = {"  0440 "," 045540","0455440","044460 "," 0660  "};
    private static final String[] TORCH = {"  33   "," 323   ","03230  "," 220   "," 220   "," 20    "," 10    "};
    private static final String[] STICK = {"     20","    210"," 0 210 "," 0210  "," 210   ","210    "};
    private static final String[] FIBER = {" 33  3 "," 23233 "," 23230 ","  232  ","  220  ","  10   "};

    private static void drawSelection(int x, int y, int size) {
        int color = 0xF4E6BF;
        fill(x, y + size - 1, 4, 1, color);
        fill(x + size - 4, y + size - 1, 4, 1, color);
        fill(x, y + size - 4, 1, 3, color);
        fill(x + size - 1, y + size - 4, 1, 3, color);
        fill(x + size / 2 - 2, y - 2, 4, 1, 0xD9C18B);
    }

    private static int mix(int from, int to, float amount) {
        int red = mixChannel(from >> 16, to >> 16, amount);
        int green = mixChannel(from >> 8, to >> 8, amount);
        int blue = mixChannel(from, to, amount);
        return (red << 16) | (green << 8) | blue;
    }

    private static int mixChannel(int from, int to, float amount) {
        return Math.round(org.joml.Math.lerp(from & 255, to & 255, amount));
    }

    private static void drawIcon(String[] icon, int x, int y) {
        for (int row = 0; row < icon.length; row++) {
            String pixels = icon[row];
            for (int from = 0; from < pixels.length();) {
                char color = pixels.charAt(from);
                int to = from + 1;
                while (to < pixels.length() && pixels.charAt(to) == color) {
                    to++;
                }
                if (color != ' ') {
                    fill(x + from, y + icon.length - row - 1, to - from, 1, iconColor(color));
                }
                from = to;
            }
        }
    }

    static void digit(int number, int x, int y, int color) {
        int pixels = DIGITS[number];
        for (int i = 0; i < 15; i++) {
            if ((pixels & (1 << i)) != 0) {
                fill(x + i % 3, y + i / 3, 1, 1, color);
            }
        }
    }

    private static void fill(int x, int y, int width, int height, int rgb) {
        glScissor(x, y, width, height);
        glClearColor(((rgb >> 16) & 255) / 255.0f, ((rgb >> 8) & 255) / 255.0f, (rgb & 255) / 255.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
}
