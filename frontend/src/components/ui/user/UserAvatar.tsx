import React from 'react'
import Styles from "./UserAvatar.module.css"

interface UserAvatarProps {
    firstName: string;
    lastName: string;
}

const UserAvatar: React.FC<UserAvatarProps> = ({
    firstName,
    lastName,
}) => {

    const profileInitials = `${firstName.charAt(0)}${lastName.charAt(0)}`

    return (
        <div className={Styles.avatarContainer}>
            <div className={Styles.avatar}>
                {profileInitials}
            </div>
            <div>
                <h2 className={Styles.userName}>{firstName} {lastName}</h2>
            </div>
        </div>
    )
}

export default UserAvatar