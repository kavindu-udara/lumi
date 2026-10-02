"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

type AdminData = {
  adminId: string;
  username?: string;
};

export const useAdminAuth = () => {
  const router = useRouter();
  const [authState, setAuthState] = useState<{
    isAuthenticated: boolean;
    adminData: AdminData | null;
    loading: boolean;
  }>({ isAuthenticated: false, adminData: null, loading: true });

  useEffect(() => {
    const token = getAdminToken();
    if (!token) {
      setAuthState({ isAuthenticated: false, adminData: null, loading: false });
      router.push("/login");
      return;
    }

    fetch("/api/v1/admin/verify-token", {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then(async (response) => {
        if (!response.ok) throw new Error("Invalid session");
        const data = await response.json() as { admin: AdminData };
        setAuthState({ isAuthenticated: true, adminData: data.admin, loading: false });
      })
      .catch(() => {
        localStorage.removeItem("adminToken");
        localStorage.removeItem("adminUser");
        setAuthState({ isAuthenticated: false, adminData: null, loading: false });
        router.push("/login");
      });
  }, [router]);

  const logout = () => {
    fetch("/api/v1/admin/logout", { method: "POST" }).catch(() => undefined);
    localStorage.removeItem("adminToken");
    localStorage.removeItem("adminUser");
    setAuthState({ isAuthenticated: false, adminData: null, loading: false });
    router.push("/login");
  };

  return { ...authState, logout };
};

export const getAdminToken = (): string | null =>
  typeof window === "undefined" ? null : localStorage.getItem("adminToken");

export const getAdminUser = () => {
  if (typeof window === "undefined") return null;
  const user = localStorage.getItem("adminUser");
  return user ? JSON.parse(user) : null;
};
