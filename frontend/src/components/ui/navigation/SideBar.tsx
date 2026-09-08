import React from 'react'
import Styles from "./SideBar.module.css"
import SideBarItem from './SideBarItem'
import UserAvatar from '../user/UserAvatar'
// import SEBLogo from '../../SEBLogo'

import { LayoutDashboard, CreditCard, CreditCardCheck, Timeline, Settings, LogOut } from "lucide-react";


const SideBar = () => {
    const iconSize = 16;

    return (
        <aside className={Styles.sideBar}>

            <div>
                <div style={{display: "flex", flexDirection: "column", alignItems: "center", marginTop: "1rem", marginBottom: "2rem"}}>
                    {/* <SEBLogo size='sm'/> */}
                    <h2 style={{fontSize: "1.2rem"}}>Företagsbetalningar</h2>
                    <hr style={{ width: "100%", marginTop: "1.5rem" }} />
                </div>

                <nav>
                    <SideBarItem label='Översikt' icon={<LayoutDashboard size={iconSize}/>}/>
                    <div style={{marginBottom: "1rem"}}/>
                    <SideBarItem label="Betalningar" icon={<CreditCard size={iconSize}/>}/>
                    <SideBarItem label="Attestera" badge={2} icon={<CreditCardCheck size={iconSize}/>}/>
                    <SideBarItem label='Historik' icon={<Timeline size={iconSize}/>} route={"/uitest"}/>
                </nav>
            </div>
            <div>
                <nav>
                    <div style={{marginBottom: "1rem"}}/>
                    <SideBarItem label='Inställningar' icon={<Settings size={iconSize}/>}/>
                    <SideBarItem label='Logga ut' icon={<LogOut size={iconSize}/>}/>
                </nav>
                <hr style={{ width: "100%", margin: "1rem 0" }} />
                <UserAvatar firstName="Marcus" lastName="Johansson" companyName='Malmö Bygg'/>
            </div>
        </aside>
    )
}

export default SideBar