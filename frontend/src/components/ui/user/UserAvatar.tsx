import React from 'react'
import Styles from "./UserAvatar.module.css"

interface UserAvatarProps {
    name?: string;
    firstName?: string;
    lastName?: string;
    companyName: string;
    compact?: boolean;
}

const UserAvatar: React.FC<UserAvatarProps> = ({
    name,
    firstName,
    lastName,
    companyName,
    compact = false,
}) => {

    const displayName = name ?? ([firstName, lastName].filter(Boolean).join(" ") || "Användare");
    const profileInitials = displayName
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part.charAt(0).toUpperCase())
        .join("");

    return (
        <div className={`${Styles.avatarContainer} ${compact ? Styles.avatarContainerCompact : ""}`}>
            <div className={Styles.avatar}>
                {profileInitials}
            </div>
            {!compact && (
                <div className={Styles.textWrapper}>
                    <h2 className={Styles.userName}>{displayName}</h2>
                    <h3 className={Styles.userWorkplace}>{companyName}</h3>
                </div>
            )}
        </div>
    )
}

export default UserAvatar
