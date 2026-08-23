package dev.vox.lss.neoforge.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * 1.21.10 LINE SHIM — the NeoForge twin of the fabric gametest {@code Gt}
 * (docs/planning/mc1.21.10-line-notes.md): this MC's {@link GameTestHelper}
 * drops the String overloads of assertTrue/assertFalse (fail(String) survives — the reroute wraps it anyway for uniformity); the port sed-reroutes
 * {@code helper.assertTrue(} → {@code Gt.assertTrue(helper, } and
 * {@code helper.fail(} → {@code Gt.fail(helper, } so every condition and
 * message expression stays byte-identical to the parent line.
 */
final class Gt {
    private Gt() {}

    static void assertTrue(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    static void fail(GameTestHelper helper, String message) {
        helper.fail(Component.literal(message));
    }
}
