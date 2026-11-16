package dev.horizonlab.platform;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.components.AbstractButton;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** A player-free client screen session while Horizon owns world selection and joining. */
public final class HorizonTitle {
    private HorizonTitle() {}
    private static final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
    private static Screen root, screen;
    private static boolean pause, rootClickReached, rootClickConsumed;
    private static int width, height;
    private static int rootPixelWidth, rootPixelHeight, rootScale;
    private static long tickAt;
    private static net.minecraft.client.DeltaTracker.Timer timer;
    private static boolean mouseWasGrabbed;
    private static boolean clientWasPaused;
    private static boolean clientWasLocal;
    private static Screen logged;
    private static boolean rootKeyReached, rootKeyConsumed;
    private static boolean nativeNavigation;
    private static final Button.OnPress NATIVE_NAVIGATION = ignored -> nativeNavigation = true;
    private static final java.util.Set<String> NATIVE_BUTTONS = java.util.Set.of(
            "menu.singleplayer", "menu.multiplayer", "menu.online", "menu.options",
            "menu.quit", "options.language", "options.accessibility", "title.credits",
            "menu.returnToGame", "gui.advancements", "gui.stats", "menu.sendFeedback",
            "menu.reportBugs", "options.worldOptions.button", "menu.playerReporting",
            "menu.shareToLan", "menu.returnToMenu", "menu.disconnect");
    private static final java.util.Set<Integer> takenButtons = new java.util.HashSet<>();
    private static final java.util.Set<Integer> takenKeys = new java.util.HashSet<>();
    private static final java.util.Map<String, Integer> images = new java.util.HashMap<>();
    private static JsonArray lastOps;
    private record ButtonPosition(Point preferred, Point assigned, int width, int height) {}
    private static final IdentityHashMap<AbstractButton, ButtonPosition> buttonPositions = new IdentityHashMap<>();
    private static List<AbstractButton> laidOutButtons = List.of();
    private static JsonArray laidOutNativeBounds;

    public static boolean active() { return active.get(); }
    public static boolean usesTitleBackground() { return active() && !pause; }
    static net.minecraft.client.DeltaTracker activeTimer() { return active() ? timer : null; }

    private static void closeSession() throws ReflectiveOperationException {
        if (screen != null) {
            HorizonScreenEvents.removed(screen);
            var gui = HorizonClient.minecraft().gui;
            if (gui.screen() == screen) HorizonClient.set(net.minecraft.client.gui.Gui.class, gui, "screen", null);
        }
        if (pause && root != null) {
            HorizonClient.grabMouse(mouseWasGrabbed);
            HorizonClient.set(net.minecraft.client.Minecraft.class, HorizonClient.minecraft(), "pause", clientWasPaused);
            HorizonClient.set(net.minecraft.client.Minecraft.class, HorizonClient.minecraft(), "isLocalServer", clientWasLocal);
        }
        root = screen = logged = null;
        takenKeys.clear();
        takenButtons.clear();
        images.clear();
        lastOps = null;
        buttonPositions.clear();
        laidOutButtons = List.of();
        laidOutNativeBounds = null;
        tickAt = 0;
        timer = null;
        if (pause) HorizonClient.rest();
    }

    static void setScreen(Screen next) throws ReflectiveOperationException {
        if (next == null) next = root;
        if (next == screen) return;
        if (screen != null) HorizonScreenEvents.removed(screen);
        screen = next;
        images.clear();
        lastOps = null;
        HorizonClient.set(net.minecraft.client.gui.Gui.class, HorizonClient.minecraft().gui, "screen", screen);
        if (screen != null) {
            screen.added();
            screen.init(width, height);
        }
    }

    private static boolean nativeButton(Object child) {
        if (!(child instanceof AbstractWidget widget)) {
            String type = child.getClass().getName();
            return type.startsWith("net.minecraft.") || type.startsWith("com.mojang.");
        }
        if (widget.getClass() == net.minecraft.client.gui.components.FriendsButton.class) return true;
        if (!(widget.getMessage().getContents() instanceof TranslatableContents text)) return false;
        return NATIVE_BUTTONS.contains(text.getKey());
    }

    public static boolean drawsNativeWidget(AbstractWidget widget) {
        return active() && screen != null && screen == root
                && nativeButton(widget) && root.children().contains(widget);
    }

    /** Move real widgets so drawing, hover, and hit testing use the same bounds. */
    private static void fitModButtons(JsonArray nativeBounds) {
        if (nativeBounds.equals(laidOutNativeBounds) && buttonLayoutCurrent()) return;
        var occupied = new ArrayList<Rectangle>();
        for (var element : nativeBounds) {
            var bounds = element.getAsJsonArray();
            int x = (int) Math.floor(bounds.get(0).getAsDouble());
            int y = (int) Math.floor(bounds.get(1).getAsDouble());
            int right = (int) Math.ceil(bounds.get(0).getAsDouble() + bounds.get(2).getAsDouble());
            int bottom = (int) Math.ceil(bounds.get(1).getAsDouble() + bounds.get(3).getAsDouble());
            occupied.add(new Rectangle(x, y, right - x, bottom - y));
        }
        var buttons = root.children().stream()
                .filter(child -> child instanceof AbstractButton button && button.visible && !nativeButton(button))
                .map(child -> (AbstractButton) child).toList();
        buttonPositions.keySet().retainAll(buttons);
        for (var button : buttons) {
            var current = new Point(button.getX(), button.getY());
            var old = buttonPositions.get(button);
            var preferred = old != null && old.assigned().equals(current) ? old.preferred() : current;
            int w = button.getWidth(), h = button.getHeight();
            var target = new Rectangle(preferred.x, preferred.y, w, h);
            if (occupied.stream().anyMatch(target::intersects)) {
                var xs = new LinkedHashSet<Integer>();
                var ys = new LinkedHashSet<Integer>();
                xs.add(preferred.x);
                ys.add(preferred.y);
                for (var rect : occupied) {
                    xs.add(rect.x - w - 4);
                    xs.add(rect.x + rect.width + 4);
                    ys.add(rect.y - h - 4);
                    ys.add(rect.y + rect.height + 4);
                }
                int best = Integer.MAX_VALUE;
                for (int y : ys) for (int x : xs) {
                    int distance = Math.abs(x - preferred.x) + Math.abs(y - preferred.y);
                    if (distance >= best || x < 0 || y < 0 || x + w > width || y + h > height) continue;
                    var candidate = new Rectangle(x, y, w, h);
                    if (occupied.stream().noneMatch(candidate::intersects)) {
                        best = distance;
                        target.setLocation(x, y);
                    }
                }
            }
            if (current.x != target.x || current.y != target.y) button.setPosition(target.x, target.y);
            buttonPositions.put(button, new ButtonPosition(preferred, target.getLocation(), w, h));
            occupied.add(target);
        }
        laidOutButtons = buttons;
        laidOutNativeBounds = nativeBounds;
    }

    private static boolean buttonLayoutCurrent() {
        int index = 0;
        for (var child : root.children()) {
            if (!(child instanceof AbstractButton button) || !button.visible || nativeButton(button)) continue;
            if (index >= laidOutButtons.size() || laidOutButtons.get(index++) != button) return false;
            var position = buttonPositions.get(button);
            if (position == null || position.assigned().x != button.getX() || position.assigned().y != button.getY()
                    || position.width() != button.getWidth() || position.height() != button.getHeight()) return false;
        }
        return index == laidOutButtons.size();
    }

    /** Draw and handle input on the same thread. Images cross JNI as bytes alongside the operations. */
    public static synchronized Object[] exchange(String input) throws ReflectiveOperationException {
        var textures = new java.util.ArrayList<Object[]>();
        String operations = exchange(input, textures);
        return new Object[]{operations, textures.toArray()};
    }

    private static String exchange(String input, java.util.List<Object[]> textures) throws ReflectiveOperationException {
        var in = JsonParser.parseString(input).getAsJsonObject();
        var mc = HorizonClient.minecraft();
        if (mc == null || mc.gui == null || mc.font == null) return "{}";
        String kind = in.has("kind") ? in.get("kind").getAsString() : "frame";
        boolean render = kind.equals("frame");
        active.set(true);
        try {
            Screen previousScreen = screen;
            if (kind.equals("close")) {
                closeSession();
                return "{}";
            }
            boolean requestedPause = in.has("pause") && in.get("pause").getAsBoolean();
            if (requestedPause != pause) {
                closeSession();
                pause = requestedPause;
            }
            if (pause && mc.player == null) return "{}";
            if (pause) HorizonClient.renderThread();
            int nativeWidth = in.get("width").getAsInt(), nativeHeight = in.get("height").getAsInt();
            int nativeScale = in.has("guiScale") ? Math.max(1, in.get("guiScale").getAsInt()) : 3;
            int pixelWidth = in.has("pixelWidth") ? in.get("pixelWidth").getAsInt() : nativeWidth * nativeScale;
            int pixelHeight = in.has("pixelHeight") ? in.get("pixelHeight").getAsInt() : nativeHeight * nativeScale;
            int scale = screen != null && screen != root ? Math.max(1, mc.getWindow().getGuiScale()) : nativeScale;
            int w = (pixelWidth + scale - 1) / scale, h = (pixelHeight + scale - 1) / scale;
            boolean resized = width != w || height != h;
            width = w;
            height = h;
            HorizonClient.width = w;
            HorizonClient.height = h;
            var window = mc.getWindow();
            if (window.getScreenWidth() != pixelWidth || window.getScreenHeight() != pixelHeight
                    || window.getWidth() != pixelWidth || window.getHeight() != pixelHeight
                    || window.getGuiScale() != scale || window.getGuiScaledWidth() != w || window.getGuiScaledHeight() != h) {
                HorizonClient.windowSize(pixelWidth, pixelHeight, scale);
            }
            double pixelX = in.get("x").getAsDouble() * nativeScale;
            double pixelY = in.get("y").getAsDouble() * nativeScale;
            int x = (int) (pixelX * w / pixelWidth), y = (int) (pixelY * h / pixelHeight);
            long now = System.nanoTime();
            boolean tick = render && now - tickAt >= 50_000_000L;
            if (tick) {
                tickAt = now;
                if (!pause) HorizonClient.clientTick();
            }
            if (!HorizonClient.modelsReady()) return "{}";
            if (mc.realmsDataFetcher() == null) HorizonClient.set(net.minecraft.client.Minecraft.class, mc,
                    "realmsDataFetcher", new com.mojang.realmsclient.gui.RealmsDataFetcher(
                            com.mojang.realmsclient.client.RealmsClient.getOrCreate(mc)));
            if (mc.gameRenderer.panorama() == null) {
                HorizonClient.set(net.minecraft.client.renderer.GameRenderer.class, mc.gameRenderer,
                        "panorama", new net.minecraft.client.renderer.Panorama());
            }
            // Recreate the menu when its display changes so mod overlays initialize
            // against the new widgets. Keep an open child screen and its state.
            boolean replaceRoot = screen == root && (pixelWidth != rootPixelWidth
                    || pixelHeight != rootPixelHeight || nativeScale != rootScale);
            if (root == null || replaceRoot) {
                if (pause) {
                    if (root == null) {
                        mouseWasGrabbed = HorizonClient.grabMouse(false);
                        clientWasPaused = mc.isPaused();
                        clientWasLocal = mc.isLocalServer();
                        HorizonClient.set(net.minecraft.client.Minecraft.class, mc, "pause", true);
                        // Rust opens this mirror only for its own integrated world.
                        HorizonClient.set(net.minecraft.client.Minecraft.class, mc, "isLocalServer", true);
                    }
                    root = new PauseScreen(true) {
                        @Override public void onClose() {
                            nativeNavigation = true;
                        }
                        @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean twice) {
                            rootClickReached = true;
                            rootClickConsumed = super.mouseClicked(event, twice);
                            return rootClickConsumed;
                        }
                        @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                            rootKeyReached = true;
                            rootKeyConsumed = super.keyPressed(event);
                            return rootKeyConsumed;
                        }
                    };
                } else {
                    var title = new TitleScreen() {
                        @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean twice) {
                            rootClickReached = true;
                            rootClickConsumed = super.mouseClicked(event, twice);
                            return rootClickConsumed;
                        }
                        @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                            rootKeyReached = true;
                            rootKeyConsumed = super.keyPressed(event);
                            return rootKeyConsumed;
                        }
                    };
                    // Horizon draws the splash; supplying one also avoids a second splash manager.
                    HorizonClient.set(TitleScreen.class, title, "splash", new SplashRenderer(Component.empty()));
                    root = title;
                }
                rootPixelWidth = pixelWidth;
                rootPixelHeight = pixelHeight;
                rootScale = nativeScale;
                buttonPositions.clear();
                setScreen(root);
            } else if (resized || screen.width != w || screen.height != h) {
                screen.resize(w, h);
                buttonPositions.clear();
            }
            for (var child : root.children()) if (child instanceof Button button && nativeButton(button)) {
                // Let mods intercept the original widgets before Horizon navigates. A modal
                // may reject a click without returning true from its screen handler.
                if (HorizonClient.field(Button.class, button, "onPress") != NATIVE_NAVIGATION) {
                    HorizonClient.set(Button.class, button, "onPress", NATIVE_NAVIGATION);
                }
                if (button.getMessage().getContents() instanceof TranslatableContents label
                        && label.getKey().equals("menu.multiplayer")) {
                    // The native menu owns multiplayer availability and the warning screen.
                    button.active = true;
                }
            }
            // Keep the complete widget hierarchy: mods anchor their overlays to vanilla
            // controls. Native navigation is filtered at input and drawing instead.
            if (render && (pause || !tick)) HorizonClient.tasks();
            // Client ticks can rest the mirror as a world player while a server finishes
            // closing. Input for this visible session must still reach its own screen.
            HorizonClient.set(net.minecraft.client.gui.Gui.class, mc.gui, "screen", screen);
            long handle = HorizonScreenEvents.windowHandle(mc);
            if (mc.mouseHandler.xpos() != pixelX || mc.mouseHandler.ypos() != pixelY) {
                mc.mouseHandler.onMove(handle, pixelX, pixelY, 0, 0);
                mc.mouseHandler.handleAccumulatedMovement();
            }
            if (tick) {
                if (pause) {
                    HorizonHooks.fireClient(mc, "tick");
                    HorizonServer.fireEach("ClientTickEvents.START_CLIENT_TICK", ClientTickEvents.START_CLIENT_TICK,
                            listener -> listener.onStartTick(mc));
                }
                HorizonScreenEvents.tick(screen);
                if (pause) HorizonServer.fireEach("ClientTickEvents.END_CLIENT_TICK", ClientTickEvents.END_CLIENT_TICK,
                        listener -> listener.onEndTick(mc));
            }
            if (screen == root && in.has("occupied")) fitModButtons(in.getAsJsonArray("occupied"));
            int modifiers = in.has("mods") ? in.get("mods").getAsInt() : 0;
            boolean taken = screen != root;
            switch (kind) {
                case "press" -> {
                    int button = in.get("button").getAsInt();
                    nativeNavigation = false;
                    rootClickReached = false;
                    rootClickConsumed = false;
                    mc.mouseHandler.onButton(handle, new MouseButtonInfo(button, modifiers), 1);
                    taken |= screen != root || (!nativeNavigation && (!rootClickReached || rootClickConsumed));
                    if (taken) takenButtons.add(button);
                }
                case "release" -> {
                    int button = in.get("button").getAsInt();
                    mc.mouseHandler.onButton(handle, new MouseButtonInfo(button, modifiers), 0);
                    taken |= takenButtons.remove(button);
                }
                case "scroll" -> mc.mouseHandler.onScroll(HorizonScreenEvents.windowHandle(mc), in.get("h").getAsDouble(), in.get("v").getAsDouble());
                case "key" -> {
                    Screen previous = screen;
                    var key = new net.minecraft.client.input.KeyEvent(in.get("key").getAsInt(),
                            in.get("keycode").getAsInt(), modifiers);
                    int code = in.get("key").getAsInt();
                    int action = in.has("action") ? in.get("action").getAsInt() : 1;
                    rootKeyReached = false;
                    rootKeyConsumed = false;
                    nativeNavigation = false;
                    mc.keyboardHandler.keyPress(HorizonScreenEvents.windowHandle(mc), action, key);
                    if (action == 0) taken |= takenKeys.remove(code);
                    else {
                        taken |= screen != root || !rootKeyReached || (rootKeyConsumed && !nativeNavigation);
                        if (taken) takenKeys.add(code);
                    }
                    if (action != 0 && code == 41 && screen != root && screen == previous && screen.shouldCloseOnEsc()) setScreen(root);
                    if (action != 0 && in.has("text")) for (int ch : in.get("text").getAsString().codePoints().toArray()) {
                        mc.keyboardHandler.charTyped(HorizonScreenEvents.windowHandle(mc), new net.minecraft.client.input.CharacterEvent(ch));
                    }
                }
                default -> {}
            }
            var out = new JsonObject();
            out.addProperty("custom", screen != root);
            out.addProperty("taken", taken || screen != root);
            if (!render) {
                out.addProperty("screenChanged", screen != previousScreen);
                return out.toString();
            }
            long millis = net.minecraft.util.Util.getMillis();
            if (timer == null) timer = new net.minecraft.client.DeltaTracker.Timer(20, millis, value -> value);
            timer.advanceGameTime(millis);
            timer.advanceRealTime(millis);
            var target = mc.gameRenderer.mainRenderTarget();
            var targetView = target == null ? null : target.getColorTextureView();
            if (target != null) {
                boolean clear = true;
                if (target.getColorTexture() instanceof HorizonGpu.Texture texture) {
                    // A fresh or already-cleared target has no pixels to erase. Most
                    // screen frames use recorded GUI operations and never write to it.
                    clear = texture.rendered || texture.written;
                    texture.rendered = false;
                    texture.written = false;
                }
                if (clear) com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                        .clearColorTexture(target.getColorTexture(), new org.joml.Vector4f(0, 0, 0, 0));
            }
            // Run the GUI entry point too: mods wrap its screen draw to place overlays
            // before and after the screen, outside Screen's own render method.
            var gameState = mc.gameRenderer.gameRenderState();
            var previousRenderState = gameState.guiRenderState;
            Object previousState = HorizonClient.field(net.minecraft.client.gui.Gui.class, mc.gui, "guiRenderState");
            var graphics = new HorizonGraphics(w, h, x, y);
            graphics.imagesSent = images;
            graphics.gpuImages = textures;
            graphics.highRefresh = pause;
            graphics.modsOnly = screen == root;
            try {
                HorizonClient.set(net.minecraft.client.gui.Gui.class, mc.gui, "guiRenderState",
                        gameState.guiRenderState);
                // The world is paused, so its tick is no longer driving client frame hooks.
                if (pause) {
                    HorizonHooks.fireClient(mc, "renderFrame");
                    HorizonHooks.fireFrame(mc.gameRenderer, true);
                }
                // Draw the screen and its overlays. Keeping the HUD disabled lets Horizon
                // supply it, while mods' screen render hooks still run inside Gui.
                mc.gui.extractRenderState(timer, false, true);
                if (pause) {
                    HorizonHooks.fireFrame(mc.gameRenderer, false);
                    HorizonHooks.fireRender(mc.gameRenderer);
                }
            } finally {
                HorizonClient.set(net.minecraft.client.renderer.state.GameRenderState.class, gameState, "guiRenderState", previousRenderState);
                HorizonClient.set(net.minecraft.client.gui.Gui.class, mc.gui, "guiRenderState", previousState);
            }
            // Some mods submit GPU passes directly to the screen target, bypassing GUI ops.
            if (targetView instanceof HorizonGpu.View view && HorizonGpu.drawnInto(view)) {
                graphics.modsOnly = false;
                graphics.gpuQuad(view, 0, 0, w, h, 0, 1, 0, 1, -1);
            }
            var ops = graphics.ops();
            // Mod screens may request a different logical GUI scale. The native window
            // keeps its own scale; transform all drawing and clipping into that space.
            float scaleX = pixelWidth / ((float) w * nativeScale);
            float scaleY = pixelHeight / ((float) h * nativeScale);
            if (scaleX != 1 || scaleY != 1) for (var element : ops) {
                var op = element.getAsJsonObject();
                if ("image".equals(op.get("op").getAsString())) continue;
                var before = op.has("pose") ? op.getAsJsonArray("pose") : null;
                var pose = new com.google.gson.JsonArray();
                for (int i = 0; i < 6; i++) {
                    float value = before == null ? (i == 0 || i == 3 ? 1 : 0) : before.get(i).getAsFloat();
                    pose.add(value * (i % 2 == 0 ? scaleX : scaleY));
                }
                op.add("pose", pose);
            }
            if (!ops.equals(lastOps)) {
                lastOps = ops;
                out.add("ops", ops);
            }
            if (HorizonHooks.DEBUG && logged != screen) {
                logged = screen;
                dev.horizonlab.bridge.Natives.log("Title UI: " + screen.getClass().getName()
                        + ", widgets=" + root.children().size() + ", operations=" + ops.size() + ", dropped=" + graphics.dropped
                        + ", screen=" + screen.width + "x" + screen.height + ", windowScale=" + mc.getWindow().getGuiScale()
                        + ", window=" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
            }
            out.addProperty("custom", screen != root);
            return out.toString();
        } finally {
            try { if (render) HorizonGpu.endFrame(); } finally { active.remove(); }
        }
    }
}
