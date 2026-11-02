import Container from "../../components/ui/layout/Container";
import UserList from "./UserList";

export default function UsersPage() {
  return (
    <main>
      <Container maxWidth="md">
        <h1 style={{textAlign: "center", marginBottom: "2rem"}}>Användare</h1>
        <UserList />
      </Container>
    </main>
  );
}
