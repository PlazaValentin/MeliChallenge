#!/usr/bin/env bash
set -e

echo "Checking required tools..."

if ! command -v java &>/dev/null; then
  echo "ERROR: Java is not installed or not on PATH." >&2
  exit 1
fi

if ! command -v gradle &>/dev/null; then
  echo "ERROR: Gradle is not installed or not on PATH." >&2
  exit 1
fi

if ! command -v node &>/dev/null; then
  echo "ERROR: Node.js is not installed or not on PATH." >&2
  exit 1
fi

echo "All required tools are present."
