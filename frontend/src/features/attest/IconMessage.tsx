import Styles from "./IconMessage.module.css"
import type { LucideIcon } from "lucide-react"

interface IconMessageProps {
    message: string;
    icon: LucideIcon;
    title?: string;
    variant?: "success" | "error";
}

const IconMessage = ({ message, icon: Icon, title, variant = "success" }: IconMessageProps) => {
  return (
    <section className={`${Styles.wrapper} ${Styles[variant]}`} aria-live="polite">
        <div className={Styles.iconWrapper}>
            <Icon size={42} strokeWidth={1.75} aria-hidden="true" />
        </div>
        {title && <h2 className={Styles.title}>{title}</h2>}
        <p className={Styles.message}>{message}</p>
    </section>
  )
}

export default IconMessage
