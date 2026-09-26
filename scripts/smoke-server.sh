#!/usr/bin/env bash
set -euo pipefail

mkdir -p run
rm -f run/logs/latest.log
printf 'eula=true\n' > run/eula.txt

timeout 300s bash gradlew --no-daemon runServer > server-start.log 2>&1 &
server_pid=$!

for _ in $(seq 1 300); do
    if [[ -f run/logs/latest.log ]] && grep -q 'Done (' run/logs/latest.log; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
        echo 'Dedicated server reached the ready state.'
        exit 0
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        cat server-start.log
        exit 1
    fi
    sleep 1
done

cat server-start.log
exit 1
