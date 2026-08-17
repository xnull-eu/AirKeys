# AirKeys

An Android application that lets you use your phone as a wireless numpad and function keys controller for your Windows PC.

## Features

- Virtual numpad (including operators) and function keys (F1-F12)
- Toggle between numpad and function keys
- Automatic server discovery on local network
- Automatic reconnection with keep-alive heartbeats
- Disconnect button to manually end a connection
- Connection status indicators
- Edge-to-edge display support
- Material Design 3 UI

## Requirements

- Android 6.0 (API level 23) or higher
- Device must be on the same network as the PC running the server

## Building

1. Clone the repository:
```
git clone https://github.com/xnull-eu/airkeys.git
```

2. Open the project in Android Studio

3. Build the project:
   - Click `Build > Make Project`
   - Or use the keyboard shortcut `Ctrl+F9` (Windows/Linux) or `Cmd+F9` (macOS)

## Usage

1. Install and run the Windows server application ([airkeys-server](https://github.com/xnull-eu/airkeys-server))
2. Launch AirKeys on your Android device
3. Tap "Scan for Devices" to find available servers
4. Select your server from the list
5. Use the numpad or function keys to control your PC

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Related Projects

- [airkeys-server](https://github.com/xnull-eu/airkeys-server) - The Windows server application for this Android client
