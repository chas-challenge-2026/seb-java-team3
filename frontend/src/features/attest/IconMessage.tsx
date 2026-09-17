import React from 'react'
import Styles from "./IconMessage.module.css"
import type { LucideIcon } from "lucide-react"
import Container from '../../components/ui/layout/Container'
import Divider from '../../components/ui/layout/Divider'

interface IconMessageProps {
    message: string;
    icon: LucideIcon
}

const IconMessage = ({ message, icon: Icon }: IconMessageProps) => {
  return (
    <Container variant='white' maxWidth='sm'>
        <div className={Styles.wrapper}>
            <div style={{marginBottom: "1rem"}}>
                <Icon size={128} strokeWidth={1.5}/>
            </div>
            <Divider/>
            <span style={{marginBottom: "1rem"}}/>
            <p>{message}</p>
        </div>
    </Container>
  )
}

export default IconMessage