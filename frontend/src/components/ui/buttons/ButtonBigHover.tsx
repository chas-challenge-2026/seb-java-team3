import React from 'react'
import Styles from "./ButtonBigHover.module.css"
import { ArrowRight } from "lucide-react";

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
        <div style={{display: "flex", alignItems: "center", justifyContent: "space-between"}}>
            {children}
            <ArrowRight size={20}/>
        </div>
    </button>
  )
}

export default ButtonBigHover