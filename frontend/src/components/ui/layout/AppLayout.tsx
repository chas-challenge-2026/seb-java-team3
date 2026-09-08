import { Outlet } from "@tanstack/react-router";
import SideBar from "../navigation/SideBar";

function AppLayout() {
  return (
    <div className="appLayout">
      <SideBar />

      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}

export default AppLayout;