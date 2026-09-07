import styles from "./AttestGrid.module.css";
import { mock } from "./types";
import Button from "../../components/ui/buttons/Button";

export default function AttestGrid() {
  return (
    <div>
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
          {mock.map((m) => (
            <tr key={m.id}>
              <td>{m.mottagare}</td>
              <td>{m.typ}</td>
              <td>{m.belopp}:-</td>
              <td>{m.currency}</td>
              <td>{m.createdAt}</td>
              <td>{m.reference}</td>
              <td>
                <div className={styles.actions}>
                <Button className={styles.approve} buttonStyle="icon-only" icon="check" variant="secondary"/>
                <Button className={styles.reject} buttonStyle="icon-only" icon="x" variant="secondary"/>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
