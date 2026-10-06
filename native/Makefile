# Top-level Makefile for the native modules.
# Each module lives in its own subdirectory with its own Makefile.
# All build output ends up in native/build/ (libraries in native/build/lib/).
#
# Usage:
#   make              - Build everything (libraries and tests)
#   make lib          - Build all shared libraries
#   make test         - Run all unit tests
#   make check-deps   - Verify dependencies
#   make clean        - Remove build artifacts
#   make iban         - Build a single module (also: make test-iban)

MODULES = iban
BUILD_DIR := $(CURDIR)/build

.PHONY: all lib test check-deps clean help $(MODULES) $(addprefix test-,$(MODULES))

all lib test check-deps:
	@for m in $(MODULES); do $(MAKE) -C $$m BUILD_DIR=$(BUILD_DIR)/$$m LIB_DIR=$(BUILD_DIR)/lib $@ || exit 1; done

$(MODULES):
	$(MAKE) -C $@ BUILD_DIR=$(BUILD_DIR)/$@ LIB_DIR=$(BUILD_DIR)/lib lib

$(addprefix test-,$(MODULES)):
	$(MAKE) -C $(patsubst test-%,%,$@) BUILD_DIR=$(BUILD_DIR)/$(patsubst test-%,%,$@) LIB_DIR=$(BUILD_DIR)/lib test

clean:
	rm -rf $(BUILD_DIR)
	@echo "✓ Cleaned build artifacts"

help:
	@echo "Targets: all, lib, test, check-deps, clean, $(MODULES), test-<module>"
