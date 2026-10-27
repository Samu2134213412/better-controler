package com.bettercontroller.logic;

/**
 * Validation of keyboard key codes without depending on GLFW, so server-side code can use it.
 * The numeric values are the GLFW key codes that Minecraft uses for {@code InputConstants.Type.KEYSYM}.
 */
public final class KeyCodes {
    /** GLFW_KEY_B, the default key of new keyboard input nodes. */
    public static final int DEFAULT_KEY = 66;
    /** GLFW_KEY_ESCAPE; reserved for cancelling key selection, never assignable. */
    public static final int ESCAPE = 256;

    private KeyCodes() {
    }

    public static boolean isAssignable(int key) {
        // Printable keys: space, apostrophe, comma..9, semicolon, equals, A..Z, [ \ ], grave accent
        if (key == 32 || key == 39 || (key >= 44 && key <= 57) || key == 59 || key == 61
                || (key >= 65 && key <= 93) || key == 96) {
            return true;
        }
        // Enter, Tab, Backspace, Insert, Delete, arrows, Page Up/Down, Home, End
        if (key >= 257 && key <= 269) {
            return true;
        }
        // Caps/Scroll/Num Lock, Print Screen, Pause
        if (key >= 280 && key <= 284) {
            return true;
        }
        // F1..F25
        if (key >= 290 && key <= 314) {
            return true;
        }
        // Keypad 0..9, decimal, divide, multiply, subtract, add, enter, equal
        if (key >= 320 && key <= 336) {
            return true;
        }
        // Shift, Control, Alt, Super (left and right), Menu
        return key >= 340 && key <= 348;
    }
}
