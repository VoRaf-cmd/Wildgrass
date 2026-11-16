package javaengine;

import org.joml.Intersectionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Random;

import static javaengine.Inventory.Item.*;
import static javaengine.NetworkPacket.Kind.*;

/** The host owns the herd, forage, and all meat outside player inventories. */
final class Ranch {
    private static final int COW_BYTES = 18;
    private static final int IDLE = 0, WALK = 1, GRAZE = 2, FLEE = 3, DEAD = 4;
    private static final int MOO = 1, BITE = 2, HURT = 3, FALL = 4;
    private final Cow[] cows = {new Cow(-4.9f, .1f, 0), new Cow(5.2f, .1f, 1),
            new Cow(7.1f, -5.0f, 0), new Cow(-7.5f, -6.6f, 1)};
    private final Random random = new Random(20181125);
    private final Vector3f relative = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f effect = new Vector3f();
    private final Vector3f zero = new Vector3f();
    private final Vector2f ray = new Vector2f();
    private final ByteBuffer state = ByteBuffer.allocate(NetworkPacket.MAX_BYTES - 18);
    private final ArrayDeque<Inventory.Item> deliveries = new ArrayDeque<>();
    private float broadcast;
    private float time;
    private boolean cookPending;
    private boolean clientHasState;

    Cow[] cows() { return cows; }
    void disconnected() { cookPending = false; }

    void update(float delta, boolean running, NetworkManager network, Meadow meadow, Forest forest, Campfires campfires,
                Inventory inventory, Vector3f eye, WoodChips chips, SoundEffect moo, SoundEffect alarm,
                SoundEffect chew, SoundEffect steps, SoundEffect pickup) {
        boolean authority = network.mode() != NetworkManager.Mode.CLIENT;
        if (running || network.connected()) time += delta;
        if (authority) {
            if (clientHasState) {
                clientHasState = false;
                for (Cow cow : cows) cow.syncPose();
            }
            if (running || network.connected()) {
                meadow.regrow(delta);
                for (Cow cow : cows) think(cow, delta, meadow, forest);
            }
            if (network.connected()) {
                broadcast -= delta;
                if (broadcast <= 0) {
                    broadcast = .1f;
                    network.sendRanch(writeState(meadow, campfires));
                }
            }
        } else {
            byte[] next = network.pollRanch();
            if (next != null) readState(next, meadow, campfires);
        }
        while (!deliveries.isEmpty() && inventory.add(deliveries.getFirst())) {
            deliveries.removeFirst();
            pickup.play();
        }
        for (Cow cow : cows) {
            cow.animation = time;
            float blend = 1 - (float) Math.exp(-delta * 13);
            if (authority) cow.syncPose();
            else {
                cow.display.lerp(cow.position, blend);
                cow.displayYaw += (float) Math.IEEEremainder(cow.yaw - cow.displayYaw, Math.PI * 2) * blend;
            }
            if (!running && !network.connected()) continue;
            cow.headDown += ((cow.state == GRAZE ? 1 : 0) - cow.headDown) * blend;
            cow.fall = cow.state == DEAD ? Math.min(1, cow.fall + delta * 2.1f) : 0;
            cow.flinch = Math.max(0, cow.flinch - delta * 3.9f);
            float speed = cow.state == FLEE ? 2.6f : cow.state == WALK ? .85f : 0;
            cow.walk += ((speed > 0 ? 1 : 0) - cow.walk) * blend;
            int previousStep = (int) (cow.phase / Math.PI);
            cow.phase += speed * delta * 7.3f;
            if (speed > 0 && (int) (cow.phase / Math.PI) != previousStep && cow.display.distanceSquared(eye) < 196) {
                project(cow, 0, (previousStep & 1) == 0 ? -.53f : .55f);
                chips.hoofDust(effect, cow.state == FLEE);
                play(steps, effect, eye, 10);
            }
            if (cow.seenEvent != cow.event) {
                cow.seenEvent = cow.event;
                emit(cow, eye, chips, moo, alarm, chew);
            }
        }
    }

    private void think(Cow cow, float delta, Meadow meadow, Forest forest) {
        if (cow.state == DEAD) return;
        cow.hunger = Math.min(1, cow.hunger + delta * .0125f);
        if (cow.hunger >= .999f) {
            cow.starvation += delta;
            if (cow.starvation >= 2) {
                cow.starvation = 0;
                cow.health = Math.max(0, cow.health - 5);
                if (cow.health == 0) {
                    die(cow);
                    return;
                }
            }
        } else if (cow.hunger < .25f) {
            cow.health = Math.min(100, cow.health + delta * 1.6f);
        }

        cow.timer -= delta;
        cow.callTimer -= delta;
        if (cow.callTimer <= 0 && cow.state != FLEE && cow.state != GRAZE) {
            cow.callTimer = random.nextFloat(19, 41);
            cow.event(MOO);
        }
        if (cow.state == FLEE) {
            walk(cow, delta, 2.65f, forest);
            if (cow.timer <= 0) cow.idle(.85f);
            return;
        }
        if (cow.state == GRAZE) {
            if (cow.timer <= 0) {
                if (meadow.graze(cow.target.x, cow.target.z)) {
                    cow.hunger = Math.max(0, cow.hunger - .47f);
                    cow.event(BITE);
                }
                cow.idle(1.2f);
            }
            return;
        }
        if (cow.state == IDLE && cow.timer <= 0) {
            cow.seeking = cow.hunger >= .46f && meadow.findGrazing(cow.position, forest, cow.target);
            if (!cow.seeking) {
                cow.target.set(cow.position).add(random.nextFloat(-4, 4), 0, random.nextFloat(-4, 4));
            }
            cow.state = WALK;
            cow.timer = cow.seeking ? 15 : 7;
        }
        if (cow.state == WALK) {
            float distance = (float) Math.hypot(cow.target.x - cow.position.x, cow.target.z - cow.position.z);
            if (cow.seeking && distance < 1.02f) {
                if (turn(cow, delta) < .25f || distance < .12f) {
                    cow.state = GRAZE;
                    cow.timer = 2.4f;
                }
            } else if (cow.timer <= 0 || distance < .43f) {
                cow.idle(random.nextFloat(2, 5));
            } else {
                walk(cow, delta, .87f, forest);
            }
        }
    }

    private void walk(Cow cow, float delta, float speed, Forest forest) {
        turn(cow, delta);
        for (int i = 0; i < 3; i++) {
            float testYaw = cow.yaw + (i == 1 ? .96f : i == 2 ? -.96f : 0);
            float x = Math.clamp(cow.position.x + speed * delta * (float) Math.sin(testYaw), -21.5f, 21.5f);
            float z = Math.clamp(cow.position.z - speed * delta * (float) Math.cos(testYaw), -21.5f, 21.5f);
            if (!forest.blocksPlayer(x, z) && !herdCrowded(cow, x, z)) {
                cow.position.set(x, Terrain.height(x, z), z);
                if (i > 0) cow.yaw = (float) Math.IEEEremainder(testYaw, Math.PI * 2);
                return;
            }
        }
        if (cow.state != FLEE) cow.idle(.75f);
    }

    private static float turn(Cow cow, float delta) {
        float targetYaw = (float) Math.atan2(cow.target.x - cow.position.x, cow.position.z - cow.target.z);
        float turn = (float) Math.IEEEremainder(targetYaw - cow.yaw, Math.PI * 2);
        cow.yaw = (float) Math.IEEEremainder(cow.yaw + Math.clamp(turn, -delta * 3.7f, delta * 3.7f), Math.PI * 2);
        return Math.abs(turn);
    }

    private boolean herdCrowded(Cow self, float x, float z) {
        for (Cow cow : cows) {
            if (cow == self || cow.state == DEAD) continue;
            float dx = x - cow.position.x, dz = z - cow.position.z;
            if (dx * dx + dz * dz < .85f * .85f) return true;
        }
        return false;
    }

    private void project(Cow cow, float height, float forward) {
        effect.set(cow.display).add((float) Math.sin(cow.displayYaw) * forward, height,
                -(float) Math.cos(cow.displayYaw) * forward);
    }

    private void emit(Cow cow, Vector3f ear, WoodChips chips, SoundEffect moo, SoundEffect alarm, SoundEffect chew) {
        if (cow.eventType == BITE) {
            project(cow, .34f, 1.16f);
            direction.set((float) Math.sin(cow.displayYaw), 0, -(float) Math.cos(cow.displayYaw));
            chips.graze(effect, direction);
            play(chew, effect, ear, 4);
        } else if (cow.eventType == HURT || cow.eventType == FALL) {
            cow.flinch = 1;
            project(cow, .83f, -.09f);
            chips.animalHit(effect, cow.eventType == FALL);
            if (cow.eventType == FALL) {
                project(cow, 0, 0);
                chips.hoofDust(effect, true);
            }
            play(alarm, effect, ear, 0);
        } else if (cow.eventType == MOO) {
            play(moo, cow.display, ear, 1);
        }
    }

    private static void play(SoundEffect sound, Vector3f source, Vector3f ear, float quiet) {
        float distance = source.distance(ear);
        if (distance > 24) return;
        sound.volume(-quiet - Math.min(32, distance * 1.65f));
        sound.play();
    }

    boolean hitRay(Vector3f eye, Vector3f aim, float reach, float damage, boolean authority) {
        Cow nearest = null;
        for (Cow cow : cows) {
            if (cow.state == DEAD) continue;
            Vector3f base = authority ? cow.position : cow.display;
            float yaw = authority ? cow.yaw : cow.displayYaw;
            relative.set(eye).sub(base).rotateY(yaw);
            direction.set(aim).rotateY(yaw);
            if (Intersectionf.intersectRayAab(relative.x, relative.y, relative.z,
                    direction.x, direction.y, direction.z, -.48f, .10f, -1.23f, .48f, 1.48f, 1.0f, ray)
                    && ray.y >= 0 && ray.x < reach) {
                reach = Math.max(0, ray.x);
                nearest = cow;
            }
        }
        if (nearest == null) return false;
        if (authority) hurt(nearest, damage, eye);
        return true;
    }

    void blast(Vector3f point) {
        for (Cow cow : cows) {
            float distance = cow.position.distance(point);
            if (cow.state != DEAD && distance < 4.2f) hurt(cow, 117 - distance * 21, point);
        }
    }

    private static void hurt(Cow cow, float damage, Vector3f source) {
        cow.health = Math.max(0, cow.health - damage);
        if (cow.health == 0) {
            die(cow);
        } else {
            float angle = (float) Math.atan2(cow.position.x - source.x, source.z - cow.position.z);
            cow.target.set(cow.position).add((float) Math.sin(angle) * 11, 0, -(float) Math.cos(angle) * 11);
            cow.state = FLEE;
            cow.timer = 4.1f;
            cow.event(HURT);
        }
    }

    private static void die(Cow cow) {
        cow.state = DEAD;
        cow.meat = 3;
        cow.event(FALL);
    }

    void interact(NetworkManager network, Inventory inventory, Campfires fires, Vector3f eye, Vector3f aim,
                  SoundEffect pickup) {
        var camp = fires.aimedAt(eye, aim);
        if (network.mode() == NetworkManager.Mode.CLIENT) {
            if (camp != null && camp.ready() && inventory.canAdd(COOKED_BEEF)) {
                network.action(COLLECT_MEAT, eye, aim, 1);
            } else if (camp != null && !cookPending && camp.fire.burning() && camp.cook < 0 && inventory.take(RAW_BEEF)) {
                cookPending = true;
                network.action(COOK, eye, aim, 0);
            } else if (nearMeat(eye) != null && inventory.canAdd(RAW_BEEF)) {
                network.action(COLLECT_MEAT, eye, aim, 0);
            }
        } else if (camp != null && camp.ready()) {
            if (inventory.add(COOKED_BEEF)) {
                camp.cook = -1;
                pickup.play();
            }
        } else if (camp != null && camp.fire.burning() && camp.cook < 0 && inventory.take(RAW_BEEF)) {
            camp.startCooking();
        } else {
            Cow cow = nearMeat(eye);
            if (cow != null && inventory.add(RAW_BEEF)) {
                cow.meat--;
                pickup.play();
            }
        }
    }

    private Cow nearMeat(Vector3f eye) {
        float best = 1.65f * 1.65f;
        Cow result = null;
        for (Cow cow : cows) {
            if (cow.meat <= 0) continue;
            float dx = cow.position.x - eye.x, dz = cow.position.z - eye.z;
            float distance = dx * dx + dz * dz;
            if (distance < best) { best = distance; result = cow; }
        }
        return result;
    }

    float indicator(Inventory inventory, Campfires fires, Vector3f eye, Vector3f aim) {
        var camp = fires.aimedAt(eye, aim);
        if (camp != null && camp.cook >= 0) return camp.cook / Campfires.COOK_SECONDS;
        if (camp != null && camp.fire.burning() && inventory.total(RAW_BEEF) > 0 || nearMeat(eye) != null) return 2;
        return -1;
    }

    void receiveAction(NetworkPacket.Action action, NetworkManager network, Campfires campfires,
                       Forest forest, Vector3f eye, Vector3f aim) {
        if (network.mode() == NetworkManager.Mode.CLIENT) {
            if (action.kind() == MEAT_GRANTED) deliveries.add(action.value() == 1 ? COOKED_BEEF : RAW_BEEF);
            if (action.kind() == COOK_RESULT && cookPending) {
                cookPending = false;
                if (action.value() == 0) deliveries.add(RAW_BEEF);
            }
            return;
        }
        if (network.mode() != NetworkManager.Mode.HOST) return;
        boolean valid = network.hasRemote() && network.remotePosition().distanceSquared(eye) < 4.0f;
        switch (action.kind()) {
            case COW_HIT -> {
                if (!valid) return;
                var pose = network.remotePose();
                float damage = pose.item() == AXE.ordinal() ? 45 : pose.item() == MACHETE.ordinal() ? 30 : 0;
                if (damage > 0) hitRay(eye, aim, Math.min(2.65f, forest.projectileDistance(eye, aim, 2.65f)), damage, true);
            }
            case COOK -> {
                var camp = valid ? campfires.aimedAt(eye, aim) : null;
                network.action(COOK_RESULT, zero, null, camp != null && camp.startCooking() ? 1 : 0);
            }
            case COLLECT_MEAT -> {
                if (!valid) return;
                if (action.value() == 1) {
                    var camp = campfires.aimedAt(eye, aim);
                    if (camp != null && camp.ready()) {
                        camp.cook = -1;
                        network.action(MEAT_GRANTED, zero, null, 1);
                    }
                } else {
                    Cow cow = nearMeat(eye);
                    if (cow != null) {
                        cow.meat--;
                        network.action(MEAT_GRANTED, zero, null, 0);
                    }
                }
            }
            default -> { }
        }
    }

    ByteBuffer writeState(Meadow meadow, Campfires campfires) {
        state.clear();
        for (Cow cow : cows) {
            state.putFloat(cow.position.x).putFloat(cow.position.z).putFloat(cow.yaw);
            state.put((byte) Math.round(cow.health)).put((byte) Math.round(cow.hunger * 255))
                    .put((byte) cow.state).put((byte) cow.event).put((byte) cow.eventType).put((byte) cow.meat);
        }
        meadow.writeGrazing(state);
        campfires.writeRanch(state);
        return state.flip();
    }

    void readState(byte[] bytes, Meadow meadow, Campfires campfires) {
        int fireCountOffset = cows.length * COW_BYTES + Meadow.GRAZING_BYTES;
        if (bytes.length <= fireCountOffset) return;
        int fires = Byte.toUnsignedInt(bytes[fireCountOffset]);
        if (fires > 16 || bytes.length != fireCountOffset + 1 + fires * Campfires.SNAPSHOT_FIRE_BYTES) return;
        var data = ByteBuffer.wrap(bytes);
        for (Cow cow : cows) {
            float x = data.getFloat(), z = data.getFloat(), yaw = data.getFloat();
            int health = Byte.toUnsignedInt(data.get()), hunger = Byte.toUnsignedInt(data.get());
            int mode = Byte.toUnsignedInt(data.get()), event = Byte.toUnsignedInt(data.get());
            int type = Byte.toUnsignedInt(data.get()), meat = Byte.toUnsignedInt(data.get());
            if (!(Math.abs(x) <= 22 && Math.abs(z) <= 22 && Math.abs(yaw) <= 3.15f)
                    || health > 100 || mode > DEAD || type > FALL || meat > 3) continue;
            cow.position.set(x, Terrain.height(x, z), z);
            cow.yaw = yaw;
            cow.health = health;
            cow.hunger = hunger / 255f;
            cow.state = mode;
            cow.event = event;
            cow.eventType = type;
            cow.meat = meat;
            if (!clientHasState) {
                cow.syncPose();
                cow.seenEvent = event;
                cow.fall = mode == DEAD ? 1 : 0;
            }
        }
        meadow.readGrazing(data);
        campfires.readRanch(data, meadow);
        clientHasState = true;
    }

    static final class Cow {
        final Vector3f position, display, target = new Vector3f();
        final int coat;
        float health = 100, hunger = .64f, starvation;
        float yaw, displayYaw, timer, callTimer, headDown, walk, phase, fall, flinch, animation;
        int state, event, seenEvent, eventType, meat;
        boolean seeking;

        Cow(float x, float z, int coat) {
            this.coat = coat;
            position = new Vector3f(x, Terrain.height(x, z), z);
            display = new Vector3f(position);
            yaw = displayYaw = coat == 0 ? 2.7f : -2.4f;
            timer = .6f + coat;
            callTimer = 4.5f + Math.abs(x) * 1.2f;
        }

        void event(int type) {
            event = (event + 1) & 255;
            eventType = type;
        }

        void idle(float time) { state = IDLE; timer = time; }
        void syncPose() { display.set(position); displayYaw = yaw; }
    }
}
