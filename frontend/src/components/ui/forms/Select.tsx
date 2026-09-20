import React from 'react'
import Styles from "./Select.module.css"

interface SelectOption {
    value: string;
    label: string;
}

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement>{
    label?: string;
    options: SelectOption[];
    placeholder?: string;
}

const Select: React.FC<SelectProps> = ({
    label,
    options,
    placeholder,
    ...props
}) => {
  return (
    <div className={Styles.wrapper}>
        {label && <label className={Styles.selectLabel}>{label}</label>}

        <select
            {...props}
            className={Styles.select}
        >
            {placeholder && (
                <option value="" disabled>
                    {placeholder}
                </option>
            )}
            {options.map((option) =>(
                <option
                    key={option.value}
                    value={option.value}
                    className={Styles.option}
                >
                    {option.label}
                </option>
            ))}

        </select>
    </div>
  )
}

export default Select
