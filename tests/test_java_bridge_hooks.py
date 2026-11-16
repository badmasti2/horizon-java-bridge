"""Execute HorizonHooks against synthetic mods, with real Mixin/ASM classes and no game.

Set HORIZON_HOOK_TEST_LIBS to a directory containing Fabric Loader, Mixin, ASM,
ASM Tree and Gson jars. No jars are downloaded or mods started by these tests.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
PLATFORM = ROOT / "crates/horizon-jvm/java/platform/dev/horizonlab/platform"

# Only services outside the callback dispatch are replaced. The complete production
# HorizonHooks class is compiled, including its census, resolution and invocation.
STUBS = {
    "dev.horizonlab.bridge.Natives": "public static void log(String text) { System.err.println(text); }",
    "dev.horizonlab.platform.HorizonProfile": "static void stallWatch() {}",
    "dev.horizonlab.platform.HorizonMixinTable": "static void scan() {}",
    "dev.horizonlab.platform.HorizonFailures": """
        static int count;
        static void report(String where, Throwable cause, java.lang.reflect.Method handler) { count++; }
    """,
    "dev.horizonlab.platform.HorizonDeviceMods": """
        static boolean runs(java.lang.reflect.Method method) { return true; }
    """,
    "dev.horizonlab.platform.HorizonClient": """
        static net.minecraft.client.main.GameConfig gameConfig() { return null; }
        static net.minecraft.client.DeltaTracker deltaTracker() { return net.minecraft.client.DeltaTracker.ONE; }
    """,
    "dev.horizonlab.platform.HorizonData": "static Object stem(Object id) { return null; }",
    "net.minecraft.client.DeltaTracker": "public static final DeltaTracker ONE = new DeltaTracker();",
    "net.minecraft.client.gui.GuiGraphicsExtractor": "",
    "net.minecraft.client.main.GameConfig": "",
    "net.minecraft.world.entity.Entity": "",
    "net.minecraft.world.level.Level": """
        public Dimension dimension() { return null; }
        public record Dimension(Object identifier) {}
    """,
    "net.minecraft.world.level.dimension.LevelStem": "",
    "net.minecraft.world.level.levelgen.NoiseGeneratorSettings": "",
    "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator": """
        public Settings generatorSettings() { return null; }
        public record Settings(Object value) {}
    """,
}


class JavaBridgeHookTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        java, javac = shutil.which("java"), shutil.which("javac")
        libs = os.environ.get("HORIZON_HOOK_TEST_LIBS")
        if not java or not javac or not libs:
            raise unittest.SkipTest("requires JDK 21+ and HORIZON_HOOK_TEST_LIBS (Fabric/Mixin/ASM/Gson)")
        jars = sorted(Path(libs).glob("*.jar"))
        if not jars:
            raise unittest.SkipTest("HORIZON_HOOK_TEST_LIBS contains no jars")
        cls.scratch = tempfile.TemporaryDirectory(prefix="horizon-hook-tests-")
        cls.addClassCleanup(cls.scratch.cleanup)
        folder = Path(cls.scratch.name)
        sources = []
        for name, body in STUBS.items():
            package, _, simple = name.rpartition(".")
            file = folder / (name.replace(".", "/") + ".java")
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(f"package {package}; public class {simple} {{ {body} }}", encoding="utf-8")
            sources.append(file)
        sources += [PLATFORM / "HorizonHooks.java", PLATFORM / "HorizonClientExecutors.java", ROOT / "tests/java/HookCallbacks.java",
                    ROOT / "crates/horizon-jvm/java/platform/dev/horizonlab/platform/HorizonMixinInit.java"]
        classes = folder / "classes"
        class_path = os.pathsep.join(str(jar) for jar in jars)
        args = ["--release", "21", "-proc:none", "-encoding", "UTF-8", "-cp", class_path,
                "-d", str(classes), *(str(file) for file in sources)]
        # Use the same argument-file convention as the bridge's Windows builds.
        argfile = folder / "javac.args"
        argfile.write_text("\n".join('"' + arg.replace("\\", "/") + '"' for arg in args), encoding="utf-8")
        compiled = subprocess.run([javac, "@" + str(argfile)], capture_output=True, text=True, timeout=60)
        if compiled.returncode:
            raise RuntimeError(compiled.stdout + compiled.stderr)
        cls.command = [java, "-cp", str(classes) + os.pathsep + class_path,
                       "dev.horizonlab.platform.HookCallbacks"]
        subprocess.run([*cls.command[:-1], cls.command[-1] + "$Prepare", str(classes)],
                       check=True, capture_output=True, text=True, timeout=30)

    def run_case(self, case):
        result = subprocess.run([*self.command, case], capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_callback_only_can_cancel_void_target_with_arguments(self):
        self.run_case("void")

    def test_callback_only_can_replace_return_value(self):
        self.run_case("return")

    def test_callback_only_receives_original_return_value(self):
        self.run_case("tail")

    def test_full_argument_handlers_keep_argument_order(self):
        self.run_case("full")

    def test_static_callback_only_handler(self):
        self.run_case("static")

    def test_cancellation_stops_later_handlers(self):
        self.run_case("chain")

    def test_captured_locals_still_excluded(self):
        self.run_case("locals")

    def test_explicit_descriptor_still_excludes_other_overload(self):
        self.run_case("overload")

    def test_hud_supports_callback_only_and_full_arguments(self):
        self.run_case("hud")

    def test_one_handler_can_run_at_head_and_return(self):
        self.run_case("multi")

    def test_duplicate_event_points_only_fire_once(self):
        self.run_case("duplicate")

    def test_merged_scan_keeps_all_event_points(self):
        self.run_case("merged-multi")

    def test_skipped_constructor_fields_initialize_once_with_the_client_receiver(self):
        self.run_case("fields")

    def test_missing_executors_queue_work_and_preserve_existing_executors(self):
        self.run_case("executor")

    def test_startup_field_and_resize_milestones(self):
        self.run_case("milestones")

    def test_client_components_move_threads_without_losing_queued_work(self):
        self.run_case("thread-migration")


if __name__ == "__main__":
    unittest.main()
