package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

public final class AxeDemo {
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Informe apenas o caminho de um arquivo OBJ");
        }
        Path customModel = args.length == 1 ? Path.of(args[0]).toAbsolutePath().normalize() : null;
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err).set();
        try {
            if (!glfwInit()) {
                throw new IllegalStateException("Falha ao inicializar GLFW");
            }
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            long window = glfwCreateWindow(960, 540, "Wildgrass", 0, 0);
            if (window == 0) {
                throw new IllegalStateException("Falha ao criar uma janela OpenGL 3.3");
            }
            try {
                glfwMakeContextCurrent(window);
                glfwSetWindowSizeLimits(window, 640, 360, GLFW_DONT_CARE, GLFW_DONT_CARE);
                glfwSwapInterval(1);
                GL.createCapabilities();
                run(window, customModel);
            } finally {
                glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
                GL.setCapabilities(null);
            }
        } finally {
            glfwTerminate();
            glfwSetErrorCallback(null);
            errorCallback.free();
        }
    }

    private static void run(long window, Path customModel) throws IOException {
        var settings = new GameSettings(GameSettings.defaultPath());
        try {
            run(window, customModel, settings);
        } finally {
            settings.save();
        }
    }

    private static void run(long window, Path customModel, GameSettings settings) throws IOException {
        try (var framebuffer = new PixelFramebuffer();
             var shader = new SceneShader();
             var sky = new Sky();
             var overlay = new MenuOverlay();
             var terrain = Terrain.mesh();
             var meadow = new Meadow();
             var tree = loadModel("tree.obj", null);
             var leaves = Foliage.mesh();
             var birdBody = loadModel("bird-body.obj", null);
             var birdWing = loadModel("bird-wing.obj", null);
             var bowMesh = loadModel("bow.obj", null);
             var stringMesh = loadModel("bow-string.obj", null);
             var arrowMesh = loadModel("arrow.obj", null);
             var stoneMesh = loadModel("throw-stone.obj", null);
             var torchMesh = loadModel("torch.obj", null);
             var marker = loadModel("bow-marker.obj", null);
             var dynamiteMesh = loadModel("dynamite.obj", null);
             var blastRing = loadModel("blast-ring.obj", null);
             var campfireMesh = loadModel("campfire.obj", null);
             var remotePlayerMesh = loadModel("remote-player.obj", null);
             var stump = loadModel("stump.obj", null);
             var scar = loadModel("tree-cut.obj", null);
             var log = loadModel("log.obj", null);
             var chip = loadModel("wood-chip.obj", null);
             var dust = loadModel("dust.obj", null);
             var grassFragment = loadModel("grass-fragment.obj", null);
             var axe = loadModel("axe.obj", customModel);
             var macheteMesh = loadModel("machete.obj", null);
             var macheteTrail = loadModel("machete-trail.obj", null);
             var lighterBody = loadModel("lighter-body.obj", null);
             var lighterLid = loadModel("lighter-lid.obj", null);
             var flame = loadModel("flame.obj", null);
             var smoke = loadModel("smoke.obj", null);
             var swingSound = new SoundEffect("axe-swing.wav");
             var hitSound = new SoundEffect("axe-wood-hit.wav");
             var splitSound = new SoundEffect("tree-split.wav");
             var landSound = new SoundEffect("tree-land.wav");
             var pickupSound = new SoundEffect("wood-pickup.wav");
             var lighterSound = new SoundEffect("lighter-click.wav");
             var ignitionSound = new SoundEffect("wood-ignite.wav");
             var windSound = new SoundEffect("prairie-wind.wav");
             var fireSound = new SoundEffect("grass-fire.wav");
             var slashSound = new SoundEffect("machete-swing.wav");
             var cutSound = new SoundEffect("grass-cut.wav");
             var rustleSound = new SoundEffect("grass-step.wav");
             var birdsong = new Birdsong();
             var bowReady = new SoundEffect("bow-ready.wav");
             var bowDraw = new SoundEffect("bow-draw.wav");
             var bowShot = new SoundEffect("bow-shot.wav");
             var projectileHit = new SoundEffect("projectile-hit.wav");
             var dynamiteToss = new SoundEffect("dynamite-throw.wav");
             var dynamiteFuse = new SoundEffect("dynamite-fuse.wav");
             var dynamiteBoom = new SoundEffect("dynamite-blast.wav");
             var campfirePlace = new SoundEffect("campfire-place.wav");
             var network = new NetworkManager();
             var stack = MemoryStack.stackPush()) {
            var camera = new FirstPersonCamera();
            var swing = new AxeSwing();
            var machete = new MacheteSwing();
            var bow = new Bow();
            var torch = new Torch();
            var projectiles = new Projectiles();
            var dynamite = new Dynamite();
            var blast = new BlastEffects();
            var campfires = new Campfires();
            var campfireRequest = new boolean[1];
            var stoneRequest = new boolean[1];
            var wind = new Wind();
            var birds = new BirdFlocks();
            var lighter = new Lighter();
            var ignition = new IgnitionAim();
            var fire = new FireVisuals(flame, smoke);
            var chips = new WoodChips();
            var motion = new ImpactMotion();
            var forest = new Forest();
            var inventory = new Inventory();
            var menu = new GameMenu(settings, inventory);
            var remotePlayer = new RemotePlayer();
            long seenRemoteRevision = 0;
            var impact = new Vector3f();
            var previewEye = new Vector3f(-3.8f, 2.05f, 8.45f);
            var previewAim = new Vector3f(-0.16f, -0.09f, -1.0f).normalize();
            camera.attach(window, inventory, menu, () -> {
                if (inventory.hasAxeEquipped()) {
                    swing.start();
                } else if (inventory.hasLighterEquipped()) {
                    lighter.toggle();
                    lighterSound.play();
                } else if (inventory.hasMacheteEquipped() && machete.start()) {
                    slashSound.play();
                } else if (inventory.equipped() == Inventory.Item.BOW) {
                    if (bow.press(inventory)) bowDraw.play();
                } else if (inventory.equipped() == Inventory.Item.TORCH) {
                    torch.toggle();
                    if (torch.lit()) ignitionSound.play();
                } else if (inventory.equipped() == Inventory.Item.STONE) {
                    stoneRequest[0] = true;
                } else if (inventory.equipped() == Inventory.Item.DYNAMITE) {
                    dynamite.start();
                } else if (inventory.equipped() == Inventory.Item.CAMPFIRE) {
                    campfireRequest[0] = true;
                }
            }, () -> {
                if (bow.drawing()) {
                    bowDraw.stop();
                    bow.release();
                }
            });
            var windowWidth = stack.mallocInt(1);
            var windowHeight = stack.mallocInt(1);
            var projection = new Matrix4f();
            var itemProjection = new Matrix4f();
            var viewProjection = new Matrix4f();
            var view = new Matrix4f();
            var heldPose = new Matrix4f();
            var model = new Matrix4f();
            var buffer = stack.mallocFloat(16);
            double previous = glfwGetTime();
            float ambientTimer = 0.0f;
            float stepTimer = 0.0f;
            int volume = settings.get(GameSettings.Option.VOLUME);
            int vsync = settings.get(GameSettings.Option.VSYNC);
            SoundEffect[] sounds = {swingSound, hitSound, splitSound, landSound, pickupSound, lighterSound,
                    ignitionSound, windSound, fireSound, slashSound, cutSound, rustleSound, bowReady, bowDraw, bowShot,
                    projectileHit, dynamiteToss, dynamiteFuse, dynamiteBoom, campfirePlace};
            for (SoundEffect sound : sounds) {
                sound.masterVolume(volume);
            }
            birdsong.masterVolume(volume);
            glfwSwapInterval(vsync);
            windSound.loop(-22.0f);
            fireSound.loop(-75.0f);

            glEnable(GL_DEPTH_TEST);
            while (!glfwWindowShouldClose(window)) {
                glfwPollEvents();
                if (menu.quitRequested() || glfwWindowShouldClose(window)) {
                    break;
                }
                int desiredVolume = settings.get(GameSettings.Option.VOLUME);
                if (volume != desiredVolume) {
                    volume = desiredVolume;
                    for (SoundEffect sound : sounds) {
                        sound.masterVolume(volume);
                    }
                    birdsong.masterVolume(volume);
                }
                int desiredVsync = settings.get(GameSettings.Option.VSYNC);
                if (vsync != desiredVsync) {
                    vsync = desiredVsync;
                    glfwSwapInterval(vsync);
                }
                double now = glfwGetTime();
                float delta = (float) Math.min(now - previous, 0.05);
                previous = now;
                switch (menu.consumeNetworkAction()) {
                    case HOST -> {
                        try {
                            if (network.mode() != NetworkManager.Mode.HOST) network.startHost(NetworkManager.DEFAULT_PORT);
                            menu.networkMessage(network.message());
                            menu.networkHostStarted();
                            camera.refreshCursor(window, menu);
                        } catch (IOException | IllegalArgumentException error) {
                            menu.networkMessage("Não foi possível abrir UDP 7777: " + error.getMessage());
                        }
                    }
                    case CONNECT -> {
                        try {
                            network.connectToHost(menu.ipAddress(), NetworkManager.DEFAULT_PORT);
                            menu.networkMessage(network.message());
                        } catch (IOException | IllegalArgumentException error) {
                            menu.networkMessage(error.getMessage());
                        }
                    }
                    case CANCEL -> {
                        if (network.mode() == NetworkManager.Mode.CLIENT && !network.connected()) network.close();
                    }
                    case DISCONNECT -> {
                        network.close();
                        menu.networkDisconnected("Você saiu da sessão.");
                        camera.refreshCursor(window, menu);
                    }
                    case NONE -> { }
                }
                NetworkManager.Mode previousMode = network.mode();
                network.update(delta, camera.position());
                if (previousMode == NetworkManager.Mode.CLIENT && network.mode() == NetworkManager.Mode.OFFLINE) {
                    menu.networkDisconnected(network.message());
                    camera.refreshCursor(window, menu);
                } else if (network.mode() == NetworkManager.Mode.CLIENT && network.connected()
                        && menu.screen() == GameMenu.Screen.CONNECT) {
                    camera.position().set(2.25f, Terrain.height(2.25f, 4.65f) + 1.53f, 4.65f);
                    menu.networkConnected();
                    camera.refreshCursor(window, menu);
                } else if (network.mode() != NetworkManager.Mode.OFFLINE) {
                    menu.networkMessage(network.message());
                }
                if (network.hasRemote()) {
                    if (network.remoteRevision() != seenRemoteRevision) {
                        remotePlayer.receive(network.remotePosition());
                        seenRemoteRevision = network.remoteRevision();
                    }
                } else {
                    remotePlayer.hide();
                    seenRemoteRevision = 0;
                }
                remotePlayer.update(delta);
                glfwGetFramebufferSize(window, windowWidth, windowHeight);
                int width = windowWidth.get(0);
                int height = windowHeight.get(0);
                if (width == 0 || height == 0) {
                    glfwWaitEventsTimeout(0.1);
                    continue;
                }
                boolean playing = !menu.visible();
                float traveled = playing ? camera.update(window, delta, forest) : 0.0f;
                boolean sprinting = camera.sprinting();
                camera.view(view);
                Vector3f eye = camera.position();
                Vector3f aim = camera.aim();
                if (menu.titleBackdrop()) {
                    eye = previewEye;
                    aim = previewAim;
                    view.identity().lookAlong(aim.x, aim.y, aim.z, 0.0f, 1.0f, 0.0f).translate(-eye.x, -eye.y, -eye.z);
                }
                wind.update(delta);
                birds.update(delta);
                birdsong.update(delta, birds, eye, aim, menu.visible());
                boolean axeEquipped = inventory.hasAxeEquipped();
                boolean lighterEquipped = inventory.hasLighterEquipped();
                boolean macheteEquipped = inventory.hasMacheteEquipped();
                boolean bowEquipped = inventory.equipped() == Inventory.Item.BOW;
                boolean dynamiteEquipped = inventory.equipped() == Inventory.Item.DYNAMITE;
                boolean campfireEquipped = inventory.equipped() == Inventory.Item.CAMPFIRE;
                boolean torchEquipped = inventory.equipped() == Inventory.Item.TORCH;
                if (!playing || !bowEquipped) {
                    if (bow.drawing()) bowDraw.stop();
                    bow.cancel();
                }
                if (!playing || !dynamiteEquipped) dynamite.cancel();
                if (playing) {
                    bow.updateAim(bowEquipped && glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS, delta);
                } else {
                    bow.resetAim();
                }
                float zoomFov = bow.fieldOfView(0.0f);
                camera.sensitivityScale((float) (Math.tan(zoomFov * 0.5f) / Math.tan(Sky.FOV * 0.5f)));
                if (!torchEquipped) torch.stow();
                if (playing) {
                    bow.update(delta, inventory, eye, aim, projectiles, bowReady, bowShot);
                    dynamite.updateThrow(delta, inventory, eye, aim, dynamiteToss, dynamiteFuse);
                    dynamite.update(delta, projectiles, forest, eye, meadow, blast, motion, dynamiteBoom);
                    blast.update(delta);
                    campfires.update(delta);
                    if (campfireRequest[0] && campfires.place(eye, aim, forest, meadow, inventory)) {
                        campfirePlace.play();
                    }
                    campfireRequest[0] = false;
                    torch.update(delta);
                    if (stoneRequest[0] && inventory.takeSelected(Inventory.Item.STONE)) {
                        projectiles.launch(Inventory.Item.STONE, eye, aim, 12.0f);
                        slashSound.play();
                    }
                    stoneRequest[0] = false;
                    meadow.update(delta, wind.strength());
                    if (!axeEquipped) {
                        swing.cancel();
                    }
                    if (swing.update(delta)) {
                        swingSound.play();
                    }
                    chips.update(delta);
                    motion.update(delta);
                    motion.stride(traveled, sprinting, delta);
                    if (swing.reachedImpact()) {
                        TreeTarget hit = forest.chop(eye, aim, impact);
                        if (hit != null) {
                            hitSound.play();
                            chips.burst(impact, hit.center());
                            motion.hit();
                            if (hit.isFalling()) {
                                splitSound.play();
                            }
                        }
                    }
                    if (!macheteEquipped) {
                        machete.cancel();
                    }
                    if (machete.update(delta) && meadow.cut(eye, aim, chips)) {
                        cutSound.play();
                        motion.cut();
                    }
                    forest.update(delta, meadow, chips, motion, landSound);
                    if (lighterEquipped) {
                        lighter.update(delta);
                    } else {
                        lighter.stow();
                    }
                    if (lighter.lit() || torch.lit()) {
                        if (camera.ignite(ignition, forest, meadow, campfires, delta)) {
                            ignitionSound.play();
                        }
                    } else {
                        ignition.reset();
                    }
                    if (forest.collect(delta, eye, inventory)
                            | projectiles.update(delta, forest, eye, inventory, projectileHit)) {
                        pickupSound.play();
                    }
                    stepTimer = Math.max(0.0f, stepTimer - delta);
                    if (traveled > 0.0f && stepTimer == 0.0f && meadow.tallAt(eye.x, eye.z)) {
                        rustleSound.play();
                        stepTimer = sprinting ? 0.25f : 0.37f;
                    }
                }
                if (!playing) {
                    stoneRequest[0] = false;
                    campfireRequest[0] = false;
                }
                ambientTimer -= delta;
                if (ambientTimer <= 0.0f) {
                    windSound.volume(-22.0f + wind.strength() * 11.0f);
                    float nearbyFire = Math.max(meadow.fireProximity(eye), campfires.proximity(eye));
                    fireSound.volume(nearbyFire > 0.03f ? -45.0f + nearbyFire * 34.0f : -75.0f);
                    ambientTimer = 0.12f;
                }
                float shake = settings.fraction(GameSettings.Option.SHAKE);
                if (playing) {
                    motion.shakeView(view, shake);
                    if (bowEquipped) bow.reactView(view, shake);
                }

                framebuffer.begin(width, height, settings.renderHeight());
                glClearColor(0.55f, 0.69f, 0.70f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                float aspect = framebuffer.aspectRatio();
                float fov = playing && bowEquipped ? bow.fieldOfView(shake) : zoomFov;
                sky.draw(view, aspect, wind.time(), fov, buffer);
                projection.setPerspective(fov, aspect, 0.05f, 60);
                shader.begin(projection.mul(view, viewProjection), buffer);
                shader.setWind(wind.time(), wind.strength(), eye.x, eye.z);
                fire.beginLights();
                blast.lights(fire);
                campfires.lights(fire, eye);
                if (playing && torch.lit()) fire.heldTorchLight(eye, aim, wind.time());
                forest.fireLights(fire, eye);
                meadow.fireLights(fire, eye);
                fire.applyLights(shader);
                forest.applyShadows(shader, eye);
                blast.ground(shader);
                shader.draw(terrain, model.identity(), true, buffer);
                meadow.draw(shader, eye, aim, buffer);
                forest.draw(shader, tree, leaves, stump, scar, log, fire, eye, aim, buffer);
                remotePlayer.draw(shader, remotePlayerMesh, buffer);
                birds.draw(shader, birdBody, birdWing, buffer);
                chips.draw(shader, chip, dust, grassFragment, buffer);
                meadow.drawFire(shader, fire, eye, aim, buffer);
                campfires.draw(shader, campfireMesh, fire, buffer);
                projectiles.draw(shader, arrowMesh, stoneMesh, buffer);
                dynamite.drawThrown(shader, dynamiteMesh, marker, buffer);
                blast.draw(shader, marker, stoneMesh, smoke, blastRing, buffer);
                if (playing && bowEquipped && bow.drawing()) {
                    projectiles.preview(shader, marker, forest, eye, aim, bow.speed(), buffer);
                }
                if (playing && dynamiteEquipped && glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) {
                    dynamite.preview(shader, marker, forest, projectiles, eye, aim, buffer);
                }
                if (playing && campfireEquipped) {
                    campfires.preview(shader, campfireMesh, eye, aim, forest, buffer);
                }

                // Os itens equipados são desenhados à frente do cenário, no espaço da câmera.
                if (playing && (axeEquipped || lighterEquipped || macheteEquipped || bowEquipped
                        || torchEquipped || dynamiteEquipped || campfireEquipped
                        || inventory.equipped() == Inventory.Item.STONE)) {
                    glClear(GL_DEPTH_BUFFER_BIT);
                    float bob = settings.fraction(GameSettings.Option.ITEM_BOB);
                    if (bowEquipped) bob *= 1.0f - 0.77f * bow.aimAmount();
                    motion.heldPose(heldPose, bob, shake);
                    itemProjection.setPerspective(Sky.FOV, aspect, 0.05f, 60);
                    shader.begin(itemProjection.mul(heldPose, viewProjection), buffer);
                    shader.clearFireLights();
                    if (axeEquipped) {
                        swing.transform(model, aspect);
                        motion.recoilAxe(model);
                        shader.draw(axe, model, false, buffer);
                    } else if (lighterEquipped) {
                        lighter.draw(shader, lighterBody, lighterLid, fire, aspect, buffer);
                    } else if (macheteEquipped) {
                        machete.drawTrail(shader, macheteTrail, aspect, buffer);
                        machete.transform(model, aspect);
                        motion.recoilAxe(model);
                        shader.draw(macheteMesh, model, false, buffer);
                    } else if (bowEquipped) {
                        bow.draw(shader, bowMesh, stringMesh, arrowMesh, aspect, buffer);
                    } else if (torchEquipped) {
                        torch.draw(shader, torchMesh, fire, aspect, buffer);
                    } else if (dynamiteEquipped) {
                        dynamite.drawHeld(shader, dynamiteMesh, marker, aspect, buffer);
                    } else if (campfireEquipped) {
                        campfires.drawHeld(shader, campfireMesh, aspect, buffer);
                    } else {
                        model.translation(0.13f * aspect, -0.36f, -0.62f).rotateY(-0.28f).scale(0.15f);
                        shader.draw(stoneMesh, model, false, buffer);
                    }
                }
                if (playing) {
                    framebuffer.drawCrosshair();
                    if (bowEquipped) framebuffer.drawBowIndicator(bow.charge(), bow.drawing(), bow.aimAmount(),
                            inventory.total(Inventory.Item.ARROW));
                    framebuffer.drawHotbar(inventory, delta);
                }
                framebuffer.present(width, height);
                if (!playing) {
                    overlay.draw(menu, width, height);
                }
                glfwSwapBuffers(window);
            }
        }
    }

    private static ObjModel loadModel(String bundledName, Path customPath) throws IOException {
        if (customPath != null) {
            try (var reader = Files.newBufferedReader(customPath)) {
                return ObjModel.read(reader, name -> Files.newBufferedReader(customPath.getParent().resolve(name)));
            }
        }
        try (var reader = openAsset(bundledName)) {
            return ObjModel.read(reader, AxeDemo::openAsset);
        }
    }

    private static BufferedReader openAsset(String name) throws IOException {
        var stream = AxeDemo.class.getResourceAsStream("/models/" + name);
        if (stream == null) {
            throw new FileNotFoundException(name);
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
