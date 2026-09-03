import Container from "../components/ui/layout/Container"
import SEBLogo from "../components/SEBLogo"
import Button from "../components/ui/buttons/Button"
import Divider from "../components/ui/layout/Divider"
import Input from "../components/ui/forms/Input"
import Select from "../components/ui/forms/Select"

export function NewPayment() {
    return <>
        <main style={{ backgroundColor: "var(--bg-surface)", minHeight: "100vh" }}>
            <Container maxWidth="xs" style={{display: "flex", justifyContent: "center", marginBottom: "1rem", marginTop: "2rem"}}>
                <SEBLogo size="md"/>
                
            </Container>
            <Container maxWidth="sm" variant="white">
                <h2 style={{textAlign: "center"}}>Ny betalning</h2>
                
                <Select
                    label="Konto"
                    options={[
                        {value: "driftkonto", label: "Driftkonto - SE4550000000058398257466 (2425000.00 SEK)"},
                        {value: "lönekonto", label: "Lönekonto - SE4550000000058398257466 (2425000.00 SEK)"},
                        {value: "projektkonto", label: "Projektkonto - SE4550000000058398257466 (2425000.00 SEK)"},
                    ]}
                />
                <Input label="Mottagar-IBAN" placeholder="SE45 5000 0000 0583 9825 7466"/>
                <Input label="Belopp (SEK)" placeholder="1000.00"/>
                <Input label="Referens" placeholder="Faktura #1234"/>

                <Divider shortWidth/>

                <div style={{display: "flex", gap: ".8rem", justifyContent: "center", marginTop: "1rem"}}>
                    <Button>Avbryt</Button>
                    <Button variant="primary" buttonStyle="icon-text" icon="check" >Skicka Betalning</Button>
                </div>
            </Container>
        </main>
    </>
}