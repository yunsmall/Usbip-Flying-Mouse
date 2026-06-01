# Usbip 飞鼠

把安卓手机变成体感飞鼠，通过 USB/IP 协议控制电脑光标。倾斜手机移动光标，
点击屏幕按钮模拟鼠标左右键，D-Pad 方向键遥控键盘操作。

基于 [usbipdcpp](https://github.com/yunsmall/usbipdcpp) 实现，导出虚拟 HID
复合设备（相对鼠标 + 键盘）。

## 功能

- **体感控制** — 陀螺仪控制光标移动，灵敏度可调
- **鼠标按键** — 左键 / 右键 / 中键，按住即持续按下
- **方向键圆盘** — 上下左右 + OK（回车），遥控器风格
- **滚轮** — 垂直滚动
- **前台服务** — 切到后台服务器不中断
- **可复用服务器** — 同一服务器可反复启停，无需重启 App

## 原理

App 在手机上启动 USB/IP 服务器，导出一个虚拟 USB HID 设备，包含两个接口：

| 接口 | 协议 | 端点 | 说明 |
|------|------|------|------|
| 0 | HID 鼠标（相对坐标） | 0x81 | 5 键鼠标，X/Y 相对移动 + 滚轮 |
| 1 | HID 键盘 | 0x82 | 标准引导键盘，支持媒体键 |

客户端通过 `usbip attach` 连接后，主机会识别到一个标准 USB HID 鼠标和键盘。

## 环境要求

### 手机端（服务器）

- Android 9.0+ (API 28)
- 陀螺仪传感器（用于体感追踪）
- [usbipdcpp](https://github.com/yunsmall/usbipdcpp)（以 submodule 方式包含）

### 电脑端（客户端）

- Linux 系统，安装 `usbip` 工具
- 或其他支持 USB/IP 客户端的操作系统

## 使用方法

1. **启动服务器** — 打开 App，设置端口（默认 3240），点击 **Start**
2. **电脑端连接** — 运行：
   ```bash
   sudo usbip attach -r <手机IP> -b 1-1
   ```
3. **操控** — 倾斜手机移动光标，屏幕按钮点击和键盘输入
4. **停止** — 点击 **Stop** 关闭服务器

## 编译

```bash
git clone --recurse-submodules https://github.com/yunsmall/Usbip-Flying-Mouse.git
```

用 Android Studio 打开，同步 Gradle，编译即可。

需要：
- Android NDK 29.0.14033849
- [vcpkg](https://github.com/microsoft/vcpkg)，设置 `VCPKG_ROOT` 环境变量
- vcpkg 管理的依赖：asio、spdlog

## 开源协议

本项目采用 [MIT 许可证](LICENSE) 开源。
