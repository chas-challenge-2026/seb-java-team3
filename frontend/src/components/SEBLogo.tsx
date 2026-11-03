import React from 'react'
import Styles from "./SEBLogo.module.css"
import Logo from "../assets/seb-wordmark-logo.svg"

interface SEBLogoProps extends React.ImgHTMLAttributes<HTMLImageElement> {
    size?: "sm" | "md" | "lg";
}

const SEBLogo: React.FC<SEBLogoProps> = ({
    size = "md",
    ...props
}) => {
  return (
    <img 
        {...props}
        src={Logo} 
        alt="SEB Logo" 
        className={`${Styles.logo} ${Styles[size]}`} 
    />
  )
}

export default SEBLogo