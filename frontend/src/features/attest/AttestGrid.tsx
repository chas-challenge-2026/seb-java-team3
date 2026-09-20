import styles from "./AttestGrid.module.css";
import Button from "../../components/ui/buttons/Button";
import { useApprovals } from "./useApprovals";
import IconMessage from "./IconMessage";
import { PartyPopper, TriangleAlert } from "lucide-react"
import Container from "../../components/ui/layout/Container";

export default function AttestGrid() {
  const { data, isLoading, isError, approve } = useApprovals();

  if (isLoading) return <p>Laddar…</p>;
  if (isError) return <IconMessage message="Kunde inte hämta attestkorgen!" icon={TriangleAlert}/>;
  if (!data?.length) return <IconMessage message="Inget väntar på ditt godkännande." icon={PartyPopper}/>;
  return (
    <Container variant="white">
      <table className={styles.grid}>
        <thead>
          <tr>
            <th>Mottagare</th>
            <th>Typ</th>
            <th>Belopp</th>
            <th>Valuta</th>
            <th>Datum</th>
            <th>Referens</th>
            <th>Åtgärd</th>
          </tr>
        </thead>
        <tbody>
          {data.map((m) => {
            const pending = approve.isPending && approve.variables === m.id;
            return (
              <tr key={m.id}>
                <td>{m.mottagare}</td>
                <td>{m.typ}</td>
                <td>{m.belopp}:-</td>
                <td>{m.currency}</td>
                <td>{m.createdAt}</td>
                <td>{m.reference}</td>
                <td>
                  <div className={styles.actions}>
                    <Button
                      onClick={() => approve.mutate(m.id)}
                      disabled={pending}
                      className={styles.approve}
                      buttonStyle="icon-only"
                      icon="check"
                      variant="primary"
                    >
                      {pending ? "Godkänner..." : "Godkän"}
                    </Button>
                    <Button
                      className={styles.reject}
                      buttonStyle="icon-only"
                      icon="x"
                      variant="secondary"
                    />
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Container>
  );
}
