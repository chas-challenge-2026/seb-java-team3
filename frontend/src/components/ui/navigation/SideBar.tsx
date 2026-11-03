import Styles from "./SideBar.module.css";
import SideBarItem from "./SideBarItem";
import UserAvatar from "../user/UserAvatar";
import { useUser } from "../../../features/auth/useUser";
import { useLogout } from "../../../features/auth/useLogout";
import { useRouterState } from "@tanstack/react-router";
import { useState } from "react";
import { getSavedTheme, transitionTheme, type Theme } from "../../../lib/theme";

import {
  LayoutDashboard,
  CreditCard,
  CreditCardCheck,
  Timeline,
  Users,
  Moon,
  LogOut,
  Sun,
} from "lucide-react";
import { usePendingApprovalCount } from "../../../features/attest/useApprovals";
import { useChangedPaymentCount } from "../../../features/audit/useChangedPaymentCount";

const SideBar = () => {
  const { data: user } = useUser();
  const canReadApprovals = user?.role === "ATTESTANT" || user?.role === "ADMIN";
  const { data: approvalCount } = usePendingApprovalCount(canReadApprovals);
  const { data: changedPaymentCount } = useChangedPaymentCount(user?.role === "INITIATOR");
  const handleLogout = useLogout();
  const [theme, setTheme] = useState<Theme>(getSavedTheme);

  const iconSize = 16;
  const currentPath = useRouterState({ select: (state) => state.location.pathname });

  const handleThemeToggle = () => {
    const nextTheme: Theme = theme === "light" ? "dark" : "light";
    transitionTheme(nextTheme);
    setTheme(nextTheme);
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
          {user?.role === "INITIATOR" && (
            <SideBarItem
              label="Ny Betalning"
              icon={<CreditCard size={iconSize} />}
              route={"/payments/new"}
              active={currentPath === "/payments/new"}
            />
          )}
          {user && user.role !== "INITIATOR" && (
            <SideBarItem
              label={user.role === "ADMIN" ? "Attestöversikt" : "Attestera"}
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
                badge={changedPaymentCount && changedPaymentCount > 0 ? changedPaymentCount : undefined}
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
          {user?.role === "ADMIN" && (
            <SideBarItem
              label="Användare"
              icon={<Users size={iconSize} />}
              route={"/admin/users"}
              active={currentPath === "/admin/users"}
            />
          )}
          </ul>
        </nav>
      </div>
      <div className={Styles.bottomArea}>
        <nav aria-label="Kontomeny">
          <ul className={Styles.navList}>
          <SideBarItem
            label={theme === "light" ? "Mörkt läge" : "Ljust läge"}
            icon={theme === "light" ? <Moon size={iconSize} /> : <Sun size={iconSize} />}
            onClick={handleThemeToggle}
            iconOnlyOnMobile
          />
          <SideBarItem
            label="Logga ut"
            icon={<LogOut size={iconSize} />}
            onClick={handleLogout}
            tone="danger"
            iconOnlyOnMobile
          />
          </ul>
        </nav>
        <div className={Styles.divider} />
        <UserAvatar
          name={user?.name ?? "Användare"}
          companyName="Malmö Bygg"
          compact
        />
      </div>
    </aside>
  );
};

export default SideBar;
