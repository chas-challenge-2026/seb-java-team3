import React from 'react'
import Styles from "./SideBarItem.module.css"

interface SidebarItemProps {
    label: string;
    icon?: React.ReactNode;
    badge?: number;
    active?: boolean;
    route: string;
}

const SideBarItem = ({
    label,
    icon,
    badge,
    active = false,
    route,
}: SidebarItemProps) => {
  return (
    <li className={Styles.item}>
        <a className={`${Styles.link} ${active ? Styles.active : ""}`} href={route}>
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
        </a>
    </li>
  )
}

export default SideBarItem