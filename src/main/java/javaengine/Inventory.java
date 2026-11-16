package javaengine;

import java.util.Objects;

final class Inventory {
    static final int SLOT_COUNT = 7;
    static final int CAPACITY = 21;
    static final int STACK_LIMIT = 20;

    enum Item {
        AXE("Machado", false), LIGHTER("Isqueiro", false), MACHETE("Facão", false), BOW("Arco", false),
        ARROW("Flecha", true), STONE("Pedra", true), TORCH("Tocha", true), LOG("Tora", true),
        STICK("Graveto", true), FIBER("Fibra seca", true), DYNAMITE("Dinamite", true),
        CAMPFIRE("Fogueira", true), RAW_BEEF("Carne bovina crua", true),
        COOKED_BEEF("Carne bovina cozida", true);

        final String label;
        final boolean stackable;

        Item(String label, boolean stackable) {
            this.label = label;
            this.stackable = stackable;
        }
    }

    private final Item[] items = new Item[CAPACITY];
    private final int[] counts = new int[CAPACITY];
    private int selected;

    Inventory() {
        add(Item.AXE);
        add(Item.LIGHTER);
        add(Item.MACHETE);
        add(Item.BOW);
        for (int i = 0; i < 12; i++) add(Item.ARROW);
        for (int i = 0; i < 6; i++) add(Item.STONE);
        add(Item.TORCH);
        for (int i = 0; i < 8; i++) add(Item.STICK);
        for (int i = 0; i < 5; i++) add(Item.FIBER);
        for (int i = 0; i < 3; i++) add(Item.DYNAMITE);
    }

    boolean add(Item item) {
        Objects.requireNonNull(item);
        int limit = item.stackable ? STACK_LIMIT : 1;
        int emptySlot = -1;
        for (int slot = 0; slot < CAPACITY; slot++) {
            if (items[slot] == item && counts[slot] < limit) {
                counts[slot]++;
                return true;
            }
            if (items[slot] == null && emptySlot == -1) {
                emptySlot = slot;
            }
        }
        if (emptySlot == -1) {
            return false;
        }
        items[emptySlot] = item;
        counts[emptySlot] = 1;
        return true;
    }

    boolean canAdd(Item item) {
        for (int slot = 0; slot < CAPACITY; slot++) {
            if (items[slot] == null || items[slot] == item && item.stackable && counts[slot] < STACK_LIMIT) return true;
        }
        return false;
    }

    void select(int slot) {
        selected = Math.floorMod(slot, SLOT_COUNT);
    }

    int selected() {
        return selected;
    }

    Item item(int slot) {
        return items[slot];
    }

    int count(int slot) {
        return counts[slot];
    }

    Item equipped() {
        return items[selected];
    }

    int total(Item item) {
        int total = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (items[i] == item) total += counts[i];
        }
        return total;
    }

    boolean take(Item item) {
        for (int i = 0; i < CAPACITY; i++) {
            if (items[i] == item) {
                remove(i);
                return true;
            }
        }
        return false;
    }

    boolean takeSelected(Item item) {
        if (items[selected] != item) return false;
        remove(selected);
        return true;
    }

    private void remove(int slot) {
        if (--counts[slot] == 0) items[slot] = null;
    }

    void moveStack(int from, int to) {
        if (from == to || items[from] == null) return;
        if (items[from] == items[to] && items[from].stackable) {
            int moved = Math.min(counts[from], STACK_LIMIT - counts[to]);
            counts[to] += moved;
            counts[from] -= moved;
            if (counts[from] == 0) items[from] = null;
        } else {
            Item saved = items[to];
            int amount = counts[to];
            items[to] = items[from];
            counts[to] = counts[from];
            items[from] = saved;
            counts[from] = amount;
        }
    }

    boolean craft(Item output) {
        if (output == Item.CAMPFIRE) {
            if (total(Item.STICK) < 3 || total(Item.STONE) < 2 || total(Item.FIBER) < 1) return false;
            Item[] previousItems = items.clone();
            int[] previousCounts = counts.clone();
            for (int i = 0; i < 3; i++) take(Item.STICK);
            for (int i = 0; i < 2; i++) take(Item.STONE);
            take(Item.FIBER);
            if (add(output)) return true;
            System.arraycopy(previousItems, 0, items, 0, CAPACITY);
            System.arraycopy(previousCounts, 0, counts, 0, CAPACITY);
            return false;
        }
        Item second = output == Item.ARROW ? Item.STONE : Item.FIBER;
        if ((output != Item.ARROW && output != Item.TORCH) || total(Item.STICK) == 0 || total(second) == 0) {
            return false;
        }
        Item[] previousItems = items.clone();
        int[] previousCounts = counts.clone();
        take(Item.STICK);
        take(second);
        int result = output == Item.ARROW ? 3 : 1;
        for (int i = 0; i < result; i++) {
            if (!add(output)) {
                System.arraycopy(previousItems, 0, items, 0, CAPACITY);
                System.arraycopy(previousCounts, 0, counts, 0, CAPACITY);
                return false;
            }
        }
        return true;
    }

    boolean hasAxeEquipped() {
        return items[selected] == Item.AXE;
    }

    boolean hasLighterEquipped() {
        return items[selected] == Item.LIGHTER;
    }

    boolean hasMacheteEquipped() {
        return items[selected] == Item.MACHETE;
    }
}
