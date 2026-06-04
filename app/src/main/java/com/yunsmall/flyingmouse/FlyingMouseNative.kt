package com.yunsmall.flyingmouse

object FlyingMouseNative {
    init { System.loadLibrary("flyingmouse_native") }

    external fun nativeInit(): Boolean
    external fun nativeCreateServer(): Boolean
    external fun nativeStartServer(port: Int): Boolean
    external fun nativeStopServer()
    external fun nativeIsServerRunning(): Boolean
    external fun nativeDestroyServer()

    external fun nativeSendMouseMove(dx: Int, dy: Int)
    external fun nativeSendMouseWheel(dy: Int)
    external fun nativeSetLeftButton(pressed: Boolean)
    external fun nativeSetRightButton(pressed: Boolean)
    external fun nativeSetMiddleButton(pressed: Boolean)

    external fun nativePressKey(keycode: Int)
    external fun nativeReleaseKey(keycode: Int)
    external fun nativeReleaseAllKeys()
    external fun nativeSetModifier(mask: Int, set: Boolean)
    external fun nativePressMediaKey(usage: Int)

    fun init() = try { nativeInit() } catch (_: UnsatisfiedLinkError) { false }
    fun createServer() = nativeCreateServer()
    fun startServer(port: Int) = nativeStartServer(port)
    fun stopServer() = nativeStopServer()
    fun isServerRunning() = nativeIsServerRunning()
    fun destroyServer() = nativeDestroyServer()

    fun sendMouseMove(dx: Int, dy: Int) = nativeSendMouseMove(dx, dy)
    fun sendMouseWheel(dy: Int) = nativeSendMouseWheel(dy.coerceIn(-127, 127))
    fun setLeftButton(p: Boolean) = nativeSetLeftButton(p)
    fun setRightButton(p: Boolean) = nativeSetRightButton(p)
    fun setMiddleButton(p: Boolean) = nativeSetMiddleButton(p)
    fun pressKey(k: Int) = nativePressKey(k)
    fun releaseKey(k: Int) = nativeReleaseKey(k)
    fun releaseAllKeys() = nativeReleaseAllKeys()
    fun pressMediaKey(u: Int) = nativePressMediaKey(u)

    object HID {
        const val KEY_LEFT = 0x50; const val KEY_RIGHT = 0x4F
        const val KEY_UP = 0x52; const val KEY_DOWN = 0x51
        const val KEY_ENTER = 0x28; const val KEY_ESC = 0x29
        const val KEY_BACKSPACE = 0x2A; const val KEY_TAB = 0x2B
        const val KEY_SPACE = 0x2C; const val KEY_CAPSLOCK = 0x39
        const val KEY_DELETE = 0x4C; const val KEY_HOME = 0x4A
        const val KEY_END = 0x4D; const val KEY_PAGEUP = 0x4B; const val KEY_PAGEDOWN = 0x4E
        const val KEY_INSERT = 0x49
        // symbols
        const val KEY_MINUS = 0x2D; const val KEY_EQUAL = 0x2E
        const val KEY_LEFTBRACE = 0x2F; const val KEY_RIGHTBRACE = 0x30
        const val KEY_BACKSLASH = 0x31; const val KEY_SEMICOLON = 0x33
        const val KEY_QUOTE = 0x34; const val KEY_COMMA = 0x36
        const val KEY_PERIOD = 0x37; const val KEY_SLASH = 0x38
        const val MOD_LEFT_CTRL = 0x01; const val MOD_LEFT_SHIFT = 0x02
        const val MOD_LEFT_ALT = 0x04; const val MOD_LEFT_GUI = 0x08
        // USB HID keycodes for letters a-z: 0x04-0x1D
        val LETTERS = (0..25).map { 0x04 + it }
        // USB HID keycodes for numbers 1-9,0: 0x1E-0x27
        val NUMBERS = listOf(0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27)
    }
}
