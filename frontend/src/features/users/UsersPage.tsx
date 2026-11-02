import Container from "../../components/ui/layout/Container";
import UserList from "./UserList";

export default function UsersPage() {
  return (
    <main>
      <Container maxWidth="md">
        <UserList />
      </Container>
    </main>
  );
}
