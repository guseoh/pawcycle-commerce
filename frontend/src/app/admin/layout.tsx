import { AdminNavigation } from "@/components/admin-catalog/shared";
import "./admin.css";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <><AdminNavigation />{children}</>;
}
