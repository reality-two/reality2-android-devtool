#!/bin/bash

echo "=== 1. Check what processes are using Bluetooth ==="
sudo lsof /dev/rfkill 2>/dev/null
ps aux | grep -E "blue|rfcomm|obex" | grep -v grep

echo ""
echo "=== 2. Check Bluetooth adapter status ==="
hciconfig -a

echo ""
echo "=== 3. Check what GATT services are registered ==="
echo "Enter bluetoothctl and run: menu gatt, then list-attributes"
echo "Press Ctrl+D to exit bluetoothctl when done"
bluetoothctl

echo ""
echo "=== 4. Check if adapter is being set to multiple modes ==="
sudo dbus-monitor --system "type='method_call',interface='org.bluez.Adapter1'" | head -50
