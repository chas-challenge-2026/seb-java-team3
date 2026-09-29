import React from 'react'
import { Link, type LinkProps } from "@tanstack/react-router";
import Styles from "./Button.module.css"

// Navigering som ser ut som en Button. Använd Button för handlingar, ButtonLink för att byta sida.
interface ButtonLinkProps {
    to: LinkProps["to"];
    children: React.ReactNode;
    variant?: "primary" | "secondary" | "dark";
    className?: string;
}

const ButtonLink = ({
    to,
    children,
    variant = "secondary",
    className,
}: ButtonLinkProps) => {
    return (
        <Link
            to={to}
            className={`
                ${Styles.btnBase}
                ${Styles[variant]}
                ${className ?? ""}
            `}
        >
            {children}
        </Link>
    )
}

export default ButtonLink
