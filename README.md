# Usbip Flying Mouse

Turn your Android phone into an air mouse via USB/IP. Control your PC cursor with motion
sensors — tilt to move, tap to click, D-Pad for keyboard navigation.

Powered by [usbipdcpp](https://github.com/yunsmall/usbipdcpp), exposing a virtual HID
composite device (relative mouse + keyboard) over USB/IP.

## Features

- **Motion control** — gyroscope-based cursor movement with adjustable sensitivity
- **Mouse buttons** — left / right / middle with hold-to-press
- **D-Pad** — arrow keys + OK (Enter) for keyboard navigation
- **Scroll wheel** — vertical scrolling
- **Foreground service** — server keeps running when the app is in background
- **Reusable server** — start / stop as many times as needed without restarting the app

## How It Works

The app creates a USB/IP server on your phone that exports a virtual HID device with two
interfaces:

| Interface | Protocol | Endpoint | Description |
|-----------|----------|----------|-------------|
| 0 | HID Mouse (relative) | 0x81 | 5-button mouse with X/Y movement and scroll wheel |
| 1 | HID Keyboard | 0x82 | Standard boot keyboard with media keys |

Clients connect via `usbip attach` and the host OS recognizes a standard USB HID mouse
and keyboard.

## Requirements

### Phone (Server)

- Android 9.0+ (API 28)
- Gyroscope sensor (used for motion tracking)
- [usbipdcpp](https://github.com/yunsmall/usbipdcpp) (included as submodule)

### PC (Client)

- Linux with `usbip` tools installed
- Or any OS with a USB/IP client

## Usage

1. **Start the server** — open the app, set port (default 3240), tap **Start**
2. **Connect from PC** — run:
   ```bash
   sudo usbip attach -r <phone-ip> -b 1-1
   ```
3. **Control** — tilt your phone to move the cursor, use on-screen buttons for clicks
   and keyboard input
4. **Stop** — tap **Stop** to shut down the server

## Build

```bash
git clone --recurse-submodules https://github.com/yunsmall/Usbip-Flying-Mouse.git
```

Open in Android Studio, sync Gradle, build.

Requires:
- Android NDK 29.0.14033849
- [vcpkg](https://github.com/microsoft/vcpkg) with `VCPKG_ROOT` environment variable set
- Dependencies (handled by vcpkg): asio, spdlog

## License

This project is licensed under the [MIT License](LICENSE).
