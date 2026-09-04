#!/usr/bin/env bash
set -e

export DISPLAY=:99
rm -f /tmp/.X99-lock /tmp/.X11-unix/X99

# Start Xvfb with GLX and render extensions
Xvfb :99 -screen 0 1280x1024x24 -ac +extension GLX +render -noreset &
XVFB_PID=$!

# Wait for Xvfb socket to become ready
for i in $(seq 1 30); do
    if [ -S /tmp/.X11-unix/X99 ]; then
        break
    fi
    sleep 0.1
done

# Run command and capture exit code
"$@"
EXIT_CODE=$?

# Cleanup Xvfb
kill -9 $XVFB_PID 2>/dev/null || true
exit $EXIT_CODE
