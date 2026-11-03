import AttestGrid from "../features/attest/AttestGrid"
import Container from "../components/ui/layout/Container"
import { useUser } from "../features/auth/useUser"

export default function AttestPage() {
    const { data: user } = useUser();
    return (
        <main>
            <Container maxWidth="xxl">
                <h1 style={{textAlign: "center", marginBottom: "2rem"}}>Attestkorg</h1>
                <AttestGrid readOnly={user?.role !== "ATTESTANT"} />
            </Container>
        </main>
    )
}
