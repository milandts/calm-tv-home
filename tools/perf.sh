#!/usr/bin/env bash
# Misst Ruckler von TV Home bei einer festen Tastenfolge. Aufruf: perf.sh
set -u
adb shell input keyevent KEYCODE_HOME; sleep 4
adb shell dumpsys gfxinfo app.calmtv.home reset >/dev/null
for k in RIGHT RIGHT RIGHT RIGHT RIGHT LEFT LEFT LEFT LEFT LEFT; do adb shell input keyevent KEYCODE_DPAD_$k; sleep 0.5; done
adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1.5
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 0.5
adb shell input keyevent KEYCODE_DPAD_UP; sleep 1.5
adb shell input keyevent KEYCODE_DPAD_UP; sleep 1; adb shell input keyevent KEYCODE_DPAD_DOWN; sleep 1
adb shell dumpsys gfxinfo app.calmtv.home | tr -d '\r' | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number High input latency|Number Slow UI thread|Number Slow bitmap uploads|Number Slow issue draw commands'
echo "RAM:"; adb shell dumpsys meminfo app.calmtv.home | tr -d '\r' | grep -E 'TOTAL PSS|TOTAL:|Graphics' | head -3
