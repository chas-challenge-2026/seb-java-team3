import React from 'react'
import Styles from "./Divider.module.css"

interface DividerProps {
  shortWidth?: boolean;
}

const Divider: React.FC<DividerProps> = ({
    shortWidth = false
}) => {
    return (
      <hr 
        className={`
          ${Styles.divider}
          ${shortWidth ? Styles.shortWidth : ""}
        `}
      />
    )
}

export default Divider