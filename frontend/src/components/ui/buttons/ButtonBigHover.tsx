import React from 'react'
import Styles from "./ButtonBigHover.module.css"

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>{
    children: React.ReactNode;
    variant?: "primary" | "secondary" | "dark";
}

const ButtonBigHover: React.FC<ButtonProps> = ({
    children,
    variant = "dark",
    ...props
}) => {
  return (
    <button
        {...props}
        className={`
            ${Styles.btnBase}
            ${Styles[variant]}
        `}
    >
        {children}
    </button>
  )
}

export default ButtonBigHover