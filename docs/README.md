# Windows UI

Windows-like Android desktop environment with a local command shell, Android/ADB tooling, optional Termux bridge, Winlator EXE handoff, desktop/window manager, Explorer, Settings, notifications and USB OTG device discovery.

## Terminal bridges
- Built-in Windows-style commands execute locally inside the app sandbox / Android shell.
- `SU` enables root shell mode only when the host device already grants root to Windows UI.
- `ADB ...` uses the bundled ADB executable.
- `OTG DEVICES` enumerates Android USB Host devices.
- `OTG PERMISSION N` requests Android's normal USB user consent.
- `OTG ADB ...` runs ADB; USB access on a host device generally requires the host to have suitable USB access (root is used by the bridge when enabled).
- `TERMUX ...` uses Termux's documented RUN_COMMAND intent when Termux is installed and permits it.

Windows UI does not implement a universal root exploit or bypass Android/bootloader authorization.
