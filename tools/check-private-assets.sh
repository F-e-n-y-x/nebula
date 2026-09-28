#!/usr/bin/env bash
# Fails when a proprietary file is tracked by git: any *.dll (Lossless.dll belongs to THS /
# Lossless Scaling and may only reach the owner's private builds) or anything under a
# private-assets/ directory. Run from anywhere inside the repository; CI runs it on every push.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
tracked=$(git ls-files -- '*.dll' '*.DLL' '**/private-assets/**' 'private-assets/**')
if [ -n "$tracked" ]; then
  echo "error: proprietary files are tracked by git (private builds only; see .gitignore):" >&2
  echo "$tracked" >&2
  exit 1
fi
# Submodules are separate repositories; check the vendored lsfg-vk copy as well.
lsfg=framegen/src/main/cpp/lsfg-vk-android
if [ -e "$lsfg/.git" ]; then
  sub=$(git -C "$lsfg" ls-files -- '*.dll' '*.DLL' || true)
  if [ -n "$sub" ]; then
    echo "error: $lsfg tracks DLLs:" >&2
    echo "$sub" >&2
    exit 1
  fi
fi
echo "ok: no proprietary files tracked"
