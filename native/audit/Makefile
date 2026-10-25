# Makefile for Audit Signer (C/C++)
# Compiles audit signer library and unit tests using Check framework
#
# Usage:
#   make              - Build everything (library and tests)
#   make test         - Run unit tests
#   make lib          - Build shared library only
#   make clean        - Remove built artifacts
#   make check-deps   - Verify gcc, OpenSSL and Check are installed

CC = gcc
CFLAGS = -Wall -Wextra -Werror -std=c99 -O2 -fPIC
LDFLAGS = -shared
LIBS = -lcrypto

# Directories
SRC_DIR = .
BUILD_DIR ?= build
LIB_DIR ?= $(BUILD_DIR)/lib
TEST_DIR = $(BUILD_DIR)/test

# Files
SIGNER_SRC = $(SRC_DIR)/audit_signer.c
SIGNER_HEADER = $(SRC_DIR)/audit_signer.h
SIGNER_OBJ = $(BUILD_DIR)/audit_signer.o
SIGNER_LIB = $(LIB_DIR)/libauditsigner.so

TEST_SRC = $(SRC_DIR)/audit_signer_test.c
TEST_OBJ = $(BUILD_DIR)/audit_signer_test.o
TEST_EXE = $(TEST_DIR)/audit_signer_test

# Check framework configuration
CHECK_CFLAGS = $(shell pkg-config --cflags check 2>/dev/null || echo "-I/usr/include")
CHECK_LIBS = $(shell pkg-config --libs check 2>/dev/null || echo "-lcheck -lm -lpthread")

# Default target
.PHONY: all
all: $(SIGNER_LIB) $(TEST_EXE)

# Create build directories
$(BUILD_DIR) $(LIB_DIR) $(TEST_DIR):
	mkdir -p $@

# Build signer object file
$(SIGNER_OBJ): $(SIGNER_SRC) $(SIGNER_HEADER) | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $< -o $@

# Build shared library
$(SIGNER_LIB): $(SIGNER_OBJ) | $(LIB_DIR)
	$(CC) $(LDFLAGS) $(SIGNER_OBJ) $(LIBS) -o $@

# Build test object file
$(TEST_OBJ): $(TEST_SRC) $(SIGNER_HEADER) | $(BUILD_DIR)
	$(CC) $(CFLAGS) $(CHECK_CFLAGS) -c $< -o $@

# Build test executable
$(TEST_EXE): $(TEST_OBJ) $(SIGNER_OBJ) | $(TEST_DIR)
	$(CC) $(TEST_OBJ) $(SIGNER_OBJ) $(CHECK_LIBS) $(LIBS) -o $@

# Phony targets
.PHONY: lib
lib: $(SIGNER_LIB)
	@echo "✓ Library built: $(SIGNER_LIB)"

.PHONY: test
test: $(TEST_EXE)
	@echo "Running audit signer unit tests..."
	$(TEST_EXE)

.PHONY: check-deps
check-deps:
	@echo "Checking for required dependencies..."
	@which gcc > /dev/null || (echo "ERROR: gcc not found" && exit 1)
	@pkg-config --exists openssl || (echo "ERROR: OpenSSL dev files not found. Install with: sudo apt-get install libssl-dev" && exit 1)
	@pkg-config --exists check || (echo "WARNING: Check framework not found. Install with:" && echo "  Ubuntu/Debian: sudo apt-get install check" && echo "  macOS: brew install check" && echo "  Fedora: sudo dnf install check-devel")
	@echo "✓ Dependencies OK"

.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)
	@echo "✓ Cleaned build artifacts"

.PHONY: help
help:
	@echo "Audit Signer - Makefile targets:"
	@echo ""
	@echo "  make              Build library and tests"
	@echo "  make lib          Build shared library only (libauditsigner.so)"
	@echo "  make test         Run unit tests"
	@echo "  make check-deps   Verify dependencies are installed"
	@echo "  make clean        Remove build artifacts"
	@echo "  make help         Show this help message"
	@echo ""
	@echo "Environment:"
	@echo "  CC=$(CC)"
	@echo "  CFLAGS=$(CFLAGS)"
	@echo "  CHECK_LIBS=$(CHECK_LIBS)"

.PHONY: debug
debug: CFLAGS = -Wall -Wextra -Werror -std=c99 -g -O0 -fPIC
debug: clean all
	@echo "✓ Built with debug symbols"
