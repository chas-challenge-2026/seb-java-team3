import React from 'react'
import Styles from "./SEBLogo.module.css"
import Logo from "../assets/seb-wordmark-logo.svg"

interface SEBLogoProps {
    size?: "sm" | "md" | "lg";
}

const SEBLogo: React.FC<SEBLogoProps> = ({
    size = "md",
}) => {
  return (
    <img 
        src={Logo} 
        alt="SEB Logo" 
        className={`${Styles.logo} ${Styles[size]}`} 
    />
  )
}

export default SEBLogo