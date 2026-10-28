import { useEffect, useRef, type ReactNode } from "react"
import { X } from "lucide-react"
import Styles from "./Modal.module.css"

interface ModalProps {
    title: string;
    description?: string;
    onClose: () => void;
    // Falskt medan något pågår, så att rutan inte kan stängas mitt i ett anrop
    dismissible?: boolean;
    children: ReactNode;
}

// Öppnas när den renderas och stängs när den tas bort. Den inbyggda <dialog> ger
// mörk bakgrund, fokus som stannar i rutan och Esc för att stänga.
const Modal = ({ title, description, onClose, dismissible = true, children }: ModalProps) => {
    const dialogRef = useRef<HTMLDialogElement>(null)
    const onCloseRef = useRef(onClose)

    useEffect(() => {
        onCloseRef.current = onClose
    })

    useEffect(() => {
        const dialog = dialogRef.current
        if (!dialog) return
        // Webbläsaren kan stänga rutan själv trots att cancel stoppas (t.ex. Esc flera gånger).
        // close-händelsen kommer asynkront, så en sen händelse från en tidigare stängning
        // (StrictMode monterar två gånger) ignoreras om rutan hunnit öppnas igen
        const handleClose = () => {
            if (!dialog.open) onCloseRef.current()
        }
        dialog.addEventListener("close", handleClose)
        if (!dialog.open) dialog.showModal()
        return () => {
            dialog.removeEventListener("close", handleClose)
            dialog.close()
        }
    }, [])

    return (
        <dialog
            ref={dialogRef}
            className={Styles.modal}
            aria-labelledby="modal-title"
            aria-describedby={description ? "modal-description" : undefined}
            onCancel={(event) => {
                // Esc. Rutan stängs av föräldern genom att den slutar rendera den
                event.preventDefault()
                if (dismissible) onClose()
            }}
            onClick={(event) => {
                // Klick på bakgrunden landar på själva <dialog>, inte på innehållet
                if (event.target === event.currentTarget && dismissible) onClose()
            }}
        >
            <div className={Styles.content}>
                <header className={Styles.header}>
                    <div>
                        <h2 id="modal-title">{title}</h2>
                        {description && <p id="modal-description">{description}</p>}
                    </div>
                    <button
                        type="button"
                        className={Styles.close}
                        onClick={onClose}
                        disabled={!dismissible}
                        aria-label="Stäng"
                    >
                        <X size={20} aria-hidden="true" />
                    </button>
                </header>
                <div className={Styles.body}>{children}</div>
            </div>
        </dialog>
    )
}

export default Modal
