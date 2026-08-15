package dev.vox.lss.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 1.21.11 pass-through pin (V-2/S5's whole-suite swap — main's twin pins the
 * 26.x ScopedValue carrier ladder): this line's ScopedCarrier is a pure
 * pass-through, and the pin keeps the xplat delegate's entry point exercised so
 * the S5 wiring cannot silently orphan it.
 */
class ScopedCarrierTest {

    @Test
    void callSerializingPassesStraightThrough() {
        assertEquals("through", ScopedCarrier.callSerializing(() -> "through"));
    }

    @Test
    void exceptionsPropagateUnchanged() {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ScopedCarrier.callSerializing(() -> {
                    throw new IllegalStateException("serialize failed");
                }));
        assertEquals("serialize failed", thrown.getMessage());
    }
}
