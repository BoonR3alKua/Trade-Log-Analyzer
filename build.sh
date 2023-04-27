#!/bin/bash
# Build script for Trade Log Analyzer & Alert Engine
# Usage: ./build.sh or bash build.sh

echo "======================================================================"
echo "Trade Log Analyzer - Build Script"
echo "======================================================================"

# Check if Java is installed
if ! command -v javac &> /dev/null; then
    echo "ERROR: Java compiler (javac) not found!"
    echo "Please install a Java JDK and add it to your PATH"
    exit 1
fi

# Display Java version
echo "Checking Java version..."
javac -version

echo ""
echo "Creating bin directory..."
mkdir -p bin

echo ""
echo "Compiling source files..."
echo ""

# Compile all Java files
javac --release 8 -d bin -encoding UTF-8 src/*.java

if [ $? -eq 0 ]; then
    echo ""
    echo "======================================================================"
    echo "BUILD SUCCESSFUL!"
    echo "======================================================================"
    echo ""
    echo "Next steps:"
    echo "  1. Run the analyzer:"
    echo "     java -cp bin LogAnalyzer trade_log.txt"
    echo ""
    echo "  2. In another terminal, run the log generator:"
    echo "     java -cp bin LogGeneratorUtility trade_log.txt"
    echo ""
    echo "For more information, see README.md"
    echo ""
else
    echo ""
    echo "======================================================================"
    echo "BUILD FAILED!"
    echo "======================================================================"
    echo "Please check the errors above"
    exit 1
fi
