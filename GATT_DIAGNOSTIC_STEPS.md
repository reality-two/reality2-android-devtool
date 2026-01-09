# GATT Characteristic Registration Diagnosis

## Problem
Android app discovers the Reality2 GATT service `0000180A-0000-1000-8000-00805F9B34FB` but sees **0 characteristics**, even though the Rust server reports successful startup.

## Root Cause
BlueZ is accepting the service registration but silently failing to register the three characteristics. The `bluer` crate's `serve_gatt_application()` call succeeds without error, but characteristics don't appear in the BlueZ GATT server.

## Diagnostic Steps

### 1. Check BlueZ Status
On your Reality2 node server, run:
```bash
systemctl status bluetooth
```

Expected: `active (running)`. If not, start it:
```bash
sudo systemctl start bluetooth
```

### 2. Check BlueZ Version
```bash
bluetoothctl --version
```

The `bluer` crate version 0.17 may have compatibility issues with certain BlueZ versions. BlueZ 5.50+ is recommended.

### 3. Check BlueZ Logs for Errors
```bash
sudo journalctl -u bluetooth -f --since "5 minutes ago"
```

Look for errors like:
- `Failed to register service`
- `DBus error`
- `Characteristic registration failed`
- Permission denied

### 4. Inspect GATT Server via bluetoothctl
```bash
bluetoothctl

# In bluetoothctl:
menu gatt
list-attributes

# Or to see services:
menu advertise
list
```

This will show all registered GATT services and characteristics. Check if the Reality2 service `0000180a-0000-1000-8000-00805f9b34fb` appears and whether it has the 3 expected characteristics.

### 5. Check D-Bus Permissions
```bash
dbus-send --system --print-reply --dest=org.bluez / org.freedesktop.DBus.Introspectable.Introspect
```

If this fails with a permission error, BlueZ can't register GATT characteristics properly.

### 6. Verify Rust NIF is Running with Proper Permissions
```bash
# Check if the Elixir process has the right capabilities
ps aux | grep beam
# Note the PID, then:
sudo cat /proc/<PID>/status | grep Cap
```

### 7. Check BlueZ Configuration
```bash
cat /etc/bluetooth/main.conf | grep -v "^#" | grep -v "^$"
```

Look for:
- `[GATT]` section
- `Cache = yes` (might cause issues)
- Any `Disable` settings that could block characteristic registration

### 8. Enable BlueZ Debug Logging
Edit `/etc/bluetooth/main.conf` and add:
```
[General]
DebugKeys = true
```

Then restart BlueZ:
```bash
sudo systemctl restart bluetooth
```

Restart the Reality2 node and watch the logs:
```bash
sudo journalctl -u bluetooth -f
```

### 9. Test with bluetoothd in Debug Mode
Stop the service and run manually with debug output:
```bash
sudo systemctl stop bluetooth
sudo /usr/libexec/bluetooth/bluetoothd -n -d
# OR
sudo bluetoothd -n -d
```

Then start your Reality2 node in another terminal and watch for errors.

### 10. Check bluer Crate Version Compatibility
The Cargo.toml specifies `bluer = "=0.17"`. Try checking if there are known issues:
```bash
cd apps/ai_reality2_transnet/native/aireality2transnet
cargo search bluer
# Current version might be 0.18 or 0.19
```

Consider trying a newer version if available.

## Possible Fixes

### Fix 1: Update BlueZ
If BlueZ version is < 5.50:
```bash
sudo apt update
sudo apt upgrade bluez
```

### Fix 2: Disable GATT Cache
Edit `/etc/bluetooth/main.conf`:
```
[GATT]
Cache = no
```

Restart BlueZ:
```bash
sudo systemctl restart bluetooth
```

### Fix 3: Run with Elevated Permissions (Testing Only)
Try running the Elixir app with sudo to rule out permissions:
```bash
sudo iex -S mix
```

If characteristics appear when running as root, it's a permissions issue.

### Fix 4: Update bluer Crate
In `Cargo.toml`, try a newer version:
```toml
bluer = { version = "0.18", features = ["bluetoothd"] }
```

Then rebuild:
```bash
cd apps/ai_reality2_transnet/native/aireality2transnet
cargo clean
cargo build --release
```

### Fix 5: Add Diagnostic Logging to Rust Code
Add logging before and after characteristic registration to see exactly what BlueZ is doing. (I can help with this if needed.)

## Expected Result
After fixing, the Android app should see:
```
Service: 0000180a-0000-1000-8000-00805f9b34fb
  ├─ Characteristic: 00002a57-0000-1000-8000-00805f9b34fb [READ WRITE]
  ├─ Characteristic: 00002a58-0000-1000-8000-00805f9b34fb [READ WRITE]
  └─ Characteristic: 00002a59-0000-1000-8000-00805f9b34fb [READ NOTIFY]
```

## Next Steps
Run the diagnostic commands above and share:
1. BlueZ version
2. Any errors from `journalctl -u bluetooth`
3. Output from `bluetoothctl menu gatt list-attributes`
4. D-Bus test result

This will help identify the exact cause of the characteristic registration failure.
