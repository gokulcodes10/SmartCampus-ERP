import { Dialog as DialogPrimitive } from "@base-ui/react/dialog";
import { XIcon } from "lucide-react";

import { SidebarNav, type SidebarNavLink } from "@/components/layout/SidebarNav";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface MobileNavDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  links: SidebarNavLink[];
}

/**
 * The below-`lg` navigation drawer for `DashboardLayout`. Built on Base UI's Dialog
 * primitive (already used by `components/ui/dialog.tsx`) rather than a hand-rolled
 * overlay, so Escape-to-close, focus trapping while open, and focus return to the
 * hamburger trigger on close all come from the primitive instead of being
 * reimplemented here.
 */
export function MobileNavDrawer({ open, onOpenChange, links }: MobileNavDrawerProps) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Backdrop
          className={cn(
            "fixed inset-0 z-40 bg-black/30 duration-150 lg:hidden",
            "data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0",
          )}
        />
        <DialogPrimitive.Popup
          className={cn(
            "fixed inset-y-0 left-0 z-50 flex w-72 max-w-[85vw] flex-col gap-4 bg-sidebar p-4",
            "text-sidebar-foreground outline-none duration-200 lg:hidden",
            "data-open:animate-in data-open:slide-in-from-left data-closed:animate-out data-closed:slide-out-to-left",
          )}
        >
          <div className="flex items-center justify-between">
            <DialogPrimitive.Title className="text-sm font-semibold tracking-tight">
              SmartCampus ERP
            </DialogPrimitive.Title>
            <DialogPrimitive.Close
              render={<Button variant="ghost" size="icon" aria-label="Close navigation menu" />}
            >
              <XIcon />
            </DialogPrimitive.Close>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto">
            <SidebarNav links={links} onNavigate={() => onOpenChange(false)} />
          </div>
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
