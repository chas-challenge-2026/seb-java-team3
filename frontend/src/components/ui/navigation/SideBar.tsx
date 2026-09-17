import Styles from "./SideBar.module.css";
import SideBarItem from "./SideBarItem";
import UserAvatar from "../user/UserAvatar";
import SEBLogo from "../../SEBLogo";
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

  const handleLogout = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    clearToken();
    queryClient.clear();
    navigate({ to: "/login" });
  };

  return (
    <aside className={Styles.sideBar}>
      <div>
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            marginTop: "1rem",
            marginBottom: "2rem",
          }}
        >
          <SEBLogo size="sm" />
          <span style={{ marginBottom: ".5rem" }} />
          <h2 style={{ fontSize: "1.2rem" }}>Företagsbetalningar</h2>
          <hr style={{ width: "100%", marginTop: "1.5rem" }} />
        </div>

        <nav>
          <SideBarItem
            label="Översikt"
            icon={<LayoutDashboard size={iconSize} />}
          />
          <div style={{ marginBottom: "1rem" }} />
          {user && user.role !== "ATTESTANT" && (
            <SideBarItem
              label="Ny Betalning"
              icon={<CreditCard size={iconSize} />}
              route={"/payments/new"}
            />
          )}
          {user && user.role !== "INITIATOR" && (
            <SideBarItem
              label="Attestera"
              badge={approvalCount && approvalCount > 0 ? approvalCount : undefined}
              icon={<CreditCardCheck size={iconSize} />}
              route={"/attest"}
            />
          )}
          {user &&
            (user.role === "INITIATOR" ? (
              <SideBarItem
                label="Mina betalningar"
                icon={<Timeline size={iconSize} />}
                route={"/my-payments"}
              />
            ) : (
              <SideBarItem
                label="Historik"
                icon={<Timeline size={iconSize} />}
                route={"/audit"}
              />
            ))}
        </nav>
      </div>
      <div>
        <nav>
          <div style={{ marginBottom: "1rem" }} />
          <SideBarItem
            label="Inställningar"
            icon={<Settings size={iconSize} />}
          />
          <SideBarItem
            label="Logga ut"
            icon={<LogOut size={iconSize} />}
            route={"/login"}
            onClick={handleLogout}
          />
        </nav>
        <hr style={{ width: "100%", margin: "1rem 0" }} />
        <UserAvatar
          firstName="Marcus"
          lastName="Johansson"
          companyName="Malmö Bygg"
        />
      </div>
    </aside>
  );
};

export default SideBar;
