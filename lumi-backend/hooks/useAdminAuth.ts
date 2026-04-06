"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { decodeToken, TokenPayload } from "@/lib/jwt";

export const useAdminAuth = () => {
  const router = useRouter();
  const [authState, setAuthState] = useState<{
    isAuthenticated: boolean;
    adminData: TokenPayload | null;
    loading: boolean;
  }>(() => {
    if (typeof window === "undefined") {
      return { isAuthenticated: false, adminData: null, loading: true };
    }

    const token = localStorage.getItem("adminToken");
    const userData = localStorage.getItem("adminUser");

    if (token && userData) {
      const decoded = decodeToken(token);
      if (decoded) {
        return { isAuthenticated: true, adminData: decoded, loading: false };
      }
    }

    return { isAuthenticated: false, adminData: null, loading: false };
  });

  useEffect(() => {
    if (!authState.loading && !authState.isAuthenticated) {
      localStorage.removeItem("adminToken");
      localStorage.removeItem("adminUser");
      router.push("/login");
    }
  }, [authState.isAuthenticated, authState.loading, router]);

  const logout = () => {
    fetch("/api/v1/admin/logout", { method: "POST" }).catch(() => {
      // Ignore network errors and continue local logout cleanup
    });
    localStorage.removeItem("adminToken");
    localStorage.removeItem("adminUser");
    setAuthState({ isAuthenticated: false, adminData: null, loading: false });
    router.push("/login");
  };

  return {
    isAuthenticated: authState.isAuthenticated,
    adminData: authState.adminData,
    loading: authState.loading,
    logout,
  };
};

export const getAdminToken = (): string | null => {
  if (typeof window !== "undefined") {
    return localStorage.getItem("adminToken");
  }
  return null;
};

export const getAdminUser = () => {
  if (typeof window !== "undefined") {
    const user = localStorage.getItem("adminUser");
    return user ? JSON.parse(user) : null;
  }
  return null;
};
