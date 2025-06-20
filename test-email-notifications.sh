#!/bin/bash

# Email Notification Test Runner
# This script helps you test different email notification scenarios

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to print colored output
print_color() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

print_header() {
    echo ""
    print_color $CYAN "========================================"
    print_color $CYAN "$1"
    print_color $CYAN "========================================"
    echo ""
}

print_step() {
    print_color $BLUE "🔹 $1"
}

print_success() {
    print_color $GREEN "✅ $1"
}

print_warning() {
    print_color $YELLOW "⚠️  $1"
}

print_error() {
    print_color $RED "❌ $1"
}

# Check if environment variables are set
check_env_vars() {
    print_step "Checking environment variables..."
    
    if [[ -z "$SMTP_USERNAME" ]]; then
        print_error "SMTP_USERNAME environment variable not set"
        echo "   Set it with: export SMTP_USERNAME='your.email@gmail.com'"
        return 1
    fi
    
    if [[ -z "$SMTP_PASSWORD" ]]; then
        print_error "SMTP_PASSWORD environment variable not set"
        echo "   Set it with: export SMTP_PASSWORD='your_app_password'"
        return 1
    fi
    
    print_success "Environment variables configured"
    echo "   📧 Email: $SMTP_USERNAME"
    echo "   🔑 Password: [HIDDEN]"
    return 0
}

# Build the project
build_project() {
    print_step "Building project..."
    if mvn clean compile test-compile -q; then
        print_success "Project built successfully"
    else
        print_error "Project build failed"
        exit 1
    fi
}

# Run a specific test
run_test() {
    local test_method=$1
    local test_description=$2
    
    print_header "Running: $test_description"
    
    print_step "Executing test method: $test_method"
    
    # Run the specific test method
    if mvn test -Dtest="EmailNotificationIntegrationTest#$test_method" -q; then
        print_success "Test completed successfully!"
        print_color $PURPLE "📧 Check your email inbox for the notification"
    else
        print_error "Test execution failed"
        echo "   Check the console output above for error details"
    fi
}

# Show usage
show_usage() {
    print_header "Email Notification Test Runner"
    
    echo "This script helps you test email notifications with realistic product data."
    echo ""
    echo "Available test options:"
    echo ""
    print_color $GREEN "1. connection    - Test email connection only (no emails sent)"
    print_color $BLUE "2. restock       - Test in-stock notification (restock alert)"
    print_color $YELLOW "3. sellout       - Test out-of-stock notification (sell-out alert)"
    print_color $PURPLE "4. both          - Test both notification types for comparison"
    print_color $CYAN "5. comprehensive - Test with various stock levels (5 products)"
    print_color $RED "6. multiple      - Test multiple recipients functionality"
    echo ""
    echo "Usage examples:"
    echo "  ./test-email-notifications.sh connection"
    echo "  ./test-email-notifications.sh restock"
    echo "  ./test-email-notifications.sh multiple"
    echo "  ./test-email-notifications.sh both"
    echo ""
    echo "Prerequisites:"
    echo "  1. Set environment variables:"
    echo "     export SMTP_USERNAME='your.email@gmail.com'"
    echo "     export SMTP_PASSWORD='your_app_password'"
    echo ""
    echo "  2. Update recipient email in the test file:"
    echo "     Edit: src/test/java/com/radar/stock/notifiers/EmailNotificationIntegrationTest.java"
    echo "     Change: TEST_RECIPIENT constant to your email"
    echo ""
    echo "  3. Enable the test by removing @Disabled annotation from the test method"
    echo ""
}

# Main execution
main() {
    if [[ $# -eq 0 ]]; then
        show_usage
        exit 0
    fi
    
    local test_type=$1
    
    print_header "🧪 Email Notification Testing"
    
    # Check environment variables for all tests except help
    if [[ "$test_type" != "help" && "$test_type" != "--help" && "$test_type" != "-h" ]]; then
        if ! check_env_vars; then
            echo ""
            print_warning "Please set the required environment variables and try again"
            exit 1
        fi
        
        # Build project
        build_project
    fi
    
    case $test_type in
        "connection"|"conn")
            run_test "testEmailConnectionOnly" "Email Connection Test"
            ;;
        "restock"|"in-stock"|"instock")
            print_warning "Make sure to remove @Disabled annotation from the test method first!"
            echo ""
            run_test "testInStockNotificationWithRealProductData" "In-Stock Notification Test"
            ;;
        "sellout"|"out-of-stock"|"outofstock")
            print_warning "Make sure to remove @Disabled annotation from the test method first!"
            echo ""
            run_test "testOutOfStockNotificationWithRealProductData" "Out-of-Stock Notification Test"
            ;;
        "both"|"compare"|"comparison")
            print_warning "Make sure to remove @Disabled annotation from the test method first!"
            echo ""
            run_test "testBothNotificationTypesForComparison" "Both Notification Types Comparison"
            ;;
        "comprehensive"|"comp"|"all")
            print_warning "Make sure to remove @Disabled annotation from the test method first!"
            echo ""
            run_test "testComprehensiveNotificationScenarios" "Comprehensive Notification Test"
            ;;
        "multiple"|"multi"|"recipients")
            print_warning "Make sure to remove @Disabled annotation from the test method first!"
            echo ""
            run_test "testMultipleRecipientsInStockNotification" "Multiple Recipients Test"
            ;;
        "help"|"--help"|"-h")
            show_usage
            ;;
        *)
            print_error "Unknown test type: $test_type"
            echo ""
            show_usage
            exit 1
            ;;
    esac
}

# Execute main function with all arguments
main "$@" 