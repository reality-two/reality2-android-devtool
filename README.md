# Reality2 Android DevTool

An Android developer tool for discovering, connecting to, and interacting with Reality2 nodes via BLE (Bluetooth Low Energy).

## Features

- **Scan & Discover**: Continuously scan for Reality2 nodes using ALTBeacon format (company ID: 0xFFFF)
- **Connect**: Connect to discovered nodes via GATT
- **Query Sentants**: Read all Sentants from a connected node (mirrors GraphQL `sentantAll`)
- **Send Events**: Send events/mutations to Sentants (mirrors GraphQL `sentantSend`)
- **Real-time Signals**: Subscribe to and monitor Sentant signals in real-time (mirrors GraphQL `awaitSignal`)

## Requirements

- Android device with BLE support
- Android 12+ (API 31+)
- Physical Reality2 node running `reality2-node-core-elixir`

## Reality2 BLE Protocol

This app implements the Reality2 BLE transient networking protocol:

### ALTBeacon Discovery
- **Company ID**: `0xFFFF`
- **Beacon Code**: `0xBEAC`
- **Payload**: 24 bytes containing UUID (node ID), major/minor version, and RSSI at 1m

### GATT Service
- **Service UUID**: `0000180A-0000-1000-8000-00805F9B34FB`

**Characteristics:**
1. **Query (Read)** - `00002A57-0000-1000-8000-00805F9B34FB`
   - Get all Sentants (sentantAll)
   - Returns JSON with sentant list

2. **Mutation (Write)** - `00002A58-0000-1000-8000-00805F9B34FB`
   - Send events to Sentants (sentantSend)
   - Accepts JSON mutation request

3. **Subscription (Notify)** - `00002A59-0000-1000-8000-00805F9B34FB`
   - Real-time signal stream (awaitSignal)
   - Emits JSON signal notifications

### JSON Protocol
- **Version**: "1.0"
- **Max Payload**: 512 bytes
- **Message Types**: `sentant_all_response`, `mutation_response`, `await_signal`, `error`

## Architecture

The app follows Clean Architecture with MVVM pattern:

```
┌─────────────────────┐
│   Presentation      │  Jetpack Compose UI + ViewModels
├─────────────────────┤
│   Domain            │  Use Cases (optional layer)
├─────────────────────┤
│   Data              │  Repository + BLE Manager
│                     │  ├─ BeaconScanner (ALTBeacon)
│                     │  ├─ GattClient (GATT operations)
│                     │  └─ Parsers (Beacon + JSON)
└─────────────────────┘
```

## Project Structure

```
com.reality2.devtool/
├── data/
│   ├── ble/              # BLE implementation
│   │   ├── BeaconScanner.kt
│   │   ├── GattClient.kt
│   │   └── BleManager.kt
│   ├── model/            # Data models
│   │   ├── BleCharacteristics.kt
│   │   ├── Reality2Node.kt
│   │   ├── Sentant.kt
│   │   └── JsonProtocol.kt
│   ├── parser/           # Parsers
│   │   ├── BeaconParser.kt
│   │   └── JsonParser.kt
│   └── repository/
│       └── Reality2Repository.kt
├── ui/
│   ├── screens/
│   │   ├── scanner/      # Node discovery
│   │   ├── sentantlist/  # Sentant browsing
│   │   └── signalmonitor/# Signal monitoring
│   ├── components/       # Reusable UI components
│   ├── navigation/       # Navigation graph
│   └── theme/            # Material 3 theme
└── util/                 # Utilities
```

## Building

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd reality2-android-devtool
   ```

2. Open in Android Studio (Hedgehog or later)

3. Sync Gradle and build:
   ```bash
   ./gradlew build
   ```

4. Run on a physical device (BLE not supported in emulators):
   ```bash
   ./gradlew installDebug
   ```

## Usage

1. **Launch the app** and grant Bluetooth permissions when prompted

2. **Scan for nodes**:
   - Tap the FAB (floating action button) to start scanning
   - Discovered Reality2 nodes will appear in the list
   - Each card shows node name, ID, signal strength, and connection status

3. **Connect to a node**:
   - Tap "Connect" on a node card
   - Once connected, tap the card to view Sentants

4. **Query Sentants**:
   - View all Sentants on the connected node
   - See available events and signals for each Sentant

5. **Send Events**:
   - Tap "Send Event" on a Sentant card
   - Select an event to send
   - The event is sent via the mutation characteristic

6. **Monitor Signals**:
   - Real-time signals appear automatically
   - Tap a signal to expand and see parameters
   - Clear the list with the Clear button

## Permissions

The app requires the following Android 12+ permissions:
- `BLUETOOTH_SCAN` (with `neverForLocation` flag)
- `BLUETOOTH_CONNECT`

## Troubleshooting

### No nodes discovered
- Ensure Reality2 nodes are running and advertising
- Check that Bluetooth is enabled
- Verify the node is using company ID `0xFFFF` and beacon code `0xBEAC`

### Connection failed
- Move closer to the node (improve RSSI)
- Ensure the Reality2 GATT service is running on the node
- Check logs with `adb logcat | grep Reality2`

### Cannot read Sentants
- Verify the GATT service UUID matches: `0000180A-0000-1000-8000-00805F9B34FB`
- Check that the query characteristic is readable
- Ensure the node is returning valid JSON

## Testing

The app can be tested with the Python GATT test client as a reference:
```bash
python reality2-node-core-elixir/apps/ai_reality2_transnet/test/test_gatt.py
```

## Development

### Dependencies
- Kotlin 1.9.10
- Jetpack Compose (Material 3)
- Kotlinx Serialization
- Timber (logging)
- Accompanist Permissions

### Key Files
- `BeaconParser.kt`: Parses ALTBeacon 24-byte payload
- `GattClient.kt`: Handles GATT connection, read/write/notify
- `JsonParser.kt`: Parses/encodes JSON protocol messages
- `Reality2Repository.kt`: Clean API for UI layer

## Related Projects

- [reality2-node-core-elixir](../reality2-node-core-elixir): Reality2 node implementation with BLE support

## License

[Specify license]

## Contributing

[Specify contribution guidelines]

## Contact

[Specify contact information]
