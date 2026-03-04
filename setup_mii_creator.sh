#!/bin/bash
set -e

echo "Creating temp directory..."
TEMP_DIR="mii_temp_dir"
rm -rf "$TEMP_DIR"

echo "Cloning repository..."
git clone https://github.com/datkat21/mii-creator.git "$TEMP_DIR"

cd "$TEMP_DIR"

echo "Installing dependencies..."
if command -v bun &> /dev/null; then
    bun install
    bun run build || bun run build-ts || echo "Build step failed or not needed"
else
    npm install
    npm run build || echo "Build step failed or not needed"
fi

cd ..

echo "Creating assets directory..."
mkdir -p app/src/main/assets/miicreator

echo "Copying files..."
if [ -d "$TEMP_DIR/dist" ]; then
    cp -r "$TEMP_DIR/dist/"* app/src/main/assets/miicreator/
elif [ -d "$TEMP_DIR/build" ]; then
    cp -r "$TEMP_DIR/build/"* app/src/main/assets/miicreator/
else
    # Exclude .git and node_modules if copying everything
    rsync -av --exclude='.git' --exclude='node_modules' "$TEMP_DIR/" app/src/main/assets/miicreator/ || cp -r "$TEMP_DIR/"* app/src/main/assets/miicreator/
fi

echo "Cleaning up..."
rm -rf "$TEMP_DIR"

echo "Assets placed successfully."
