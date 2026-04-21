#!/bin/bash

# Quick Start Script for iOS Chat Testing
# This script automates the entire setup process

echo "🚀 Click Chat - iOS Testing Quick Start"
echo "========================================"
echo ""

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Step 1: Get local IP
echo -e "${BLUE}Step 1: Getting your Mac's IP address...${NC}"
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "")

if [ -z "$LOCAL_IP" ]; then
    echo -e "${YELLOW}Could not auto-detect IP. Please enter manually:${NC}"
    read -p "Your Mac's local IP address: " LOCAL_IP
fi

echo -e "${GREEN}✓ IP Address: $LOCAL_IP${NC}"
echo ""

# Step 2: Update API configuration
echo -e "${BLUE}Step 2: Updating API configuration...${NC}"
APICONFIG_FILE="composeApp/src/commonMain/kotlin/compose/project/click/click/data/api/ApiConfig.kt"

if [ -f "$APICONFIG_FILE" ]; then
    sed -i.bak "s/private const val LOCAL_IP = \".*\"/private const val LOCAL_IP = \"$LOCAL_IP\"/" "$APICONFIG_FILE"
    rm -f "${APICONFIG_FILE}.bak"
    echo -e "${GREEN}✓ Updated ApiConfig.kt${NC}"
else
    echo -e "${YELLOW}⚠️  ApiConfig.kt not found${NC}"
fi
echo ""

# Step 3: Setup Python environment
echo -e "${BLUE}Step 3: Setting up Python environment...${NC}"
cd server

if [ ! -d "venv" ]; then
    python3 -m venv venv
    echo -e "${GREEN}✓ Created virtual environment${NC}"
fi

source venv/bin/activate
pip install -q --upgrade pip
pip install -q -r requirements.txt
echo -e "${GREEN}✓ Python dependencies installed${NC}"
echo ""

# Step 4: Check environment variables
echo -e "${BLUE}Step 4: Checking server configuration...${NC}"

if [ ! -f ".env" ]; then
    echo -e "${RED}❌ .env file not found!${NC}"
    echo "Creating template .env file..."
    cat > .env << 'EOF'
SUPABASE_URL=your_supabase_url_here
SUPABASE_KEY=your_supabase_anon_key_here
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
EOF
    echo -e "${YELLOW}⚠️  Please edit server/.env with your actual Supabase credentials${NC}"
    echo "   Then run this script again or start the server manually."
    exit 1
fi

# Check if .env has placeholder values
if grep -q "your_supabase_url_here" .env; then
    echo -e "${RED}❌ .env has placeholder values${NC}"
    echo "Please update server/.env with real credentials"
    exit 1
fi

echo -e "${GREEN}✓ Configuration looks good${NC}"
echo ""

# Step 5: Create test data (optional)
echo -e "${BLUE}Step 5: Test data setup${NC}"
read -p "Do you want to create test users and connections? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Creating test data..."
    python create_test_data.py
    echo ""
fi

cd ..

# Step 6: Build the iOS app
echo -e "${BLUE}Step 6: Building iOS app...${NC}"
echo "This may take a few minutes..."
./gradlew :composeApp:build --quiet

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build successful${NC}"
else
    echo -e "${RED}❌ Build failed. Check the output above for errors.${NC}"
    exit 1
fi
echo ""

# Step 7: Final instructions
echo ""
echo "=========================================="
echo -e "${GREEN}✅ Setup Complete!${NC}"
echo "=========================================="
echo ""
echo -e "${YELLOW}📋 Next Steps:${NC}"
echo ""
echo "1️⃣  Start the Flask server (in a new terminal):"
echo "   cd server"
echo "   source venv/bin/activate"
echo "   python app.py"
echo ""
echo "   Server will be at: http://$LOCAL_IP:5000"
echo ""
echo "2️⃣  Run the iOS app:"
echo "   Option A - Xcode:"
echo "     open iosApp/iosApp.xcodeproj"
echo "     Select iPhone simulator and click Run"
echo ""
echo "   Option B - Command line:"
echo "     cd iosApp"
echo "     SIM_ID=\$(xcrun simctl list devices available | awk '/iPhone/ { if (match(\$0, /[0-9A-F-]{36}/)) { print substr(\$0, RSTART, RLENGTH); exit } }')"
echo "     xcodebuild -scheme iosApp -sdk iphonesimulator -destination \"id=\$SIM_ID\" run"
echo ""
echo "3️⃣  Test the chat:"
echo "   - Login/signup with test users"
echo "   - Navigate to Connections screen"
echo "   - Open a chat and send messages"
echo "   - Watch messages appear in real-time!"
echo ""
echo -e "${BLUE}📚 Documentation:${NC}"
echo "   - Full testing guide: IOS_CHAT_TESTING_GUIDE.md"
echo "   - API documentation: CHAT_API_INTEGRATION.md"
echo "   - Quick reference: CHAT_API_QUICK_START.md"
echo ""
echo -e "${GREEN}Happy testing! 🎉${NC}"
echo ""

