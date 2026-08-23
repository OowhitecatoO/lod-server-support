package dev.vox.lss.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * 1.21.10 LINE SHIM (docs/planning/mc1.21.10-line-notes.md): this MC's
 * {@link GameTestHelper} takes {@link Component} assertion messages — the
 * 1.21.9/1.21.10 window; every sibling line (26.x, 1.21.11, 1.21.1) has the
 * String overloads the shared test bodies were written against. One adapter
 * beats ~550 wrapped message expressions: the port sed-reroutes
 * {@code helper.assertTrue(} → {@code Gt.assertTrue(helper, } and
 * {@code helper.fail(} → {@code Gt.fail(helper, } across the gametest
 * sources, leaving every condition and message expression byte-identical to
 * the parent line (the diff-of-diffs reviewers' fidelity property).
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
