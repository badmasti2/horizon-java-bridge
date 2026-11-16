package dev.horizonlab.platform;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Synthetic mod metadata and merged handlers; the census and dispatch are HorizonHooks'. */
public final class HookCallbacks {
    @Retention(RetentionPolicy.RUNTIME)
    @interface Merged { String mixin(); int priority(); }

    /** MixinMerged is bytecode-only (@Target({})); attach it as a transformer would. */
    public static final class Prepare {
        public static void main(String[] args) throws Exception {
            for (String target : new String[] {"Target", "Hud"}) {
                var path = java.nio.file.Path.of(args[0], "dev/horizonlab/platform/HookCallbacks$" + target + ".class");
                ClassNode node = new ClassNode();
                new ClassReader(java.nio.file.Files.readAllBytes(path)).accept(node, 0);
                for (var method : node.methods) {
                    if (method.visibleAnnotations == null) continue;
                    for (var annotation : method.visibleAnnotations) {
                        if (annotation.desc.equals("Ldev/horizonlab/platform/HookCallbacks$Merged;")) {
                            annotation.desc = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
                        }
                    }
                }
                ClassWriter writer = new ClassWriter(0);
                node.accept(writer);
                java.nio.file.Files.write(path, writer.toByteArray());
            }
        }
    }

    private static final String MIXIN = "dev.horizonlab.platform.HookCallbacks$Mod";
    private static final String DESC = "(ILjava/lang/String;)V";
    private static int calls;
    private static String seen = "";

    public static final class State {
        final Object owner;
        private final Thread thread = Thread.currentThread();
        private final java.util.Queue<Runnable> queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        State(Object owner) { this.owner = owner; calls++; }
    }

    static class ClientBase {
        java.util.concurrent.Executor inherited;
    }

    static final class QueuedClient extends ClientBase implements java.util.concurrent.Executor {
        private java.util.concurrent.Executor missing;
        private final java.util.concurrent.Executor existing = Runnable::run;
        private static java.util.concurrent.Executor global;
        private final java.util.Queue<Runnable> queue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        public void execute(Runnable task) { queue.add(task); }
    }

    @Mixin(Target.class)
    static class Mod {
        Mod(String ignored) {}
        private State state = new State(this);
        private Runnable callback = () -> { this.state.owner.toString(); calls++; };
        @Inject(method = "voidTarget" + DESC, at = @At("HEAD"), cancellable = true)
        private void cancel(CallbackInfo ci) {}
        @Inject(method = "returnTarget(ILjava/lang/String;)I", at = @At("HEAD"), cancellable = true)
        private void replace(CallbackInfoReturnable<Integer> ci) {}
        @Inject(method = "tailTarget(ILjava/lang/String;)I", at = @At("RETURN"), cancellable = true)
        private void tail(CallbackInfoReturnable<Integer> ci) {}
        @Inject(method = "fullTarget" + DESC, at = @At("HEAD"))
        private void full(int value, String text, CallbackInfo ci) {}
        @Inject(method = "staticTarget" + DESC, at = @At("HEAD"), cancellable = true)
        private static void statik(CallbackInfo ci) {}
        @Inject(method = "chainTarget" + DESC, at = @At("HEAD"))
        private void first(int value, String text, CallbackInfo ci) {}
        @Inject(method = "chainTarget" + DESC, at = @At("HEAD"), cancellable = true)
        private void second(CallbackInfo ci) {}
        @Inject(method = "chainTarget" + DESC, at = @At("HEAD"))
        private void third(CallbackInfo ci) {}
        @Inject(method = "localsTarget" + DESC, at = @At("RETURN"))
        private void locals(int value, String text, CallbackInfo ci, Object local) {}
        @Inject(method = "multiTarget" + DESC, at = {@At("HEAD"), @At("RETURN")})
        private void multi(CallbackInfo ci) {}
        @Inject(method = "duplicateTarget" + DESC, at = {@At("HEAD"), @At("HEAD")})
        private void duplicate(int value, String text, CallbackInfo ci) {}
    }

    static final class Target {
        private State state;
        private Runnable callback;
        @Merged(mixin = MIXIN, priority = 1000)
        private void mda000$lambda$new$0$0() { check(state.owner == this, "lambda receiver changed"); calls++; }
        @Merged(mixin = MIXIN, priority = 1000)
        private void cancel(CallbackInfo ci) { calls++; ci.cancel(); }
        @Merged(mixin = MIXIN, priority = 1000)
        private void replace(CallbackInfoReturnable<Integer> ci) { calls++; ci.setReturnValue(37); }
        @Merged(mixin = MIXIN, priority = 1000)
        private void tail(CallbackInfoReturnable<Integer> ci) { calls++; ci.setReturnValue(ci.getReturnValueI() + 1); }
        @Merged(mixin = MIXIN, priority = 1000)
        private void full(int value, String text, CallbackInfo ci) { calls++; seen = value + ":" + text; }
        @Merged(mixin = MIXIN, priority = 1000)
        private static void statik(CallbackInfo ci) { calls++; ci.cancel(); }
        @Merged(mixin = MIXIN, priority = 100)
        private void first(int value, String text, CallbackInfo ci) { seen += value + ":" + text; }
        @Merged(mixin = MIXIN, priority = 200)
        private void second(CallbackInfo ci) { seen += ":cancel"; ci.cancel(); }
        @Merged(mixin = MIXIN, priority = 300)
        private void third(CallbackInfo ci) { throw new AssertionError("ran after cancellation"); }
        @Merged(mixin = MIXIN, priority = 1000)
        private void locals(int value, String text, CallbackInfo ci, Object local) { calls++; }
        @Merged(mixin = MIXIN, priority = 1000)
        private void multi(CallbackInfo ci) { calls++; }
        @Merged(mixin = MIXIN, priority = 1000)
        private void duplicate(int value, String text, CallbackInfo ci) { calls++; }
    }

    static final class Hud {
        @Merged(mixin = MIXIN, priority = 1000)
        private void shortHud(CallbackInfo ci) { calls++; }
        @Merged(mixin = MIXIN, priority = 1000)
        private void fullHud(int width, String text, CallbackInfo ci) { calls++; seen = width + ":" + text; }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static HorizonHooks.Result fire(String method, String desc, String at, Object initial) {
        return HorizonHooks.fire(method.equals("staticTarget") ? null : new Target(), Target.class,
                method, desc, at, new Object[] {7, "hello"}, initial, false);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        // Read the synthetic mod through the production ASM census, not a hand-made Hook.
        var read = HorizonHooks.class.getDeclaredMethod("read", String.class, byte[].class);
        read.setAccessible(true);
        try (var in = HookCallbacks.class.getResourceAsStream("HookCallbacks$Mod.class")) {
            read.invoke(null, "synthetic", in.readAllBytes());
        }
        switch (args[0]) {
            case "milestones" -> {
                var point = HorizonHooks.class.getDeclaredMethod("startPoint", Object.class);
                point.setAccessible(true);
                var at = new org.objectweb.asm.tree.AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
                at.values = new java.util.ArrayList<>(List.of("value", "FIELD", "target",
                        "Lnet/minecraft/client/Minecraft;fixerUpper:Lcom/mojang/datafixers/DataFixer;"));
                check(HorizonHooks.START_FIXER.equals(point.invoke(null, List.of(at))), "data fixer hook lost");
                at.values = new java.util.ArrayList<>(List.of("value", "INVOKE", "target",
                        "Lnet/minecraft/client/Minecraft;resizeGui()V"));
                check(HorizonHooks.START_OPTIONS.equals(point.invoke(null, List.of(at))), "GUI startup hook lost");
                at.values = new java.util.ArrayList<>(List.of("value", "INVOKE", "target",
                        "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;registerReloadListener(Lnet/minecraft/server/packs/resources/PreparableReloadListener;)V"));
                check(HorizonHooks.START_RESOURCES.equals(point.invoke(null, List.of(at))), "resource startup hook lost");
            }
            case "executor" -> {
                QueuedClient client = new QueuedClient();
                var original = client.existing;
                HorizonClientExecutors.initialize(client);
                check(client.missing == client && client.inherited == client, "missing executor not supplied");
                check(client.existing == original && QueuedClient.global == null, "existing or static executor changed");
                Thread worker = new Thread(() -> client.missing.execute(() -> calls++));
                worker.start();
                worker.join();
                check(calls == 0, "background task executed on the worker");
                client.queue.remove().run();
                check(calls == 1, "queued task lost");
            }
            case "fields" -> {
                var scan = HorizonHooks.class.getDeclaredField("scanned");
                scan.setAccessible(true);
                scan.setBoolean(null, true);
                Target target = new Target();
                HorizonHooks.initFields(target);
                check(target.state != null && target.state.owner == target, "receiver field was not initialized");
                State original = target.state;
                HorizonHooks.initFields(target);
                check(target.state == original && calls == 1, "initializer ran again on an initialized object");
                check(target.callback != null, "constructor lambda not initialized");
                target.callback.run();
                check(calls == 2, "constructor lambda did not run");
                Target partial = new Target();
                Runnable existing = () -> {};
                partial.callback = existing;
                HorizonHooks.initFields(partial);
                check(partial.state != null && partial.state.owner == partial, "partial initialization lost a field");
                check(partial.callback == existing, "partial initialization replaced an existing field");
            }
            case "thread-migration" -> {
                var scan = HorizonHooks.class.getDeclaredField("scanned");
                scan.setAccessible(true);
                scan.setBoolean(null, true);
                Target target = new Target();
                HorizonHooks.initFields(target);
                State state = target.state;
                state.queue.add(() -> seen = "queued");
                Thread previous = Thread.currentThread();
                Thread next = new Thread(() -> {
                    HorizonMixinInit.rebindThread(target, previous, Thread.currentThread());
                    check(state.thread == Thread.currentThread(), "client component retained its loading thread");
                    check(target.state == state, "client component was replaced");
                    state.queue.remove().run();
                });
                next.setUncaughtExceptionHandler((thread, error) -> seen = "failed: " + error);
                next.start();
                next.join();
                check("queued".equals(seen), "migration lost queued work: " + seen);
            }
            case "void" -> {
                var result = fire("voidTarget", DESC, "HEAD", null);
                check(calls == 1 && result.cancelled(), "callback-only cancellation was skipped");
            }
            case "return" -> {
                var result = fire("returnTarget", "(ILjava/lang/String;)I", "HEAD", 0);
                check(calls == 1 && result.cancelled() && result.returnValue().equals(37), "return override lost");
            }
            case "tail" -> {
                var result = fire("tailTarget", "(ILjava/lang/String;)I", "RETURN", 41);
                check(calls == 1 && result.returnValue().equals(42), "original return value lost");
            }
            case "full" -> {
                var result = fire("fullTarget", DESC, "HEAD", null);
                check(calls == 1 && seen.equals("7:hello") && !result.cancelled(), "arguments changed");
            }
            case "static" -> {
                var result = fire("staticTarget", DESC, "HEAD", null);
                check(calls == 1 && result.cancelled(), "static callback-only handler was skipped");
            }
            case "chain" -> {
                var result = fire("chainTarget", DESC, "HEAD", null);
                check(result.cancelled() && seen.equals("7:hello:cancel"), "mixed handlers did not share cancellation");
            }
            case "locals" -> {
                var result = fire("localsTarget", DESC, "RETURN", null);
                check(calls == 0 && !result.cancelled(), "unavailable locals were supplied");
            }
            case "overload" -> {
                var result = fire("voidTarget", "(ILjava/lang/Object;)V", "HEAD", null);
                check(calls == 0 && !result.cancelled(), "handler ran on wrong overload");
            }
            case "multi" -> {
                fire("multiTarget", DESC, "HEAD", null);
                check(calls == 1, "multi-point HEAD was skipped");
                fire("multiTarget", DESC, "RETURN", null);
                check(calls == 2, "multi-point RETURN was skipped");
            }
            case "duplicate" -> {
                fire("duplicateTarget", DESC, "HEAD", null);
                check(calls == 1, "same event was fired twice");
            }
            case "merged-multi" -> {
                var injects = HorizonHooks.class.getDeclaredMethod("mixinInjects", String.class);
                injects.setAccessible(true);
                List<String[]> entries = (List<String[]>) injects.invoke(null, MIXIN);
                var points = entries.stream().filter(e -> e[0].equals("multiTarget")).map(e -> e[2]).toList();
                check(points.equals(List.of("HEAD", "RETURN")), "merged scan lost event points");
            }
            case "hud" -> {
                Field field = HorizonHooks.class.getDeclaredField("HUD_HOOKS");
                field.setAccessible(true);
                List<String[]> hooks = (List<String[]>) field.get(null);
                hooks.add(new String[] {"draw", MIXIN, "shortHud", "synthetic"});
                hooks.add(new String[] {"draw", MIXIN, "fullHud", "synthetic"});
                HorizonHooks.fireHud(new Hud(), "draw", 7, "hello");
                check(calls == 2 && seen.equals("7:hello"), "HUD callback signatures were skipped");
            }
            default -> throw new AssertionError(args[0]);
        }
        check(HorizonFailures.count == 0, "handler raised a swallowed exception");
        System.out.println(args[0] + " passed");
    }
}
