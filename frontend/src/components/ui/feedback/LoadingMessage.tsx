import { LoaderCircle } from "lucide-react"
import Styles from "./LoadingMessage.module.css"

interface LoadingMessageProps {
    message: string;
}

const LoadingMessage = ({ message }: LoadingMessageProps) => {
  return (
    <div className={Styles.wrapper} role="status">
        <LoaderCircle className={Styles.spinner} size={28} strokeWidth={1.75} aria-hidden="true" />
        <p className={Styles.message}>{message}</p>
    </div>
  )
}

export default LoadingMessage
