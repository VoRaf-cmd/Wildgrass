package javaengine;

import static org.lwjgl.opengl.GL33C.*;

final class PixelFramebuffer implements AutoCloseable {
    private static final int TARGET_HEIGHT = 180;
    private final int framebuffer = glGenFramebuffers();
    private final int color = glGenTextures();
    private final int depth = glGenRenderbuffers();
    private final Hotbar hotbar = new Hotbar();
    private int width;
    private int height;
    private int scale;

    void begin(int windowWidth, int windowHeight) {
        begin(windowWidth, windowHeight, TARGET_HEIGHT);
    }

    void begin(int windowWidth, int windowHeight, int targetHeight) {
        scale = Math.min(Math.min(windowWidth, windowHeight),
                Math.max(1, Math.round((float) windowHeight / targetHeight)));
        int nextWidth = windowWidth / scale;
        int nextHeight = windowHeight / scale;

        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        if (width != nextWidth || height != nextHeight) {
            width = nextWidth;
            height = nextHeight;
            glBindTexture(GL_TEXTURE_2D, color);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindRenderbuffer(GL_RENDERBUFFER, depth);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Framebuffer de baixa resolução incompleto");
            }
        }
        glViewport(0, 0, width, height);
    }

    float aspectRatio() {
        return (float) width / height;
    }

    void drawCrosshair() {
        glEnable(GL_SCISSOR_TEST);
        glScissor(width / 2 - 1, height / 2 - 1, 3, 3);
        glClearColor(0.10f, 0.12f, 0.095f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glScissor(width / 2, height / 2, 1, 1);
        glClearColor(0.86f, 0.82f, 0.68f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
    }

    void drawHotbar(Inventory inventory, float delta) {
        hotbar.draw(width, height, inventory, delta);
    }

    void drawRanchIndicator(float status) {
        if (status < 0) return;
        int x = width / 2;
        int y = height / 2 + 9;
        glEnable(GL_SCISSOR_TEST);
        glClearColor(.10f, .12f, .095f, 1);
        glScissor(x - 11, y, 22, 7);
        glClear(GL_COLOR_BUFFER_BIT);
        glClearColor(.98f, .79f, .43f, 1);
        if (status >= 1) {
            glScissor(x - 1, y + 1, 1, 5);
            glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x, y + 5, 2, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x, y + 3, 1, 1);
        } else {
            glScissor(x - 10, y + 2, Math.max(1, Math.round(status * 20)), 3);
        }
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
    }

    void drawBowIndicator(float charge, boolean drawing, float aim, int arrows) {
        glEnable(GL_SCISSOR_TEST);
        int x = width / 2;
        int y = height / 2 - 13;
        int color = arrows == 0 ? 0xF58D76 : charge >= 1.0f ? 0xFFD777 : 0xEADFC1;
        if (aim > 0.16f) {
            int centerY = height / 2;
            int spread = 3 + Math.round(4.0f * (1.0f - aim));
            boolean charged = charge >= 1.0f;
            glClearColor(charged ? 1.0f : 0.88f, charged ? 0.77f : 0.81f, charged ? 0.36f : 0.63f, 1.0f);
            glScissor(x - spread - 2, centerY, 2, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x + spread + 1, centerY, 2, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glScissor(x, centerY + spread + 1, 1, 2);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        if (drawing) {
            glScissor(x - 12, y, 24, 3);
            glClearColor(0.10f, 0.12f, 0.095f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            int completed = Math.round(charge * 22.0f);
            if (completed > 0) {
                glScissor(x - 11, y + 1, completed, 1);
                glClearColor(1.0f, charge >= 1.0f ? 0.79f : 0.93f, 0.44f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT);
            }
        }
        String count = Integer.toString(arrows);
        for (int i = 0; i < count.length(); i++) {
            Hotbar.digit(count.charAt(i) - '0', x + 10 + i * 4, height / 2 + 1, color);
        }
        glDisable(GL_SCISSOR_TEST);
    }

    void present(int windowWidth, int windowHeight) {
        int drawWidth = width * scale;
        int drawHeight = height * scale;
        int x = (windowWidth - drawWidth) / 2;
        int y = (windowHeight - drawHeight) / 2;

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glBlitFramebuffer(0, 0, width, height, x, y, x + drawWidth, y + drawHeight,
                GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    }

    @Override
    public void close() {
        glDeleteFramebuffers(framebuffer);
        glDeleteRenderbuffers(depth);
        glDeleteTextures(color);
    }
}
