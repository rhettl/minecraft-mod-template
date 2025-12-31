#!/bin/bash
set -e

TEST_STRUCTS="structures"
MC_DIR="fabric/versions/1.21.1/run/saves/New World/generated/minecraft"

rm -rf "$MC_DIR/structures/"
rm -rf "$MC_DIR/backups/"

cp -r $TEST_STRUCTS "$MC_DIR/structures"