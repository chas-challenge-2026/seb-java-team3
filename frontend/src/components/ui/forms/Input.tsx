import React, { useId, useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import styles from "./Input.module.css";

type TextInputProps = React.InputHTMLAttributes<HTMLInputElement> & {
  label?: string;
  error?: string;
  ref?: React.Ref<HTMLInputElement>;
};

const Input = ({
  label,
  error,
  id,
  name,
  type,
  className = "",
  ref,
  ...inputProps
}: TextInputProps) => {
  const reactId = useId();
  const inputId = id ?? name ?? reactId;
  const isPassword = type === "password";
  const [revealed, setRevealed] = useState(false);

  return (
    <div className={`${styles.wrapper}${error ? ` ${styles.fieldError}` : ""}`}>
      {label && (
        <label className={styles.inputLabel} htmlFor={inputId}>
          {label}
        </label>
      )}
      <div className={styles.wrap}>
        <input
          ref={ref}
          id={inputId}
          name={name}
          type={isPassword ? (revealed ? "text" : "password") : type}
          className={`${styles.inputField}${isPassword ? ` ${styles.inputPassword}` : ""}${error ? ` ${styles.inputError}` : ""}${className ? ` ${className}` : ""}`}
          aria-invalid={!!error}
          aria-describedby={error ? `${inputId}-error` : undefined}
          {...inputProps}
          />
        {isPassword && (
          <button
          type="button"
          className={styles.toggle}
          onClick={() => setRevealed((v) => !v)}
          aria-label={revealed ? "Dölj lösenord" : "Visa lösenord"}
          >
            {revealed ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        )}
      </div>
      {error && (
        <p id={`${inputId}-error`} className={styles.errorMsg}>
          {error}
        </p>
      )}
    </div>
  );
};
 export default Input;