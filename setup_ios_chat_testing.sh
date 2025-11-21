#!/bin/bash

# iOS Simulator Chat Testing Setup Script
# This script helps configure and test the chat functionality with Python API

set -e

echo "🚀 Click Chat API Testing Setup for iOS Simulator"
echo "=================================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Step 1: Get local IP address
echo "📡 Step 1: Detecting your Mac's local IP address..."
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "")

if [ -z "$LOCAL_IP" ]; then
    echo -e "${YELLOW}⚠️  Could not auto-detect IP address${NC}"
    echo "Please find your IP manually:"
    echo "  System Preferences > Network > Wi-Fi > Your IP address"
    echo ""
    read -p "Enter your Mac's local IP address: " LOCAL_IP
fi

echo -e "${GREEN}✅ Local IP: $LOCAL_IP${NC}"
echo ""

# Step 2: Check if Python server dependencies are installed
echo "🐍 Step 2: Checking Python server dependencies..."
cd server

if [ ! -d "venv" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv venv
fi

source venv/bin/activate

echo "Installing/updating dependencies..."
pip install -q -r requirements.txt

echo -e "${GREEN}✅ Python dependencies ready${NC}"
echo ""

# Step 3: Check .env configuration
echo "⚙️  Step 3: Checking Flask server configuration..."

if [ ! -f ".env" ]; then
    echo -e "${YELLOW}⚠️  .env file not found. Creating template...${NC}"
    cat > .env << EOF
SUPABASE_URL=your_supabase_url
SUPABASE_KEY=your_supabase_anon_key
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
EOF
    echo ""
    echo -e "${RED}❌ Please edit server/.env with your actual credentials${NC}"
    echo "   Then run this script again."
    exit 1
fi

# Check if .env has placeholder values
if grep -q "your_supabase_url" .env; then
    echo -e "${RED}❌ .env contains placeholder values${NC}"
    echo "   Please edit server/.env with your actual credentials"
    exit 1
fi

echo -e "${GREEN}✅ Server configuration looks good${NC}"
echo ""

# Step 4: Update Kotlin API configuration
echo "📱 Step 4: Updating iOS app API configuration..."
cd ..

# Update ApiConfig.kt with the detected IP
APICONFIG_FILE="composeApp/src/commonMain/kotlin/compose/project/click/click/data/api/ApiConfig.kt"

if [ -f "$APICONFIG_FILE" ]; then
    # Use sed to update the LOCAL_IP constant
    sed -i.bak "s/private const val LOCAL_IP = \".*\"/private const val LOCAL_IP = \"$LOCAL_IP\"/" "$APICONFIG_FILE"
    rm -f "${APICONFIG_FILE}.bak"
    echo -e "${GREEN}✅ Updated ApiConfig.kt with IP: $LOCAL_IP${NC}"
else
    echo -e "${YELLOW}⚠️  ApiConfig.kt not found at expected location${NC}"
fi

echo ""

# Step 5: Instructions summary
echo "📋 Next Steps:"
echo "=============="
echo ""
echo "1️⃣  Start the Flask server:"
echo "   cd server"
echo "   source venv/bin/activate"
echo "   python app.py"
echo ""
echo "   Server will be available at: http://$LOCAL_IP:5000"
echo ""
echo "2️⃣  Build and run the iOS app:"
echo "   ./gradlew :composeApp:build"
echo "   Then open in Xcode:"
echo "   open iosApp/iosApp.xcodeproj"
echo "   Select a simulator and click Run"
echo ""
echo "   Or use command line:"
echo "   xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' build"
echo ""
echo "3️⃣  Test the chat functionality:"
echo "   a) Create/login with two test users"
echo "   b) Create a connection between them"
echo "   c) Open Connections screen"
echo "   d) Send messages and verify they appear in real-time"
echo ""
echo "4️⃣  Monitor API calls:"
echo "   Watch Flask server logs to see API requests"
echo ""
echo -e "${GREEN}🎉 Setup complete! Happy testing!${NC}"
echo ""
echo "Troubleshooting:"
echo "  - If connection fails, verify Flask server is running"
echo "  - Check that both devices/simulators are on the same network"
echo "  - Review server logs for error messages"
echo "  - Ensure Supabase credentials in .env are correct"
echo ""

