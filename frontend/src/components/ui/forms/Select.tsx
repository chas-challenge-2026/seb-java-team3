import React from 'react'
import Styles from "./Select.module.css"

interface SelectOption {
    value: string;
    label: string;
}

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement>{
    label?: string;
    options: SelectOption[];
}

const Select: React.FC<SelectProps> = ({
    label,
    options,
    ...props
}) => {
  return (
    <div className={Styles.wrapper}>
        {label && <label className={Styles.selectLabel}>{label}</label>}

        <select
            {...props}
            className={Styles.select}
        >
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