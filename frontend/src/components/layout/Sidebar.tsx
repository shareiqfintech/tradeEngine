import { SidebarNav } from './SidebarNav'

/** Desktop/tablet-and-up fixed sidebar. Below `lg` this is replaced by MobileNav (a Sheet triggered from TopBar). */
export function Sidebar() {
  return (
    <aside className="hidden w-60 shrink-0 border-r border-sidebar-border bg-sidebar lg:block">
      <div className="fixed inset-y-0 left-0 w-60">
        <SidebarNav />
      </div>
    </aside>
  )
}
