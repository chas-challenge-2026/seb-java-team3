import React from 'react'
import Styles from "./Button.module.css"

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>{
    children: React.ReactNode;
    fullWidth?: boolean;
    variant?: "primary" | "secondary" | "dark";
    buttonStyle?: "text-only" | "icon-only" | "icon-text";
    // icon: null;
}

const Button: React.FC<ButtonProps> = ({
    children,
    fullWidth = false,
    variant = "secondary",
    buttonStyle = "text-only",
    ...props
}) => {
  return (
    <button
        {...props}
        className={`
            ${Styles.btnBase}
            ${Styles[variant]}
            ${Styles[buttonStyle]}
            ${fullWidth ? Styles.fullWidth : ""}
        `}
    >
        {children}
    </button>
  )
}

export default Button