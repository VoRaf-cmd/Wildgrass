package javaengine;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class GameSettings {
    enum Option {
        VOLUME("Volume geral", 0, 100, 5, 100),
        SENSITIVITY("Sensibilidade do mouse", 25, 200, 5, 100),
        SHAKE("Tremor da câmera", 0, 100, 5, 100),
        ITEM_BOB("Balanço dos itens", 0, 100, 5, 100),
        PIXELS("Pixelização", 0, 2, 1, 1),
        VSYNC("VSync", 0, 1, 1, 1);

        final String label;
        final int min;
        final int max;
        final int step;
        final int defaultValue;

        Option(String label, int min, int max, int step, int defaultValue) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.defaultValue = defaultValue;
        }

        String display(int value) {
            return switch (this) {
                case PIXELS -> switch (value) {
                    case 0 -> "Fina";
                    case 1 -> "Clássica";
                    default -> "Forte";
                };
                case VSYNC -> value == 1 ? "Ligado" : "Desligado";
                default -> value + "%";
            };
        }
    }

    static final Option[] OPTIONS = Option.values();
    private final Path file;
    private final int[] values = new int[OPTIONS.length];
    private boolean dirty;

    GameSettings(Path file) {
        this.file = file.toAbsolutePath();
        restoreDefaults();
        load();
        dirty = false;
    }

    static Path defaultPath() {
        String appData = System.getenv("LOCALAPPDATA");
        return appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".wildgrass", "settings.properties")
                : Path.of(appData, "Wildgrass", "settings.properties");
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        var properties = new Properties();
        try (var source = Files.newInputStream(file)) {
            properties.load(source);
            for (Option option : OPTIONS) {
                try {
                    set(option, Integer.parseInt(properties.getProperty(option.name(), "")));
                } catch (NumberFormatException ignored) {
                    // Uma entrada inválida deixa só aquela opção com o valor padrão.
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Não foi possível ler as configurações de Wildgrass: " + e.getMessage());
        }
    }

    int get(Option option) {
        return values[option.ordinal()];
    }

    float fraction(Option option) {
        return get(option) / 100.0f;
    }

    boolean set(Option option, int value) {
        int bounded = Math.clamp(value, option.min, option.max);
        int snapped = option.min + Math.round((bounded - option.min) / (float) option.step) * option.step;
        int index = option.ordinal();
        if (values[index] == snapped) {
            return false;
        }
        values[index] = snapped;
        dirty = true;
        return true;
    }

    void restoreDefaults() {
        for (Option option : OPTIONS) {
            set(option, option.defaultValue);
        }
    }

    int renderHeight() {
        return switch (get(Option.PIXELS)) {
            case 0 -> 270;
            case 2 -> 135;
            default -> 180;
        };
    }

    void save() {
        if (!dirty) {
            return;
        }
        var properties = new Properties();
        for (Option option : OPTIONS) {
            properties.setProperty(option.name(), Integer.toString(get(option)));
        }
        Path temporary = null;
        try {
            Path parent = Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(parent, "settings-", ".tmp");
            try (var target = Files.newOutputStream(temporary)) {
                properties.store(target, "Wildgrass");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException e) {
            System.err.println("Não foi possível salvar as configurações de Wildgrass: " + e.getMessage());
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A falha de gravação já foi registrada.
                }
            }
        }
    }
}
