import React from 'react'
import Styles from "./IconMessage.module.css"
import type { LucideIcon } from "lucide-react"
import Divider from '../../components/ui/layout/Divider'

interface IconMessageProps {
    message: string;
    icon: LucideIcon
}

const IconMessage = ({ message, icon: Icon }: IconMessageProps) => {
  return (
    <div className={Styles.wrapper}>
        <div className={Styles.iconWrapper}>
            <Icon size={175} strokeWidth={1}/>
        </div>
        <Divider/>
        <span style={{marginBottom: "1rem"}}/>
        <p className={Styles.message}>{message}</p>
    </div>
  )
}

export default IconMessage