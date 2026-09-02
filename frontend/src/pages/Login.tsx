import Button from "../components/ui/buttons/Button"
import ButtonBigHover from "../components/ui/buttons/ButtonBigHover"
import Container from "../components/ui/layout/Container"
import UserAvatar from "../components/ui/user/UserAvatar"
import Logo from "../components/SEBLogo"
import Divider from "../components/ui/layout/Divider"

export function Login() {
    return <>

    <Container maxWidth="md">
        <h1 style={{textAlign: "center", marginBottom: "2rem"}}>UI COMPONENT TEST</h1>
        <Divider/>
    </Container>
    <h3 style={{textAlign: "center"}}>Primary buttons</h3>
    <Container maxWidth="md">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <Button fullWidth variant="primary">Primary Button</Button>
            <Button fullWidth variant="secondary">Secondary Button</Button>
            <Button fullWidth variant="dark">Dark Button</Button>
        </div>
    </Container>

    <h3 style={{textAlign: "center"}}>Icon button variations</h3>
    <Container maxWidth="md">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <Button buttonStyle="text-only" icon="plus" variant="primary">Primary Button</Button>
            <Button buttonStyle="icon-text" icon="plus" variant="secondary">Secondary Button</Button>
            <Button buttonStyle="icon-only" icon="plus" variant="dark">Dark Button</Button>
        </div>
    </Container>

    <h3 style={{textAlign: "center"}}>Icon types</h3>
    <Container maxWidth="md">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <Button buttonStyle="icon-only" icon="plus" variant="secondary"/>
            <Button buttonStyle="icon-only" icon="x" variant="secondary"/>
            <Button buttonStyle="icon-only" icon="check" variant="secondary"/>
            <Button buttonStyle="icon-only" icon="chevron" variant="secondary"/>
        </div>
    </Container>

    <h3 style={{textAlign: "center"}}>Container with surface + full size buttons</h3>
    <Container maxWidth="md" variant="surface">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <Button fullWidth variant="primary">Primary Button</Button>
            <Button fullWidth variant="dark">Dark Button</Button>
        </div>
    </Container>

    <h3 style={{textAlign: "center"}}>Big hover buttons</h3>
    <Container maxWidth="xl">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <ButtonBigHover>Kontakta Oss</ButtonBigHover>
            <ButtonBigHover>Attestlista</ButtonBigHover>
            <ButtonBigHover>GDPR</ButtonBigHover>
        </div>
    </Container>

    <Container maxWidth="md">
        <Divider/>
    </Container>

    <h3 style={{textAlign: "center"}}>User components</h3>   
    <Container maxWidth="xl">
        <div style={{textAlign: "center"}}>
            <UserAvatar firstName="Marcus" lastName="Johansson"/>
        </div>
    </Container>

    <Container maxWidth="md">
        <Divider/>
    </Container>

    <h3 style={{textAlign: "center"}}>Branding assets</h3>
    <Container maxWidth="xl">
        <div style={{display: "flex", justifyContent: "center", gap: "1rem"}}>
            <Logo size="md"/>
        </div>
    </Container>

    <Container maxWidth="md">
        <Divider/>
    </Container>
    </>
}