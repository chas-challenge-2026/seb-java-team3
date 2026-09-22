import { useEffect, useId, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";
import styles from "./Select.module.css";

interface SelectOption {
  value: string;
  label: string;
}

interface SelectProps {
  label?: string;
  options: SelectOption[];
  placeholder?: string;
  value: string;
  onChange: (value: string) => void;
  id?: string;
  name?: string;
  disabled?: boolean;
}

export default function Select({
  label,
  options,
  placeholder = "Välj ett alternativ",
  value,
  onChange,
  id,
  name,
  disabled = false,
}: SelectProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  const listboxId = `${selectId}-listbox`;
  const rootRef = useRef<HTMLDivElement>(null);
  const [isOpen, setIsOpen] = useState(false);
  const selectedIndex = options.findIndex((option) => option.value === value);
  const [highlightedIndex, setHighlightedIndex] = useState(Math.max(selectedIndex, 0));
  const selectedOption = options[selectedIndex];

  useEffect(() => {
    if (!isOpen) return;

    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setIsOpen(false);
    };

    document.addEventListener("pointerdown", closeOnOutsideClick);
    return () => document.removeEventListener("pointerdown", closeOnOutsideClick);
  }, [isOpen]);

  const open = () => {
    if (disabled) return;
    setHighlightedIndex(selectedIndex >= 0 ? selectedIndex : 0);
    setIsOpen(true);
  };

  const selectOption = (index: number) => {
    const option = options[index];
    if (!option) return;
    onChange(option.value);
    setHighlightedIndex(index);
    setIsOpen(false);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === "Escape") {
      setIsOpen(false);
      return;
    }

    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      if (!isOpen) {
        open();
        return;
      }
      const direction = event.key === "ArrowDown" ? 1 : -1;
      setHighlightedIndex((current) =>
        (current + direction + options.length) % options.length,
      );
      return;
    }

    if ((event.key === "Enter" || event.key === " ") && isOpen) {
      event.preventDefault();
      selectOption(highlightedIndex);
    }
  };

  return (
    <div className={styles.wrapper} ref={rootRef}>
      {label && <label className={styles.selectLabel} htmlFor={selectId}>{label}</label>}
      {name && <input type="hidden" name={name} value={value} />}

      <button
        id={selectId}
        type="button"
        className={`${styles.trigger}${isOpen ? ` ${styles.triggerOpen}` : ""}`}
        role="combobox"
        aria-controls={listboxId}
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        aria-activedescendant={isOpen ? `${selectId}-option-${highlightedIndex}` : undefined}
        disabled={disabled}
        onClick={() => (isOpen ? setIsOpen(false) : open())}
        onKeyDown={handleKeyDown}
      >
        <span className={selectedOption ? styles.value : styles.placeholder}>
          {selectedOption?.label ?? placeholder}
        </span>
        <ChevronDown className={styles.chevron} size={18} aria-hidden="true" />
      </button>

      {isOpen && (
        <div id={listboxId} className={styles.menu} role="listbox" aria-labelledby={selectId}>
          {options.map((option, index) => {
            const isSelected = option.value === value;
            const isHighlighted = index === highlightedIndex;
            return (
              <div
                id={`${selectId}-option-${index}`}
                key={option.value}
                className={`${styles.option}${isHighlighted ? ` ${styles.highlighted}` : ""}`}
                role="option"
                aria-selected={isSelected}
                onMouseEnter={() => setHighlightedIndex(index)}
                onMouseDown={(event) => {
                  event.preventDefault();
                  selectOption(index);
                }}
              >
                <span>{option.label}</span>
                {isSelected && <Check size={17} aria-hidden="true" />}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
