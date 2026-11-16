package javaengine;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;

import static javaengine.GameMenu.DESIGN_HEIGHT;
import static javaengine.GameMenu.DESIGN_WIDTH;
import static org.lwjgl.opengl.GL33C.*;

final class MenuOverlay implements AutoCloseable {
    private static final String VERTEX = """
            #version 330 core
            out vec2 uv;
            void main() {
                vec2 p = vec2(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0);
                uv = vec2((p.x + 1.0) * 0.5, (1.0 - p.y) * 0.5);
                gl_Position = vec4(p, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 330 core
            in vec2 uv;
            uniform sampler2D overlay;
            out vec4 fragment;
            void main() { fragment = texture(overlay, uv); }
            """;
    private static final Color CREAM = new Color(0xF3E7C5);
    private static final Color GOLD = new Color(0xDCB872);
    private static final Color MUTED = new Color(0xC6C7AB);
    private static final Color DARK = new Color(0x202417);
    private static final Color LINE = new Color(0x72735C);
    private static final Color BUTTON = new Color(29, 39, 30, 223);
    private static final Color HOVER = new Color(62, 68, 43, 239);

    private final Font heading;
    private final Font body;
    private final int program;
    private final int vao;
    private final int texture;
    private BufferedImage image;
    private int paintedRevision = -1;

    MenuOverlay() throws IOException {
        heading = font("BarlowCondensed-SemiBold.ttf");
        body = font("Barlow-Regular.ttf");
        program = SceneShader.link(VERTEX, FRAGMENT);
        vao = glGenVertexArrays();
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "overlay"), 0);
    }

    private static Font font(String name) throws IOException {
        try (var stream = MenuOverlay.class.getResourceAsStream("/fonts/" + name)) {
            if (stream == null) {
                throw new IOException("Fonte ausente: " + name);
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (FontFormatException e) {
            throw new IOException("Fonte inválida: " + name, e);
        }
    }

    void draw(GameMenu menu, int width, int height) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            paint(menu);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                    GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, pixels());
        } else if (paintedRevision != menu.revision()) {
            paint(menu);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                    GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, pixels());
        }
        glViewport(0, 0, width, height);
        glUseProgram(program);
        glBindVertexArray(vao);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
    }

    private int[] pixels() {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private void paint(GameMenu menu) {
        Graphics2D graphics = image.createGraphics();
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            graphics.setComposite(java.awt.AlphaComposite.Src);
            graphics.setPaint(new LinearGradientPaint(0.0f, 0.0f, width, 0.0f,
                    new float[] {0.0f, 0.40f, 0.72f, 1.0f},
                    new Color[] {new Color(17, 27, 22, 252), new Color(17, 27, 22, 235),
                            new Color(17, 27, 22, 55), new Color(17, 27, 22, 9)}));
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double scale = Math.min((double) width / DESIGN_WIDTH, (double) height / DESIGN_HEIGHT);
            graphics.translate((width - DESIGN_WIDTH * scale) * 0.5, (height - DESIGN_HEIGHT * scale) * 0.5);
            graphics.scale(scale, scale);
            if (menu.screen() == GameMenu.Screen.INVENTORY) {
                paintInventory(graphics, menu);
            } else if (menu.screen() == GameMenu.Screen.SETTINGS) {
                paintSettings(graphics, menu);
            } else if (menu.screen() == GameMenu.Screen.CONNECT) {
                paintConnect(graphics, menu);
            } else {
                paintActions(graphics, menu);
            }
        } finally {
            graphics.dispose();
        }
        paintedRevision = menu.revision();
    }

    private void paintInventory(Graphics2D g, GameMenu menu) {
        g.setColor(new Color(19, 29, 23, 243));
        g.fillRect(65, 35, 817, 454);
        g.setColor(LINE);
        g.drawRect(65, 35, 817, 454);
        g.drawLine(547, 104, 547, 443);
        g.setColor(CREAM);
        g.setFont(heading.deriveFont(45.0f));
        g.drawString("INVENTÁRIO", 91, 90);
        g.setFont(heading.deriveFont(28.0f));
        g.drawString("FABRICAÇÃO", 574, 129);
        g.setFont(body.deriveFont(19.0f));
        g.drawString("Barra rápida", 92, 132);
        g.drawString("Mochila", 92, 233);
        for (int i = 0; i < Inventory.CAPACITY; i++) {
            Rectangle slot = menu.bounds(i);
            frame(g, slot, i == menu.selection() || i == menu.picked(), false);
            var item = menu.inventory().item(i);
            if (item != null) {
                String[] icon = Hotbar.icon(item);
                int scale = 3;
                int x = slot.x + (slot.width - icon[0].length() * scale) / 2;
                int y = slot.y + (slot.height - icon.length * scale) / 2;
                for (int row = 0; row < icon.length; row++) {
                    for (int col = 0; col < icon[row].length(); col++) {
                        char color = icon[row].charAt(col);
                        if (color != ' ') {
                            g.setColor(new Color(Hotbar.iconColor(color)));
                            g.fillRect(x + col * scale, y + row * scale, scale, scale);
                        }
                    }
                }
                if (item.stackable) {
                    g.setColor(CREAM);
                    g.setFont(body.deriveFont(14.0f));
                    rightText(g, Integer.toString(menu.inventory().count(i)), slot.x + 49, slot.y + 47);
                }
            }
        }
        recipe(g, menu, Inventory.CAPACITY, "3 flechas", "1 graveto + 1 pedra");
        recipe(g, menu, Inventory.CAPACITY + 1, "1 tocha", "1 graveto + 1 fibra seca");
        recipe(g, menu, Inventory.CAPACITY + 2, "1 fogueira", "3 gravetos + 2 pedras + 1 fibra");
        int selected = menu.selection();
        var item = selected < Inventory.CAPACITY ? menu.inventory().item(selected) : null;
        g.setFont(body.deriveFont(19.0f));
        g.setColor(CREAM);
        if (item != null) g.drawString(item.label, 92, 393);
        g.setColor(MUTED);
        g.setFont(body.deriveFont(16.0f));
        g.drawString("Selecione um item e depois o slot de destino.", 92, 449);
        g.drawString("E / Esc  Fechar", 574, 449);
    }

    private void recipe(Graphics2D g, GameMenu menu, int id, String name, String materials) {
        Rectangle rect = menu.bounds(id);
        frame(g, rect, id == menu.selection(), false);
        g.setColor(CREAM);
        g.setFont(body.deriveFont(21.0f));
        g.drawString(name, rect.x + 13, rect.y + 25);
        g.setColor(MUTED);
        g.setFont(body.deriveFont(15.0f));
        g.drawString(materials, rect.x + 13, rect.y + 46);
    }

    private void paintActions(Graphics2D g, GameMenu menu) {
        boolean title = menu.screen() == GameMenu.Screen.TITLE;
        g.setColor(CREAM);
        g.setFont(heading.deriveFont(title ? 96.0f : 81.0f));
        g.drawString(title ? "WILDGRASS" : "PAUSADO", 59, 187);
        g.setColor(GOLD);
        g.fillRect(65, 211, 47, 3);

        String[] labels = {title ? "Jogar solo" : "Continuar",
                title ? "Conectar por IP" : menu.clientSession() ? "Sair da sessão"
                        : menu.hostingSession() ? "LAN já aberta" : "Abrir para LAN",
                "Configurações", "Sair do jogo"};
        for (int row = 0; row < labels.length; row++) {
            button(g, menu.bounds(row), labels[row], row == menu.selection(), row == 0);
        }
        if (!menu.networkMessage().isEmpty()) {
            g.setColor(GOLD);
            g.setFont(body.deriveFont(16.0f));
            g.drawString(menu.networkMessage(), 65, 475);
        }
        hint(g, "Setas para navegar   ·   Enter para selecionar");
    }

    private void paintConnect(Graphics2D g, GameMenu menu) {
        g.setColor(CREAM);
        g.setFont(heading.deriveFont(63.0f));
        g.drawString("CONECTAR POR IP", 60, 153);
        g.setColor(MUTED);
        g.setFont(body.deriveFont(19.0f));
        g.drawString("Digite o IPv4 do anfitrião no Radmin VPN", 65, 198);
        Rectangle input = new Rectangle(65, 218, 365, 51);
        frame(g, input, true, false);
        g.setColor(menu.ipAddress().isEmpty() ? MUTED : CREAM);
        g.setFont(body.deriveFont(23.0f));
        g.drawString(menu.ipAddress().isEmpty() ? "Ex.: 26.12.34.56" : menu.ipAddress(), 79, 251);
        button(g, menu.bounds(0), "Conectar · UDP 7777", menu.selection() == 0, true);
        button(g, menu.bounds(1), "Voltar", menu.selection() == 1, false);
        if (!menu.networkMessage().isEmpty()) {
            g.setColor(GOLD);
            g.setFont(body.deriveFont(16.0f));
            g.drawString(menu.networkMessage(), 65, 456);
        }
        hint(g, "Digite o IP ou cole com Ctrl+V   ·   Esc para voltar");
    }

    private void paintSettings(Graphics2D g, GameMenu menu) {
        g.setColor(CREAM);
        g.setFont(heading.deriveFont(53.0f));
        g.drawString("CONFIGURAÇÕES", 62, 89);
        for (var option : GameSettings.OPTIONS) {
            int row = option.ordinal();
            Rectangle rect = menu.bounds(row);
            int value = menu.settings().get(option);
            boolean focused = row == menu.selection();
            frame(g, rect, focused, false);
            g.setColor(CREAM);
            g.setFont(body.deriveFont(19.0f));
            g.drawString(option.label, rect.x + 13, rect.y + 27);
            if (option == GameSettings.Option.VSYNC) {
                int switchX = rect.x + rect.width - 108;
                g.setColor(value == 1 ? GOLD : LINE);
                g.fillRect(switchX, rect.y + 16, 23, 10);
                g.setColor(DARK);
                g.fillRect(switchX + (value == 1 ? 13 : 3), rect.y + 18, 7, 6);
                g.setFont(body.deriveFont(15.0f));
                g.setColor(CREAM);
                rightText(g, option.display(value), rect.x + rect.width - 16, rect.y + 26);
            } else {
                slider(g, rect, option, value);
            }
        }
        button(g, menu.bounds(GameMenu.BACK), "Voltar", menu.selection() == GameMenu.BACK, false);
        button(g, menu.bounds(GameMenu.RESET), "Restaurar padrões", menu.selection() == GameMenu.RESET, false);
        hint(g, "Setas para ajustar   ·   Esc para voltar");
    }

    private void slider(Graphics2D g, Rectangle row, GameSettings.Option option, int value) {
        int x = GameMenu.TRACK_LEFT;
        int w = GameMenu.TRACK_WIDTH;
        int y = row.y + 29;
        int fill = Math.round(w * (value - option.min) / (float) (option.max - option.min));
        g.setColor(LINE);
        g.fillRect(x, y, w, 2);
        g.setColor(GOLD);
        g.fillRect(x, y, fill, 2);
        if (option == GameSettings.Option.PIXELS) {
            g.fillRect(x + w / 2, y - 1, 1, 4);
        }
        g.fillRect(x + fill - 2, y - 3, 5, 8);
        g.setFont(body.deriveFont(14.0f));
        g.setColor(CREAM);
        rightText(g, option.display(value), x + w, row.y + 19);
    }

    private void button(Graphics2D g, Rectangle rect, String label, boolean focused, boolean primary) {
        frame(g, rect, focused, primary);
        Color foreground = focused && primary ? DARK : CREAM;
        g.setColor(foreground);
        g.setFont(body.deriveFont(21.0f));
        g.drawString(label, rect.x + 14, rect.y + 29);
        if (focused && rect.width > 250) {
            int x = rect.x + rect.width - 22;
            int y = rect.y + rect.height / 2;
            g.fillPolygon(new int[] {x - 3, x + 3, x - 3}, new int[] {y - 4, y, y + 4}, 3);
        }
    }

    private static void frame(Graphics2D g, Rectangle rect, boolean focused, boolean primary) {
        g.setColor(focused ? (primary ? GOLD : HOVER) : BUTTON);
        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        g.setColor(focused ? GOLD : LINE);
        g.drawRect(rect.x, rect.y, rect.width - 1, rect.height - 1);
        if (focused && !primary) {
            g.fillRect(rect.x, rect.y, 2, rect.height);
        }
    }

    private void hint(Graphics2D g, String text) {
        g.setColor(MUTED);
        g.setFont(body.deriveFont(15.0f));
        g.drawString(text, 65, 503);
    }

    private static void rightText(Graphics2D g, String text, int x, int y) {
        g.drawString(text, x - g.getFontMetrics().stringWidth(text), y);
    }

    @Override
    public void close() {
        glDeleteTextures(texture);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
