#!/usr/bin/env bash
#
# Checks for newer versions of dependencies, plugins and properties
# using the versions-maven-plugin. Results are written to separate
# files in the target/ directory.
#
set -euo pipefail

cd "$(dirname "$0")"

OUTPUT_DIR="target"
LINE_WIDTH=120
ENCODING="UTF-8"

mkdir -p "${OUTPUT_DIR}"

echo "Checking for dependency updates..."
mvn versions:display-dependency-updates \
  -Dversions.outputFile="${OUTPUT_DIR}/dependency-updates.txt" \
  -Dversions.outputLineWidth="${LINE_WIDTH}" \
  -Dversions.outputEncoding="${ENCODING}"

echo "Checking for plugin updates..."
mvn versions:display-plugin-updates \
  -Dversions.outputFile="${OUTPUT_DIR}/plugin-updates.txt" \
  -Dversions.outputLineWidth="${LINE_WIDTH}" \
  -Dversions.outputEncoding="${ENCODING}"

echo "Checking for property updates..."
mvn versions:display-property-updates \
  -Dversions.outputFile="${OUTPUT_DIR}/property-updates.txt" \
  -Dversions.outputLineWidth="${LINE_WIDTH}" \
  -Dversions.outputEncoding="${ENCODING}"

echo ""
echo "Done. Reports written to:"
echo "  ${OUTPUT_DIR}/dependency-updates.txt"
echo "  ${OUTPUT_DIR}/plugin-updates.txt"
echo "  ${OUTPUT_DIR}/property-updates.txt"