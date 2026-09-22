import React from 'react'
import { Link } from "@tanstack/react-router";
import Styles from "./SideBarItem.module.css"

type AppRoute = "/" | "/login" | "/payments/new" | "/attest" | "/my-payments" | "/audit";

interface SidebarItemProps {
    label: string;
    icon?: React.ReactNode;
    badge?: number;
    active?: boolean;
    route?: AppRoute;
    onClick?: () => void;
    tone?: "default" | "danger";
}

const SideBarItem = ({
    label,
    icon,
    badge,
    active = false,
    route,
    onClick,
    tone = "default",
}: SidebarItemProps) => {
  const className = `${Styles.link} ${active ? Styles.active : ""} ${tone === "danger" ? Styles.danger : ""}`;
  const content = (
    <>
      {icon && (
        <span className={Styles.icon}>
          {icon}
        </span>
      )}
      <span className={Styles.label}>
        {label}
      </span>
      {badge !== undefined && (
        <span className={Styles.badge}>
          {badge}
        </span>
      )}
    </>
  );

  return (
    <li className={Styles.item}>
        {route ? (
          <Link className={className} to={route} aria-current={active ? "page" : undefined}>
            {content}
          </Link>
        ) : (
          <button className={`${className} ${Styles.button}`} type="button" onClick={onClick}>
            {content}
          </button>
        )}
    </li>
  )
}

export default SideBarItem
