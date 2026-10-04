#!/bin/sh
# Lists the Material, Sound and Particle constants of a Bukkit, Spigot or Paper API jar in the format
# of core/src/testFixtures/resources/versions/*.txt, which the tests use to check that the default
# files work on that version.
#
# Usage: tools/list-api-names.sh <api.jar> "<description>" > core/src/testFixtures/resources/versions/<version>.txt
set -e
jar="$1"
if [ ! -f "$jar" ]; then
    echo "usage: $0 <api.jar> \"<description>\"" >&2
    exit 1
fi
constants() {
    javap -cp "$jar" "$1" 2>/dev/null | sed -n "s/^ *public static final $1 \([A-Z0-9_]*\);$/\1/p" | LC_ALL=C sort
}
echo "# The Material, Sound and Particle constants of $2, used by the tests to check"
echo "# that the default files work on this version. Generated with tools/list-api-names.sh."
echo "[materials]"
constants org.bukkit.Material
echo "[sounds]"
constants org.bukkit.Sound
echo "[particles]"
constants org.bukkit.Particle
