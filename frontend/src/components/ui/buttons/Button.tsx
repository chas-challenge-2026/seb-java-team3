import React from 'react'
import Styles from "./Button.module.css"

import { Plus, Check, X, ChevronRight } from "lucide-react";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>{
    children?: React.ReactNode;
    fullWidth?: boolean;
    variant?: "primary" | "secondary" | "dark";
    buttonStyle?: "text-only" | "icon-only" | "icon-text";
    icon?: "plus" | "check" | "x" | "chevron";
}

const icons = {
    plus: Plus,
    check: Check,
    x: X,
    chevron: ChevronRight,
}

const Button: React.FC<ButtonProps> = ({
    children,
    fullWidth = false,
    variant = "secondary",
    buttonStyle = "text-only",
    icon = "chevron",
    ...props
}) => {

    const Icon = icons[icon];

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
            {buttonStyle !== "icon-only" && children}
            {buttonStyle !== "text-only" && <Icon size={18}/>}
        </button>
    )
}

export default Button