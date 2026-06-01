#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <memory>
#include <mutex>

#include <spdlog/spdlog.h>
#include <spdlog/sinks/android_sink.h>

#include "Server.h"
#include "virtual_device/SimpleVirtualDeviceHandler.h"
#include "virtual_device/devices/RelativeMouseHandler.h"
#include "virtual_device/devices/KeyboardHandler.h"

#define LOG_TAG "FlyingMouseNative"

namespace {

std::mutex g_mutex;
std::unique_ptr<usbipdcpp::Server> g_server;
std::atomic<bool> g_server_running{false};
usbipdcpp::StringPool g_string_pool;
std::shared_ptr<usbipdcpp::RelativeMouseHandler> g_mouse_handler;
std::shared_ptr<usbipdcpp::KeyboardHandler> g_keyboard_handler;

bool createServer() {
    spdlog::info("Creating virtual flying mouse device");

    usbipdcpp::UsbInterface mouse_if{
        .interface_class = 0x03,
        .interface_subclass = 0x01,
        .interface_protocol = 0x02,
        .endpoints = {{{
            usbipdcpp::UsbEndpoint{
                .address = 0x81,
                .attributes = 0x03,
                .max_packet_size = 8,
                .interval = 1
            }
        }}}
    };
    mouse_if.with_handler<usbipdcpp::RelativeMouseHandler>(g_string_pool);

    usbipdcpp::UsbInterface kb_if{
        .interface_class = 0x03,
        .interface_subclass = 0x01,
        .interface_protocol = 0x01,
        .endpoints = {{{
            usbipdcpp::UsbEndpoint{
                .address = 0x82,
                .attributes = 0x03,
                .max_packet_size = 9,
                .interval = 1
            }
        }}}
    };
    kb_if.with_handler<usbipdcpp::KeyboardHandler>(g_string_pool);

    auto device = std::make_shared<usbipdcpp::UsbDevice>(usbipdcpp::UsbDevice{
        .path = "/usbipdcpp/flyingmouse",
        .busid = "1-1",
        .bus_num = 1,
        .dev_num = 1,
        .speed = static_cast<std::uint32_t>(usbipdcpp::UsbSpeed::Full),
        .vendor_id = 0x5678,
        .product_id = 0x1234,
        .device_bcd = {1, 0, 0},
        .device_class = 0x00,
        .device_subclass = 0x00,
        .device_protocol = 0x00,
        .configuration_value = 1,
        .num_configurations = 1,
        .interfaces = {mouse_if, kb_if},
        .ep0_in = usbipdcpp::UsbEndpoint::get_ep0_in(usbipdcpp::UsbSpeed::Full),
        .ep0_out = usbipdcpp::UsbEndpoint::get_ep0_out(usbipdcpp::UsbSpeed::Full),
    });

    auto device_handler = device->with_handler<usbipdcpp::SimpleVirtualDeviceHandler>(g_string_pool);
    device_handler->setup_interface_handlers();

    g_mouse_handler = std::dynamic_pointer_cast<usbipdcpp::RelativeMouseHandler>(device->interfaces[0].handler);
    g_keyboard_handler = std::dynamic_pointer_cast<usbipdcpp::KeyboardHandler>(device->interfaces[1].handler);

    g_server = std::make_unique<usbipdcpp::Server>();
    g_server->add_device(std::move(device));

    spdlog::info("Flying mouse device created");
    return true;
}

bool startServer(std::uint16_t port) {
    if (g_server_running) {
        spdlog::warn("Server already running");
        return true;
    }
    try {
        asio::ip::tcp::endpoint endpoint(asio::ip::tcp::v4(), port);
        g_server->start(endpoint);
        g_server_running = true;
        spdlog::info("Server started on port {}", port);
        return true;
    } catch (const std::exception &e) {
        spdlog::error("Failed to start server: {}", e.what());
        return false;
    }
}

void stopServer() {
    if (!g_server_running) {
        spdlog::warn("Server not running");
        return;
    }
    spdlog::info("Stopping server");
    try {
        g_server->stop();
    } catch (const std::exception &e) {
        spdlog::error("Error stopping: {}", e.what());
    }
    g_server_running = false;
    spdlog::info("Server stopped");
}

} // anonymous namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeInit(JNIEnv *, jobject) {
    auto android_sink = std::make_shared<spdlog::sinks::android_sink_mt>("FlyingMouse");
    auto logger = std::make_shared<spdlog::logger>("flyingmouse", spdlog::sinks_init_list{android_sink});
    logger->set_level(spdlog::level::debug);
    spdlog::set_default_logger(logger);
    spdlog::set_pattern("[%H:%M:%S] [%l] %v");
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Native initialized");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeCreateServer(JNIEnv *, jobject) {
    std::lock_guard lock(g_mutex);
    if (g_server) {
        spdlog::info("Server already created");
        return JNI_TRUE;
    }
    return createServer() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeStartServer(JNIEnv *, jobject, jint port) {
    std::lock_guard lock(g_mutex);
    if (!g_server) {
        spdlog::error("Server not created yet");
        return JNI_FALSE;
    }
    return startServer(static_cast<std::uint16_t>(port)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeStopServer(JNIEnv *, jobject) {
    std::lock_guard lock(g_mutex);
    stopServer();
}

JNIEXPORT jboolean JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeIsServerRunning(JNIEnv *, jobject) {
    return g_server_running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeDestroyServer(JNIEnv *, jobject) {
    std::lock_guard lock(g_mutex);
    stopServer();
    g_server.reset();
    g_mouse_handler.reset();
    g_keyboard_handler.reset();
    spdlog::info("Server destroyed");
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeSendMouseMove(JNIEnv *, jobject, jint dx, jint dy) {
    if (g_mouse_handler) {
        g_mouse_handler->move(static_cast<std::int16_t>(dx), static_cast<std::int16_t>(dy));
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeSendMouseWheel(JNIEnv *, jobject, jint dy) {
    if (g_mouse_handler) {
        g_mouse_handler->set_wheel(static_cast<std::int8_t>(dy));
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeSetLeftButton(JNIEnv *, jobject, jboolean pressed) {
    if (g_mouse_handler) {
        g_mouse_handler->set_left_button(pressed == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeSetRightButton(JNIEnv *, jobject, jboolean pressed) {
    if (g_mouse_handler) {
        g_mouse_handler->set_right_button(pressed == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeSetMiddleButton(JNIEnv *, jobject, jboolean pressed) {
    if (g_mouse_handler) {
        g_mouse_handler->set_middle_button(pressed == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativePressKey(JNIEnv *, jobject, jint keycode) {
    if (g_keyboard_handler) {
        g_keyboard_handler->press_key(static_cast<std::uint8_t>(keycode));
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeReleaseKey(JNIEnv *, jobject, jint keycode) {
    if (g_keyboard_handler) {
        g_keyboard_handler->release_key(static_cast<std::uint8_t>(keycode));
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeReleaseAllKeys(JNIEnv *, jobject) {
    if (g_keyboard_handler) {
        g_keyboard_handler->release_all();
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativeSetModifier(JNIEnv *, jobject, jint mask, jboolean set) {
    if (g_keyboard_handler) {
        if (set) g_keyboard_handler->set_modifier(static_cast<std::uint8_t>(mask));
        else    g_keyboard_handler->clear_modifier(static_cast<std::uint8_t>(mask));
    }
}

JNIEXPORT void JNICALL
Java_com_yunsmall_flyingmouse_FlyingMouseNative_nativePressMediaKey(JNIEnv *, jobject, jint usage) {
    if (g_keyboard_handler) {
        g_keyboard_handler->press_media_key(static_cast<std::uint16_t>(usage));
    }
}

} // extern "C"
