On macOS, the cleanest way to do this is with duti, a small command-line tool for setting default apps by UTI (Uniform Type Identifier).
Here's a script:
bash#!/usr/bin/env bash
#
# set-md-default-intellij.sh
# Set IntelliJ IDEA as the default app for Markdown files on macOS.

set -euo pipefail

# --- Config -----------------------------------------------------------------
# Adjust if you use IntelliJ IDEA Community ("com.jetbrains.intellij.ce")
# or a Toolbox-installed variant.
INTELLIJ_BUNDLE_ID="com.jetbrains.intellij"
# ----------------------------------------------------------------------------

# 1. Ensure duti is installed
if ! command -v duti >/dev/null 2>&1; then
  echo "duti not found. Installing via Homebrew..."
  if ! command -v brew >/dev/null 2>&1; then
    echo "Error: Homebrew is required. Install from https://brew.sh first." >&2
    exit 1
  fi
  brew install duti
fi

# 2. Verify IntelliJ is actually installed under that bundle ID
if ! mdfind "kMDItemCFBundleIdentifier == '${INTELLIJ_BUNDLE_ID}'" | grep -q .; then
  echo "Error: No app found with bundle ID ${INTELLIJ_BUNDLE_ID}." >&2
  echo "Find your bundle ID with:" >&2
  echo "  mdls -name kMDItemCFBundleIdentifier /Applications/IntelliJ\\ IDEA*.app" >&2
  exit 1
fi

# 3. Bind Markdown UTIs and common extensions to IntelliJ
#    "all" role = Viewer + Editor + Shell
EXTENSIONS=(md markdown mdown mkd mkdn)
UTIS=(net.daringfireball.markdown public.markdown)

for uti in "${UTIS[@]}"; do
  echo "Binding UTI ${uti} -> ${INTELLIJ_BUNDLE_ID}"
  duti -s "${INTELLIJ_BUNDLE_ID}" "${uti}" all
done

for ext in "${EXTENSIONS[@]}"; do
  echo "Binding .${ext} -> ${INTELLIJ_BUNDLE_ID}"
  duti -s "${INTELLIJ_BUNDLE_ID}" ".${ext}" all
done

echo
echo "Done. Verify with:"
echo "  duti -x md"
