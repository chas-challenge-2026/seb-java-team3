import React from 'react'
import Styles from "./SideBar.module.css"
import SideBarItem from './SideBarItem'
import UserAvatar from '../user/UserAvatar'

const SideBar = () => {
  return (
    <aside className={Styles.sideBar}>

        <h2>Företagsbetalningar</h2>

        <nav>
            <SideBarItem label='Översikt'/>
            <div style={{marginBottom: "1.5rem"}}/>
            <SideBarItem/>
            <SideBarItem/>
            <SideBarItem/>
        </nav>

        <div style={{marginBottom: "1.5rem"}}/>

        <nav>
            <SideBarItem/>
        </nav>
         {/* <hr style={{ width: "100%", margin: "1.5rem 0" }} /> */}
         <UserAvatar firstName="Marcus" lastName="Johansson" companyName='Malmö Bygg'/>
    </aside>
  )
}

export default SideBar