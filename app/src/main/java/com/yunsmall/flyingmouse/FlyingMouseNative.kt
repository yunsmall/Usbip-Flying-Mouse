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
        const val MOD_LEFT_CTRL = 0x01; const val MOD_LEFT_SHIFT = 0x02
    }
}
