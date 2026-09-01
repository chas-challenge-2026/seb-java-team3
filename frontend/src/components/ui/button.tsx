import React from 'react'
import Styles from "./button.module.css"

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>{
    children: React.ReactNode;
    variant?: "primary" | "secondary" | "dark";
    style?: "text-only" | "icon-only" | "icon-text";
    icon: null;
}

const button: React.FC<ButtonProps> = ({
    children,
    variant = "secondary",
    style = "text-only",
}) => {
  return (
    <button
        className={`
            ${Styles.btnBase}
            ${Styles[variant]}
            ${Styles[style]}
        `}
    >
        {children}
    </button>
  )
}

export default button