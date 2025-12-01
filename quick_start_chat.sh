#!/bin/bash

# Quick Start Script for Click Chat Implementation
# This script helps you set up the chat functionality

set -e  # Exit on error

echo "===================================="
echo "Click Chat Quick Start Setup"
echo "===================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored messages
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Check if we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
    print_error "Please run this script from the Click project root directory"
    exit 1
fi

print_success "Found Click project"

# Step 1: Check Python installation
echo ""
echo "Step 1: Checking Python installation..."
if command -v python3 &> /dev/null; then
    PYTHON_VERSION=$(python3 --version)
    print_success "Python found: $PYTHON_VERSION"
else
    print_error "Python 3 is not installed. Please install Python 3.8 or higher"
    exit 1
fi

# Step 2: Set up Python virtual environment
echo ""
echo "Step 2: Setting up Python virtual environment..."
cd server
if [ ! -d "venv" ]; then
    python3 -m venv venv
    print_success "Created virtual environment"
else
    print_warning "Virtual environment already exists"
fi

# Activate virtual environment
source venv/bin/activate
print_success "Activated virtual environment"

# Step 3: Install Python dependencies
echo ""
echo "Step 3: Installing Python dependencies..."
pip install -q --upgrade pip
pip install -q -r requirements.txt
print_success "Installed Python dependencies"

# Step 4: Check for .env file
echo ""
echo "Step 4: Checking environment configuration..."
if [ ! -f ".env" ]; then
    print_warning ".env file not found. Creating template..."
    cat > .env << EOF
# Supabase Configuration
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-anon-key-here

# Google OAuth Configuration
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
EOF
    print_warning "Created .env template. Please update it with your credentials!"
    echo ""
    echo "To get your Supabase credentials:"
    echo "  1. Go to https://supabase.com/dashboard"
    echo "  2. Select your project"
    echo "  3. Go to Settings > API"
    echo "  4. Copy the URL and anon/public key"
    echo ""
else
    print_success ".env file found"
fi

# Check if .env has been configured
if grep -q "your-project.supabase.co" .env 2>/dev/null; then
    print_warning "Please update .env with your actual Supabase credentials"
fi

# Step 5: Check if refresh.json exists
echo ""
echo "Step 5: Checking refresh token storage..."
if [ ! -f "refresh.json" ]; then
    echo "[]" > refresh.json
    print_success "Created refresh.json"
else
    print_success "refresh.json exists"
fi

cd ..

# Step 6: Check Gradle setup
echo ""
echo "Step 6: Checking Gradle configuration..."
if [ -f "gradlew" ]; then
    chmod +x gradlew
    print_success "Gradle wrapper is ready"
else
    print_error "Gradle wrapper not found"
fi

# Step 7: Database setup instructions
echo ""
echo "Step 7: Database Setup"
echo "======================================"
echo "You need to run the SQL schema in your Supabase project:"
echo ""
echo "  1. Open your Supabase project dashboard"
echo "  2. Go to SQL Editor"
echo "  3. Copy the contents of: database/chat_schema.sql"
echo "  4. Paste and execute in the SQL Editor"
echo ""
read -p "Have you run the database schema? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_success "Database schema applied"
else
    print_warning "Don't forget to run the database schema!"
fi

# Step 8: Update Kotlin configuration
echo ""
echo "Step 8: Kotlin Configuration"
echo "======================================"
echo "Update the following file with your Supabase credentials:"
echo "  composeApp/src/commonMain/kotlin/compose/project/click/click/data/SupabaseConfig.kt"
echo ""
echo "Replace:"
echo '  private const val SUPABASE_URL = "your_supabase_url_here"'
echo '  private const val SUPABASE_ANON_KEY = "your_supabase_anon_key_here"'
echo ""
read -p "Have you updated SupabaseConfig.kt? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_success "SupabaseConfig.kt updated"
else
    print_warning "Don't forget to update SupabaseConfig.kt!"
fi

# Step 9: Final instructions
echo ""
echo "======================================"
echo "Setup Complete! 🎉"
echo "======================================"
echo ""
echo "Next Steps:"
echo ""
echo "1. Start the Python server:"
echo "   cd server"
echo "   source venv/bin/activate"
echo "   python app.py"
echo ""
echo "2. Build and run the Kotlin app:"
echo "   ./gradlew :composeApp:assembleDebug"
echo "   # Or open in Android Studio and run"
echo ""
echo "3. Test the chat functionality:"
echo "   - Create two user accounts"
echo "   - Create a connection between them"
echo "   - Start chatting!"
echo ""
echo "Documentation:"
echo "  - Full guide: CHAT_IMPLEMENTATION.md"
echo "  - Examples: examples/chat_usage_examples.py"
echo "  - Database schema: database/chat_schema.sql"
echo ""
print_success "Happy coding! 🚀"

