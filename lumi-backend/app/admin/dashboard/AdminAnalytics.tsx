"use client";

import { useEffect, useMemo, useState } from "react";
import { adminGetRequest } from "@/lib/admin-api";

type DashboardRecord = {
  [key: string]: unknown;
};

type ResourceResponse = {
  items: DashboardRecord[];
};

type AnalyticsData = {
  plans: DashboardRecord[];
  albums: DashboardRecord[];
  images: DashboardRecord[];
  subscriptions: DashboardRecord[];
  storage: DashboardRecord[];
};

type Period = 7 | 30 | 90;

const asNumber = (value: unknown) => {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
};

const asDate = (value: unknown) => {
  if (!value) return null;
  const date = new Date(String(value));
  return Number.isNaN(date.getTime()) ? null : date;
};

const firstValue = (row: DashboardRecord, ...keys: string[]) => {
  for (const key of keys) {
    if (row[key] !== undefined && row[key] !== null) return row[key];
  }
  return undefined;
};

const formatBytes = (bytes: number) => {
  if (bytes < 1024) return `${Math.round(bytes)} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = -1;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unit]}`;
};

const formatNumber = (value: number) => new Intl.NumberFormat("en-US").format(value);

const csvValue = (value: unknown) => {
  const text = typeof value === "object" ? JSON.stringify(value) : String(value ?? "");
  return `"${text.replaceAll('"', '""')}"`;
};

const downloadReport = (data: AnalyticsData) => {
  const sections = [
    ["plans", data.plans],
    ["albums", data.albums],
    ["images", data.images],
    ["subscriptions", data.subscriptions],
    ["storage", data.storage],
  ];
  const rows: string[] = ["section,id,name,status,created_at,amount,bytes"];
  sections.forEach(([section, records]) => {
    const items = records as DashboardRecord[];
    items.forEach((record) => {
      rows.push(
        [
          section,
          firstValue(record, "_id", "id"),
          firstValue(record, "name", "original_name"),
          firstValue(record, "status"),
          firstValue(record, "created_at", "start_date"),
          firstValue(record, "price", "amount_paid"),
          firstValue(record, "size", "size_bytes", "usedStorage", "used_bytes", "storage_limit_bytes"),
        ]
          .map(csvValue)
          .join(","),
      );
    });
  });

  const blob = new Blob([rows.join("\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `lumi-admin-report-${new Date().toISOString().slice(0, 10)}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
};

const BarChart = ({
  items,
  color,
  valueFormatter = (value: number) => formatNumber(value),
}: {
  items: Array<{ label: string; value: number; color?: string }>;
  color?: string;
  valueFormatter?: (value: number) => string;
}) => {
  const max = Math.max(...items.map((item) => item.value), 1);
  return (
    <div className="space-y-4">
      {items.length === 0 ? (
        <p className="text-sm text-slate-500">No activity in this period.</p>
      ) : (
        items.map((item) => (
          <div key={item.label}>
            <div className="mb-1 flex items-center justify-between text-xs font-medium text-slate-600">
              <span>{item.label}</span>
              <span>{valueFormatter(item.value)}</span>
            </div>
            <div className="h-2.5 overflow-hidden rounded-full bg-slate-100">
              <div
                className={`h-full rounded-full ${item.color ?? color ?? "bg-indigo-500"}`}
                style={{ width: `${Math.max((item.value / max) * 100, item.value ? 4 : 0)}%` }}
              />
            </div>
          </div>
        ))
      )}
    </div>
  );
};

const VerticalBarChart = ({ items }: {
  items: Array<{ label: string; value: number }>;
}) => {
  const max = Math.max(...items.map((item) => item.value), 1);

  if (items.length === 0) {
    return <p className="text-sm text-slate-500">No uploads in this period.</p>;
  }

  return (
    <div className="relative">
      <div className="pointer-events-none absolute inset-x-0 top-0 flex h-40 flex-col justify-between">
        {[100, 75, 50, 25, 0].map((value) => (
          <div key={value} className="border-t border-dashed border-slate-200" />
        ))}
      </div>
      <div className="relative flex h-40 items-end gap-2 px-1 sm:gap-4">
        {items.map((item) => (
          <div key={item.label} className="flex min-w-0 flex-1 flex-col items-center justify-end gap-2">
            <span className="text-xs font-semibold text-slate-600">{item.value}</span>
            <div
              className="w-full max-w-12 rounded-t-lg bg-gradient-to-t from-indigo-600 to-cyan-400 transition-all hover:from-indigo-500 hover:to-cyan-300"
              style={{ height: `${Math.max((item.value / max) * 100, item.value ? 6 : 0)}%` }}
              title={`${item.label}: ${item.value} uploads`}
            />
            <span className="w-full truncate text-center text-[10px] text-slate-500">{item.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
};

const Metric = ({ label, value, detail, accent }: {
  label: string;
  value: string;
  detail: string;
  accent: string;
}) => (
  <div className="relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <div className={`absolute inset-y-0 left-0 w-1 ${accent}`} />
    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">{label}</p>
    <p className="mt-3 text-3xl font-bold tracking-tight text-slate-950">{value}</p>
    <p className="mt-1 text-xs text-slate-500">{detail}</p>
  </div>
);

export const AdminAnalytics = () => {
  const [period, setPeriod] = useState<Period>(30);
  const [data, setData] = useState<AnalyticsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const loadAnalytics = async () => {
    setLoading(true);
    setError("");
    try {
      const resources = ["plans", "albums", "images", "subscriptions", "storage"] as const;
      const responses = await Promise.all(
        resources.map((resource) =>
          adminGetRequest<ResourceResponse>(`/api/v1/admin/manage/${resource}?limit=200&page=1`),
        ),
      );
      setData({
        plans: responses[0].items,
        albums: responses[1].items,
        images: responses[2].items,
        subscriptions: responses[3].items,
        storage: responses[4].items,
      });
      setLastUpdated(new Date());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to load analytics");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadAnalytics();
  }, []);

  const analysis = useMemo(() => {
    if (!data) return null;
    const since = new Date();
    since.setDate(since.getDate() - period);
    const recentImages = data.images.filter((item) => {
      const date = asDate(firstValue(item, "created_at", "timestamp"));
      return date ? date >= since : false;
    });
    const totalUsed = data.storage.reduce(
      (sum, item) => sum + asNumber(firstValue(item, "usedStorage", "used_bytes")),
      0,
    );
    const planLookup = new Map(
      data.plans.map((plan) => [String(firstValue(plan, "_id", "id")), String(firstValue(plan, "name") ?? "Unknown")]),
    );
    const planCounts = new Map<string, number>();
    data.storage.forEach((item) => {
      const name = planLookup.get(String(firstValue(item, "planId", "plan_id"))) ?? "Unknown";
      planCounts.set(name, (planCounts.get(name) ?? 0) + 1);
    });
    const monthCounts = new Map<string, number>();
    recentImages.forEach((item) => {
      const date = asDate(firstValue(item, "created_at", "timestamp"));
      if (!date) return;
      const label = date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
      monthCounts.set(label, (monthCounts.get(label) ?? 0) + 1);
    });
    const activeSubscriptions = data.subscriptions.filter((item) =>
      ["active", "trialing"].includes(String(firstValue(item, "status") ?? "active")),
    ).length;
    const totalImageBytes = data.images.reduce(
      (sum, item) => sum + asNumber(firstValue(item, "size", "size_bytes")),
      0,
    );
    return {
      recentImages,
      totalUsed,
      totalImageBytes,
      activeSubscriptions,
      planCounts: Array.from(planCounts, ([label, value]) => ({ label, value })),
      monthCounts: Array.from(monthCounts, ([label, value]) => ({ label, value })).slice(-8),
      recentActivity: [...data.images]
        .sort((a, b) => (asDate(firstValue(b, "created_at", "timestamp"))?.getTime() ?? 0) - (asDate(firstValue(a, "created_at", "timestamp"))?.getTime() ?? 0))
        .slice(0, 5),
    };
  }, [data, period]);

  return (
    <section id="analysis" className="space-y-6">
      <div className="flex flex-col gap-4 rounded-2xl bg-slate-950 p-6 text-white shadow-xl sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-indigo-300">Admin intelligence</p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight">Analysis dashboard</h2>
          <p className="mt-2 max-w-2xl text-sm text-slate-300">
            A live operational view of growth, storage health, plans, and recent media activity.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {[7, 30, 90].map((value) => (
            <button
              key={value}
              onClick={() => setPeriod(value as Period)}
              className={`rounded-lg px-3 py-2 text-sm font-semibold transition ${period === value ? "bg-white text-slate-950" : "bg-white/10 text-white hover:bg-white/20"}`}
            >
              {value} days
            </button>
          ))}
          <button onClick={() => void loadAnalytics()} className="rounded-lg bg-indigo-500 px-3 py-2 text-sm font-semibold hover:bg-indigo-400">
            Refresh
          </button>
        </div>
      </div>

      {error && <p className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</p>}
      {loading && !analysis ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-sm text-slate-500 shadow-sm">Loading analysis...</div>
      ) : analysis ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Metric label="Accounts" value={formatNumber(data?.storage.length ?? 0)} detail="users with a storage record" accent="bg-indigo-500" />
            <Metric label="Media library" value={formatNumber(data?.images.length ?? 0)} detail={`${formatNumber(analysis.recentImages.length)} added in ${period} days`} accent="bg-cyan-500" />
            <Metric label="Storage used" value={formatBytes(analysis.totalUsed)} detail={`${formatBytes(analysis.totalImageBytes)} across media files`} accent="bg-emerald-500" />
            <Metric label="Active subscriptions" value={formatNumber(analysis.activeSubscriptions)} detail={`${formatNumber(data?.subscriptions.length ?? 0)} total records`} accent="bg-amber-500" />
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="mb-6 flex items-start justify-between">
                <div>
                  <h3 className="font-semibold text-slate-950">Media uploads</h3>
                  <p className="mt-1 text-sm text-slate-500">Files added during the selected period</p>
                </div>
                <span className="rounded-full bg-indigo-50 px-3 py-1 text-xs font-semibold text-indigo-700">{period}D</span>
              </div>
              <BarChart items={analysis.monthCounts} />
            </article>
            <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="mb-6">
                <h3 className="font-semibold text-slate-950">Plan distribution</h3>
                <p className="mt-1 text-sm text-slate-500">Storage accounts by current plan</p>
              </div>
              <BarChart items={analysis.planCounts} color="bg-emerald-500" />
            </article>
          </div>

          <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="mb-6 flex items-start justify-between">
              <div>
                <h3 className="font-semibold text-slate-950">Upload volume</h3>
                <p className="mt-1 text-sm text-slate-500">Vertical bar chart of media uploads over time</p>
              </div>
              <span className="rounded-full bg-cyan-50 px-3 py-1 text-xs font-semibold text-cyan-700">
                {formatNumber(analysis.recentImages.length)} uploads
              </span>
            </div>
            <VerticalBarChart items={analysis.monthCounts} />
          </article>

          <div className="grid gap-6 lg:grid-cols-[1fr_1.45fr]">
            <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm print:hidden">
              <div className="mb-5 flex items-center justify-between">
                <div>
                  <h3 className="font-semibold text-slate-950">Report center</h3>
                  <p className="mt-1 text-sm text-slate-500">Take the current snapshot with you.</p>
                </div>
                <span className="text-2xl">↗</span>
              </div>
              <div className="space-y-3">
                <button onClick={() => data && downloadReport(data)} className="w-full rounded-xl bg-slate-950 px-4 py-3 text-left text-sm font-semibold text-white hover:bg-slate-800">
                  Download CSV report
                  <span className="mt-1 block text-xs font-normal text-slate-300">All loaded records and key metrics</span>
                </button>
                <button onClick={() => window.print()} className="w-full rounded-xl border border-slate-200 px-4 py-3 text-left text-sm font-semibold text-slate-800 hover:bg-slate-50">
                  Print / save PDF
                  <span className="mt-1 block text-xs font-normal text-slate-500">A clean printable analysis summary</span>
                </button>
              </div>
              {lastUpdated && <p className="mt-4 text-xs text-slate-400">Updated {lastUpdated.toLocaleTimeString()}</p>}
            </article>
            <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h3 className="font-semibold text-slate-950">Recent media activity</h3>
              <p className="mt-1 text-sm text-slate-500">Latest uploads across the library</p>
              <div className="mt-5 divide-y divide-slate-100">
                {analysis.recentActivity.length === 0 ? <p className="py-4 text-sm text-slate-500">No media activity yet.</p> : analysis.recentActivity.map((item, index) => (
                  <div key={String(firstValue(item, "_id", "id") ?? index)} className="flex items-center justify-between gap-4 py-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-slate-800">{String(firstValue(item, "original_name", "imageId") ?? "Untitled media")}</p>
                      <p className="mt-1 text-xs text-slate-500">{formatBytes(asNumber(firstValue(item, "size", "size_bytes")))}</p>
                    </div>
                    <span className="shrink-0 text-xs text-slate-400">
                      {asDate(firstValue(item, "created_at", "timestamp"))?.toLocaleDateString() ?? "Unknown date"}
                    </span>
                  </div>
                ))}
              </div>
            </article>
          </div>
        </>
      ) : null}
    </section>
  );
};
