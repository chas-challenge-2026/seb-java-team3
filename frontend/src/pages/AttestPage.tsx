import AttestGrid from "../features/attest/AttestGrid"
import Container from "../components/ui/layout/Container"

export default function AttestPage() {
    return (
        <main>
            <Container>
                <h1 style={{textAlign: "center", marginBottom: "2rem"}}>Attestkorg</h1>
                <AttestGrid />
            </Container>
        </main>
    )
}