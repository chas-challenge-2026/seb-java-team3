import Button from "../components/ui/buttons/Button"
import ButtonBigHover from "../components/ui/buttons/ButtonBigHover"
import Container from "../components/ui/layout/Container"

export function Login() {
    return <>

    <h1 style={{textAlign: "center", marginBottom: "3rem"}}>UI Component Tests</h1>
    <Container maxWidth="md">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <Button fullWidth variant="primary">Primary Button</Button>
            <Button fullWidth variant="secondary">Secondary Button</Button>
            <Button fullWidth variant="dark">Dark Button</Button>
        </div>
    </Container>
    <Container maxWidth="md" variant="surface">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <Button fullWidth variant="primary">Primary Button</Button>
            <Button fullWidth variant="dark">Dark Button</Button>
        </div>
    </Container>
    <Container maxWidth="xl">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <ButtonBigHover>Kontakta Oss</ButtonBigHover>
            <ButtonBigHover>Attestlista</ButtonBigHover>
            <ButtonBigHover>GDPR</ButtonBigHover>
        </div>
    </Container>
    </>
}