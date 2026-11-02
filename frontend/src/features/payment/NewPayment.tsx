import Container from "../../components/ui/layout/Container";
import PaymentForm from "./PaymentForm";

export function NewPayment() {
  return (
    <>
      {/* Container ger samma övre marginal som titeln har på övriga sidor */}
      <Container maxWidth="xxl">
        <h1 style={{textAlign: "center", marginBottom: "0"}}>Ny betalning</h1>
      </Container>
      <PaymentForm />
    </>
  );
}
