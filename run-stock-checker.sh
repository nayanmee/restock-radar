#!/bin/bash

# Amul Stock Radar - Smart Product Stock Monitoring System
# This script provides an easy way to run the stock checker with proper validation

set -e  # Exit on any error

# Configuration
JAR_FILE="target/restock-radar-1.0-SNAPSHOT.jar"
CONFIG_FILE="config.yml"
STATE_FILE="last-known-stock.json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}"
    echo "=================================="
    echo "   🛒 Amul Stock Radar v1.0"
    echo "   Smart Product Monitoring"
    echo "=================================="
    echo -e "${NC}"
}

# Function to check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        print_info "Please install Java 17 or later"
        exit 1
    fi
    
    # Check Java version
    java_version=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
    if [ "$java_version" -lt 17 ]; then
        print_error "Java 17 or later is required. Found Java $java_version"
        exit 1
    fi
    
    print_success "Java $java_version detected"
    
    # Check if JAR file exists
    if [ ! -f "$JAR_FILE" ]; then
        print_error "Application JAR file not found: $JAR_FILE"
        print_info "Please build the application first:"
        print_info "  mvn clean package"
        exit 1
    fi
    
    print_success "Application JAR found"
    
    # Check if config file exists
    if [ ! -f "$CONFIG_FILE" ]; then
        print_error "Configuration file not found: $CONFIG_FILE"
        print_info "Please ensure config.yml exists in the current directory"
        exit 1
    fi
    
    print_success "Configuration file found"
}

# Function to check environment variables
check_environment() {
    print_info "Checking environment variables..."
    
    local missing_vars=()
    
    if [ -z "$SMTP_USERNAME" ]; then
        missing_vars+=("SMTP_USERNAME")
    fi
    
    if [ -z "$SMTP_PASSWORD" ]; then
        missing_vars+=("SMTP_PASSWORD")
    fi
    
    if [ ${#missing_vars[@]} -gt 0 ]; then
        print_warning "Missing environment variables: ${missing_vars[*]}"
        print_info "Email notifications will be disabled"
        print_info "To enable notifications, set:"
        for var in "${missing_vars[@]}"; do
            print_info "  export $var='your_value'"
        done
        echo ""
        read -p "Continue without email notifications? (y/N): " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            print_info "Exiting. Set environment variables and try again."
            exit 1
        fi
    else
        print_success "Environment variables configured"
    fi
}

# Function to show current status
show_status() {
    print_info "Current system status:"
    echo "  📁 JAR File: $JAR_FILE"
    echo "  ⚙️  Config File: $CONFIG_FILE"
    echo "  💾 State File: $STATE_FILE"
    
    if [ -f "$STATE_FILE" ]; then
        state_size=$(wc -c < "$STATE_FILE" 2>/dev/null || echo "0")
        print_info "  📊 Previous state: ${state_size} bytes"
    else
        print_info "  📊 Previous state: None (first run)"
    fi
    
    echo ""
}

# Function to run the stock checker
run_stock_checker() {
    print_info "Starting Amul Stock Radar..."
    print_info "Monitoring protein products from shop.amul.com"
    echo ""
    
    # Define Java command with system property to allow restricted headers
    # This is crucial for avoiding bot detection on certain networks
    JAVA_CMD="java -Dsun.net.http.allowRestrictedHeaders=true -jar \"$JAR_FILE\" check-stock \"$CONFIG_FILE\""
    
    # Run the application and capture exit code
    if eval "$JAVA_CMD"; then
        print_success "Stock check completed successfully!"
        
        # Show state file info after run
        if [ -f "$STATE_FILE" ]; then
            state_size=$(wc -c < "$STATE_FILE")
            product_count=$(grep -o '"alias"' "$STATE_FILE" 2>/dev/null | wc -l || echo "0")
            print_info "Updated state file: $product_count products, $state_size bytes"
        fi
    else
        exit_code=$?
        print_error "Stock check failed with exit code: $exit_code"
        print_info "Check the logs above for error details"
        exit $exit_code
    fi
}

# Function to show help
show_help() {
    cat << EOF
Amul Stock Radar - Smart Product Stock Monitoring

USAGE:
    $0 [OPTIONS]

OPTIONS:
    -h, --help      Show this help message
    --check-only    Only check prerequisites, don't run
    --status        Show current system status
    --clean         Remove state file before running

ENVIRONMENT VARIABLES:
    SMTP_USERNAME   Email address for SMTP authentication
    SMTP_PASSWORD   Password or app-specific password for SMTP

EXAMPLES:
    # Normal run
    $0

    # Check system before running
    $0 --check-only

    # Clean start (reset state)
    $0 --clean

    # Set up Gmail notifications
    export SMTP_USERNAME="your.email@gmail.com"
    export SMTP_PASSWORD="your_app_password"
    $0

For detailed setup instructions, see README.md
EOF
}

# Main script logic
main() {
    print_header
    
    # Parse command line arguments
    case "${1:-}" in
        -h|--help)
            show_help
            exit 0
            ;;
        --check-only)
            check_prerequisites
            check_environment
            show_status
            print_success "All checks passed. Ready to run!"
            exit 0
            ;;
        --status)
            show_status
            exit 0
            ;;
        --clean)
            if [ -f "$STATE_FILE" ]; then
                print_warning "Removing existing state file: $STATE_FILE"
                rm "$STATE_FILE"
                print_success "State file removed. Next run will start fresh."
            else
                print_info "No state file to clean."
            fi
            shift
            ;;
        --*)
            print_error "Unknown option: $1"
            print_info "Use --help for usage information"
            exit 1
            ;;
    esac
    
    # Run all checks
    check_prerequisites
    check_environment
    show_status
    
    # Run the stock checker
    run_stock_checker
    
    print_success "🎉 Amul Stock Radar completed successfully!"
    print_info "Run this script regularly to monitor stock changes"
}

# Execute main function with all arguments
main "$@" 