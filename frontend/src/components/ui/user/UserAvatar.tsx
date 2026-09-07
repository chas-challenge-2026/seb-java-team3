import React from 'react'
import Styles from "./UserAvatar.module.css"

interface UserAvatarProps {
    firstName: string;
    lastName: string;
    companyName: string;
}

const UserAvatar: React.FC<UserAvatarProps> = ({
    firstName,
    lastName,
    companyName,
}) => {

    const profileInitials = `${firstName.charAt(0)}${lastName.charAt(0)}`

    return (
        <div className={Styles.avatarContainer}>
            <div className={Styles.avatar}>
                {profileInitials}
            </div>
            <div className={Styles.textWrapper}>
                <h2 className={Styles.userName}>{firstName} {lastName}</h2>
                <h3 className={Styles.userWorkplace}>{companyName}</h3>
            </div>
        </div>
    )
}

export default UserAvatar