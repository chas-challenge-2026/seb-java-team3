import React from 'react'
import Styles from "./Input.module.css"

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement>{
    label?: string;
}

const Input: React.FC<InputProps> = ({
    label,
    ...props
}) => {
  return (
    <div className={Styles.wrapper}>
        {label && <label className={Styles.inputLabel}>{label}</label>}
        <input
            {...props}
            className={Styles.inputField}
        />
    </div>
  )
}

export default Input