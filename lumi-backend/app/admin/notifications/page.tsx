"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useAdminAuth } from "@/hooks/useAdminAuth";
import { adminGetRequest, adminPostRequest } from "@/lib/admin-api";

type Broadcast = {
  id: string;
  title: string;
  body: string;
  status: string;
  recipient_count: number;
  delivered_count: number;
  read_count: number;
  failure_count: number;
  created_at: string;
};

type BroadcastList = { items: Broadcast[]; total: number };

const NotificationsPage = () => {
  const { adminData, logout } = useAdminAuth();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [deepLink, setDeepLink] = useState("");
  const [history, setHistory] = useState<Broadcast[]>([]);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const loadHistory = async () => {
    setLoading(true);
    try {
      const result = await adminGetRequest<BroadcastList>(
        "/api/v1/admin/notifications/broadcasts?limit=50&page=1",
      );
      setHistory(result.items);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Failed to load broadcast history");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadHistory();
  }, []);

  const sendBroadcast = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setSuccess("");
    if (!window.confirm("Send this notification to all eligible authenticated Android users?")) return;
    setSending(true);
    try {
      await adminPostRequest("/api/v1/admin/notifications/broadcasts", {
        title,
        body,
        data: deepLink.trim() ? { deepLink: deepLink.trim() } : {},
        idempotencyKey: crypto.randomUUID(),
      });
      setTitle("");
      setBody("");
      setDeepLink("");
      setSuccess("Broadcast sent through Supabase Realtime.");
      await loadHistory();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Failed to send broadcast");
    } finally {
      setSending(false);
    }
  };

  return (
    <ProtectedRoute>
      <main className="min-h-screen bg-slate-100 text-slate-900">
        <header className="border-b border-slate-200 bg-white">
          <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
            <div>
              <Link href="/admin/dashboard" className="text-sm text-blue-600 hover:underline">
                Back to dashboard
              </Link>
              <h1 className="text-2xl font-bold">Broadcast notifications</h1>
              <p className="text-sm text-slate-500">Signed in as {adminData?.username}</p>
            </div>
            <button onClick={logout} className="rounded-md bg-rose-600 px-4 py-2 text-white hover:bg-rose-700">
              Logout
            </button>
          </div>
        </header>

        <div className="mx-auto grid max-w-6xl gap-6 px-4 py-8 lg:grid-cols-[380px_1fr]">
          <section className="rounded-lg bg-white p-6 shadow">
            <h2 className="mb-1 text-lg font-semibold">New broadcast</h2>
            <p className="mb-5 text-sm text-slate-500">
              Connected Android clients receive this through their authenticated Supabase Realtime subscription.
            </p>
            <form onSubmit={sendBroadcast} className="space-y-4">
              <label className="block text-sm font-medium">
                Title
                <input
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  maxLength={120}
                  required
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
                />
                <span className="text-xs text-slate-500">{title.length}/120</span>
              </label>
              <label className="block text-sm font-medium">
                Message
                <textarea
                  value={body}
                  onChange={(event) => setBody(event.target.value)}
                  maxLength={2000}
                  required
                  rows={6}
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
                />
                <span className="text-xs text-slate-500">{body.length}/2000</span>
              </label>
              <label className="block text-sm font-medium">
                Optional deep link
                <input
                  value={deepLink}
                  onChange={(event) => setDeepLink(event.target.value)}
                  placeholder="lumi://..."
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
                />
              </label>
              <button
                type="submit"
                disabled={sending}
                className="w-full rounded-md bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {sending ? "Sending..." : "Send broadcast"}
              </button>
            </form>
            {error && <p className="mt-4 rounded-md bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
            {success && <p className="mt-4 rounded-md bg-emerald-50 p-3 text-sm text-emerald-700">{success}</p>}
          </section>

          <section className="rounded-lg bg-white p-6 shadow">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold">Broadcast history</h2>
              <button onClick={() => void loadHistory()} className="text-sm text-blue-600 hover:underline">
                Refresh
              </button>
            </div>
            {loading ? (
              <p className="text-sm text-slate-500">Loading history...</p>
            ) : history.length === 0 ? (
              <p className="text-sm text-slate-500">No broadcasts yet.</p>
            ) : (
              <div className="space-y-3">
                {history.map((broadcast) => (
                  <article key={broadcast.id} className="rounded-md border border-slate-200 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <h3 className="font-semibold">{broadcast.title}</h3>
                        <p className="mt-1 whitespace-pre-wrap text-sm text-slate-600">{broadcast.body}</p>
                      </div>
                      <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-medium uppercase">
                        {broadcast.status}
                      </span>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-4 text-xs text-slate-500">
                      <span>{broadcast.recipient_count} recipients</span>
                      <span>{broadcast.delivered_count} delivered</span>
                      <span>{broadcast.read_count} read</span>
                      <span>{broadcast.failure_count} failed</span>
                      <span>{new Date(broadcast.created_at).toLocaleString()}</span>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </main>
    </ProtectedRoute>
  );
};

export default NotificationsPage;
