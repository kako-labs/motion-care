#!/usr/bin/env python3
"""Compatibility entry point for the Gradle module dependency check."""
from pathlib import Path
import subprocess
import os
root = Path(__file__).resolve().parents[1]
wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
raise SystemExit(subprocess.call([str(root / wrapper), "verifyArchitecture"], cwd=root))
