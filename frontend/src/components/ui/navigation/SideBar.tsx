import Styles from "./SideBar.module.css";
import SideBarItem from "./SideBarItem";
import UserAvatar from "../user/UserAvatar";
import { useUser } from "../../../features/auth/useUser";
import { clearToken } from "../../../lib/authToken";
import { useNavigate } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";

import {
  LayoutDashboard,
  CreditCard,
  CreditCardCheck,
  Timeline,
  Settings,
  LogOut,
} from "lucide-react";
import { usePendingApprovalCount } from "../../../features/attest/useApprovals";

const SideBar = () => {
  const { data: user } = useUser();
  const canApprove = user?.role === "ATTESTANT" || user?.role === "ADMIN";
  const { data: approvalCount } = usePendingApprovalCount(canApprove);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const iconSize = 16;
  const currentPath = window.location.pathname;

  const handleLogout = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    clearToken();
    queryClient.clear();
    navigate({ to: "/login" });
  };

  return (
    <aside className={Styles.sideBar}>
      <div>
        <div className={Styles.brand}>
          <h2><span>SEB</span> Företagsbetalningar</h2>
        </div>

        <nav aria-label="Huvudmeny">
          <ul className={Styles.navList}>
          <SideBarItem
            label="Översikt"
            icon={<LayoutDashboard size={iconSize} />}
            route="/"
            active={currentPath === "/"}
          />
          {user && user.role !== "ATTESTANT" && (
            <SideBarItem
              label="Ny Betalning"
              icon={<CreditCard size={iconSize} />}
              route={"/payments/new"}
              active={currentPath === "/payments/new"}
            />
          )}
          {user && user.role !== "INITIATOR" && (
            <SideBarItem
              label="Attestera"
              badge={approvalCount && approvalCount > 0 ? approvalCount : undefined}
              icon={<CreditCardCheck size={iconSize} />}
              route={"/attest"}
              active={currentPath === "/attest"}
            />
          )}
          {user &&
            (user.role === "INITIATOR" ? (
              <SideBarItem
                label="Mina betalningar"
                icon={<Timeline size={iconSize} />}
                route={"/my-payments"}
                active={currentPath === "/my-payments"}
              />
            ) : (
              <SideBarItem
                label="Historik"
                icon={<Timeline size={iconSize} />}
                route={"/audit"}
                active={currentPath === "/audit"}
              />
            ))}
          </ul>
        </nav>
      </div>
      <div className={Styles.bottomArea}>
        <nav aria-label="Kontomeny">
          <ul className={Styles.navList}>
          <SideBarItem
            label="Inställningar"
            icon={<Settings size={iconSize} />}
          />
          <SideBarItem
            label="Logga ut"
            icon={<LogOut size={iconSize} />}
            route={"/login"}
            onClick={handleLogout}
            tone="danger"
          />
          </ul>
        </nav>
        <div className={Styles.divider} />
        <UserAvatar
          name={user?.name ?? "Användare"}
          companyName="Malmö Bygg"
        />
      </div>
    </aside>
  );
};

export default SideBar;
