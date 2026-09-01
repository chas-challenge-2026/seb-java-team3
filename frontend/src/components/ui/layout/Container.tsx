import React from 'react'
import Styles from "./Container.module.css"

interface ContainerProps extends React.HTMLAttributes<HTMLElement>{
    children: React.ReactNode;
    maxWidth?: "sm" | "md" | "lg" | "xl";
    variant?: "default" | "surface" | "surface-nav"
}

const Container: React.FC<ContainerProps> = ({
    children,
    maxWidth = "lg",
    variant = "default",
    ...props
}) => {
  return (
    <div
        {...props}
        className={`
            ${Styles.container}
            ${Styles[maxWidth]}
            ${Styles[variant]}
            `}
    >
        {children}
    </div> 
  )
}

export default Container