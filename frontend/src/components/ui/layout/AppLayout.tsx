import { Outlet, useRouterState } from "@tanstack/react-router";
import SideBar from "../navigation/SideBar";

function AppLayout() {
  const pathname = useRouterState({ select: (state) => state.location.pathname });

  return (
    <div className="appLayout">
      <SideBar />

      <main className="main">
        <div className="pageTransition" key={pathname}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}

export default AppLayout;
