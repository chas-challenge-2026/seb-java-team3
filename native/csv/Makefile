# Makefile for CSV Parser (C/C++)
# Compiles CSV parser library and unit tests using Check framework
#
# Usage:
#   make              - Build everything (library and tests)
#   make test         - Run unit tests
#   make lib          - Build shared library only
#   make clean        - Remove built artifacts
#   make check-deps   - Verify Check framework is installed

CC = gcc
CFLAGS = -Wall -Wextra -Werror -std=c99 -O2 -fPIC -fopenmp
LDFLAGS = -shared -fopenmp

# Directories
SRC_DIR = .
BUILD_DIR ?= build
LIB_DIR ?= $(BUILD_DIR)/lib
TEST_DIR = $(BUILD_DIR)/test

# Files
PARSER_SRC = $(SRC_DIR)/csv_parser.c
PARSER_HEADER = $(SRC_DIR)/csv_parser.h
PARSER_OBJ = $(BUILD_DIR)/csv_parser.o
PARSER_LIB = $(LIB_DIR)/libcsvparser.so

TEST_SRC = $(SRC_DIR)/csv_parser_test.c
TEST_OBJ = $(BUILD_DIR)/csv_parser_test.o
TEST_EXE = $(TEST_DIR)/csv_parser_test

# Check framework configuration
CHECK_CFLAGS = $(shell pkg-config --cflags check 2>/dev/null || echo "-I/usr/include")
CHECK_LIBS = $(shell pkg-config --libs check 2>/dev/null || echo "-lcheck -lm -lpthread")

# Default target
.PHONY: all
all: $(PARSER_LIB) $(TEST_EXE)

# Create build directories
$(BUILD_DIR) $(LIB_DIR) $(TEST_DIR):
	mkdir -p $@

# Build validator object file
$(PARSER_OBJ): $(PARSER_SRC) $(PARSER_HEADER) | $(BUILD_DIR)
	$(CC) $(CFLAGS) -c $< -o $@

# Build shared library
$(PARSER_LIB): $(PARSER_OBJ) | $(LIB_DIR)
	$(CC) $(LDFLAGS) $(PARSER_OBJ) -o $@

# Build test object file
$(TEST_OBJ): $(TEST_SRC) $(PARSER_HEADER) | $(BUILD_DIR)
	$(CC) $(CFLAGS) $(CHECK_CFLAGS) -c $< -o $@

# Build test executable
$(TEST_EXE): $(TEST_OBJ) $(PARSER_OBJ) | $(TEST_DIR)
	$(CC) -fopenmp $(TEST_OBJ) $(PARSER_OBJ) $(CHECK_LIBS) -o $@

# Phony targets
.PHONY: lib
lib: $(PARSER_LIB)
	@echo "✓ Library built: $(PARSER_LIB)"

.PHONY: test
test: $(TEST_EXE)
	@echo "Running CSV parser unit tests..."
	$(TEST_EXE)

.PHONY: check-deps
check-deps:
	@echo "Checking for required dependencies..."
	@which gcc > /dev/null || (echo "ERROR: gcc not found" && exit 1)
	@pkg-config --exists check || (echo "WARNING: Check framework not found. Install with:" && echo "  Ubuntu/Debian: sudo apt-get install check" && echo "  macOS: brew install check" && echo "  Fedora: sudo dnf install check-devel")
	@echo "✓ Dependencies OK"

.PHONY: clean
clean:
	rm -rf $(BUILD_DIR)
	@echo "✓ Cleaned build artifacts"

.PHONY: help
help:
	@echo "CSV Parser - Makefile targets:"
	@echo ""
	@echo "  make              Build library and tests"
	@echo "  make lib          Build shared library only (libcsvparser.so)"
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
debug: CFLAGS = -Wall -Wextra -Werror -std=c99 -g -O0 -fPIC -fopenmp
debug: clean all
	@echo "✓ Built with debug symbols"
