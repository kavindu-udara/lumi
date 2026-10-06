"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useAdminAuth } from "@/hooks/useAdminAuth";
import { AdminAnalytics } from "./AdminAnalytics";
import {
  adminDeleteRequest,
  adminGetRequest,
  adminPostRequest,
  adminPutRequest,
} from "@/lib/admin-api";

type ResourceName =
  | "admins"
  | "plans"
  | "albums"
  | "images"
  | "subscriptions"
  | "storage";

type GenericItem = {
  _id: string;
  [key: string]: unknown;
};

type ListResponse = {
  resource: ResourceName;
  page: number;
  limit: number;
  total: number;
  items: GenericItem[];
};

type FieldType = "text" | "number" | "password" | "datetime" | "json";

type FieldConfig = {
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  hiddenOnUpdate?: boolean;
};

const resources: Array<{ value: ResourceName; label: string }> = [
  { value: "admins", label: "Admins" },
  { value: "plans", label: "Plans" },
  { value: "albums", label: "Albums" },
  { value: "images", label: "Images" },
  { value: "subscriptions", label: "Subscriptions" },
  { value: "storage", label: "Storage" },
];

const resourceFieldMap: Record<ResourceName, FieldConfig[]> = {
  admins: [
    { key: "username", label: "Username", type: "text", required: true },
    {
      key: "password",
      label: "Password",
      type: "password",
      required: true,
      hiddenOnUpdate: true,
    },
  ],
  plans: [
    { key: "name", label: "Name", type: "text", required: true },
    { key: "storageLimit", label: "Storage Limit", type: "number", required: true },
    { key: "price", label: "Price", type: "number", required: true },
  ],
  albums: [
    { key: "userId", label: "User ID", type: "text", required: true },
    { key: "name", label: "Name", type: "text", required: true },
    { key: "description", label: "Description", type: "text" },
    { key: "coverPhotoUrl", label: "Cover Photo URL", type: "text" },
  ],
  images: [
    { key: "userId", label: "User ID", type: "text", required: true },
    { key: "imageId", label: "Image ID", type: "text", required: true },
    { key: "albumId", label: "Album ID", type: "text", required: true },
    { key: "size", label: "Size", type: "number" },
    { key: "location", label: "Location (JSON)", type: "json" },
    { key: "metadata", label: "Metadata (JSON)", type: "json" },
    { key: "timestamp", label: "Timestamp", type: "datetime" },
  ],
  subscriptions: [
    { key: "userId", label: "User ID", type: "text", required: true },
    { key: "planId", label: "Plan ID", type: "text", required: true },
    { key: "stripeMerchantId", label: "Stripe Merchant ID", type: "text" },
    { key: "paymentIntentId", label: "Payment Intent ID", type: "text" },
    { key: "startDate", label: "Start Date", type: "datetime" },
    { key: "endDate", label: "End Date", type: "datetime" },
  ],
  storage: [
    { key: "userId", label: "User ID", type: "text", required: true },
    { key: "planId", label: "Plan ID", type: "text", required: true },
    { key: "usedStorage", label: "Used Storage", type: "number" },
  ],
};

const toDatetimeLocal = (value: unknown): string => {
  if (!value) {
    return "";
  }

  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");

  return `${year}-${month}-${day}T${hour}:${minute}`;
};

const formValuesFromPayload = (
  resource: ResourceName,
  payload: Record<string, unknown>
): Record<string, string> => {
  const fields = resourceFieldMap[resource];
  const next: Record<string, string> = {};

  fields.forEach((field) => {
    const raw = payload[field.key];

    if (raw === undefined || raw === null) {
      if (field.type === "json") {
        next[field.key] = "{}";
      } else {
        next[field.key] = "";
      }
      return;
    }

    if (field.type === "json") {
      next[field.key] = JSON.stringify(raw, null, 2);
      return;
    }

    if (field.type === "datetime") {
      next[field.key] = toDatetimeLocal(raw);
      return;
    }

    next[field.key] = String(raw);
  });

  return next;
};

const payloadFromFormValues = (
  resource: ResourceName,
  values: Record<string, string>,
  mode: "create" | "update"
): Record<string, unknown> => {
  const fields = resourceFieldMap[resource];
  const payload: Record<string, unknown> = {};

  for (const field of fields) {
    const value = values[field.key] ?? "";
    const trimmed = value.trim();

    if (mode === "update" && field.hiddenOnUpdate) {
      continue;
    }

    if (field.required && trimmed.length === 0) {
      throw new Error(`${field.label} is required`);
    }

    if (trimmed.length === 0) {
      continue;
    }

    if (field.type === "number") {
      const numeric = Number(trimmed);
      if (Number.isNaN(numeric)) {
        throw new Error(`${field.label} must be a valid number`);
      }
      payload[field.key] = numeric;
      continue;
    }

    if (field.type === "json") {
      try {
        payload[field.key] = JSON.parse(trimmed);
      } catch {
        throw new Error(`${field.label} must be valid JSON`);
      }
      continue;
    }

    if (field.type === "datetime") {
      const date = new Date(trimmed);
      if (Number.isNaN(date.getTime())) {
        throw new Error(`${field.label} must be a valid date`);
      }
      payload[field.key] = date.toISOString();
      continue;
    }

    payload[field.key] = trimmed;
  }

  return payload;
};

const getTemplateForResource = (resource: ResourceName) => {
  switch (resource) {
    case "admins":
      return {
        username: "",
        password: "",
      };
    case "plans":
      return {
        name: "",
        storageLimit: 0,
        price: 0,
      };
    case "albums":
      return {
        userId: "",
        name: "",
        description: "",
        coverPhotoUrl: "",
      };
    case "images":
      return {
        userId: "",
        imageId: "",
        albumId: "",
        size: 0,
        location: {
          latitude: 0,
          longitude: 0,
        },
        metadata: [],
      };
    case "subscriptions":
      return {
        userId: "",
        planId: "",
        stripeMerchantId: "",
        paymentIntentId: "",
        startDate: new Date().toISOString(),
        endDate: new Date().toISOString(),
      };
    case "storage":
      return {
        userId: "",
        planId: "",
        usedStorage: 0,
      };
    default:
      return {};
  }
};

const AdminDashboard = () => {
  const { adminData, logout } = useAdminAuth();
  const [selectedResource, setSelectedResource] = useState<ResourceName>("plans");
  const [items, setItems] = useState<GenericItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [editorMode, setEditorMode] = useState<"create" | "update">("create");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [formValues, setFormValues] = useState<Record<string, string>>(
    formValuesFromPayload("plans", getTemplateForResource("plans") as Record<string, unknown>)
  );
  const [jsonInput, setJsonInput] = useState(
    JSON.stringify(getTemplateForResource("plans"), null, 2)
  );

  const columns = useMemo(() => {
    const row = items[0];
    if (!row) {
      return ["_id"];
    }
    return Object.keys(row);
  }, [items]);

  const loadItems = async (resource: ResourceName) => {
    setLoading(true);
    setError("");
    setSuccess("");

    try {
      const data = await adminGetRequest<ListResponse>(
        `/api/v1/admin/manage/${resource}?limit=100&page=1`
      );
      setItems(data.items);
      setTotal(data.total);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load data";
      setError(message);
      setItems([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const template = getTemplateForResource(selectedResource) as Record<string, unknown>;
    setFormValues(formValuesFromPayload(selectedResource, template));
    setJsonInput(JSON.stringify(template, null, 2));
    setEditorMode("create");
    setEditingId(null);
    loadItems(selectedResource);
  }, [selectedResource]);

  const resetEditor = () => {
    const template = getTemplateForResource(selectedResource) as Record<string, unknown>;
    setEditorMode("create");
    setEditingId(null);
    setFormValues(formValuesFromPayload(selectedResource, template));
    setJsonInput(JSON.stringify(template, null, 2));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setSuccess("");

    try {
      const payload = payloadFromFormValues(selectedResource, formValues, editorMode);
      setJsonInput(JSON.stringify(payload, null, 2));

      if (editorMode === "create") {
        await adminPostRequest(`/api/v1/admin/manage/${selectedResource}`, payload);
        setSuccess("Created successfully");
      } else if (editingId) {
        await adminPutRequest(
          `/api/v1/admin/manage/${selectedResource}/${editingId}`,
          payload
        );
        setSuccess("Updated successfully");
      }

      await loadItems(selectedResource);
      resetEditor();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to save item";
      setError(message);
    }
  };

  const handleEdit = (item: GenericItem) => {
    setEditorMode("update");
    setEditingId(item._id);
    const payload: Record<string, unknown> = { ...item };
    delete payload._id;
    delete payload.__v;
    setFormValues(formValuesFromPayload(selectedResource, payload));
    setJsonInput(JSON.stringify(payload, null, 2));
    setError("");
    setSuccess("");
  };

  const handleFormChange = (key: string, value: string) => {
    setFormValues((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  const syncJsonFromForm = () => {
    setError("");

    try {
      const payload = payloadFromFormValues(selectedResource, formValues, editorMode);
      setJsonInput(JSON.stringify(payload, null, 2));
      setSuccess("JSON preview synced from form values");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Unable to sync JSON";
      setError(message);
    }
  };

  const visibleFields = useMemo(() => {
    return resourceFieldMap[selectedResource].filter((field) => {
      if (editorMode === "update" && field.hiddenOnUpdate) {
        return false;
      }
      return true;
    });
  }, [editorMode, selectedResource]);

  const confirmDelete = async () => {
    if (!pendingDeleteId) {
      return;
    }

    setError("");
    setSuccess("");
    setIsDeleting(true);

    try {
      await adminDeleteRequest(
        `/api/v1/admin/manage/${selectedResource}/${pendingDeleteId}`
      );
      setSuccess("Deleted successfully");
      await loadItems(selectedResource);
      if (editingId === pendingDeleteId) {
        resetEditor();
      }
      setPendingDeleteId(null);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to delete item";
      setError(message);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <ProtectedRoute>
      <div className="min-h-screen bg-slate-100 text-slate-900">
        <div className="bg-white shadow-sm border-b border-slate-200 print:hidden">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-2xl font-bold text-slate-900">Admin Data Manager</h1>
              <p className="text-sm text-slate-500">
                Logged in as {adminData?.username}.
              </p>
            </div>
            <button
              onClick={logout}
              className="px-4 py-2 bg-rose-600 text-white rounded-md hover:bg-rose-700 transition"
            >
              Logout
            </button>
            <Link
              href="/admin/notifications"
              className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition"
            >
              Notifications
            </Link>
          </div>
        </div>

        <div className="mx-auto max-w-7xl space-y-8 px-4 py-8 sm:px-6 lg:px-8">
          <AdminAnalytics />

          <div className="grid grid-cols-1 gap-6 xl:grid-cols-12 print:hidden">
          <div className="xl:col-span-4 bg-white rounded-lg shadow p-5">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-slate-900">
                {editorMode === "create" ? "Create Item" : "Update Item"}
              </h2>
              {editorMode === "update" && (
                <button
                  onClick={resetEditor}
                  className="text-xs px-2 py-1 rounded bg-slate-200 hover:bg-slate-300"
                >
                  Cancel Edit
                </button>
              )}
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Resource
              </label>
              <select
                value={selectedResource}
                onChange={(event) => setSelectedResource(event.target.value as ResourceName)}
                className="w-full border border-slate-300 rounded-md px-3 py-2 text-sm bg-white text-slate-900 font-medium shadow-sm"
              >
                {resources.map((resource) => (
                  <option key={resource.value} value={resource.value}>
                    {resource.label}
                  </option>
                ))}
              </select>
            </div>

            <form onSubmit={handleSubmit} className="space-y-3">
              <div className="rounded-md border border-slate-200 bg-slate-50 p-3 space-y-3">
                <p className="text-sm font-semibold text-slate-800">Schema Form</p>
                <div className="space-y-2">
                  {visibleFields.map((field) => (
                    <div key={field.key}>
                      <label className="block text-xs font-medium text-slate-700 mb-1">
                        {field.label}
                        {field.required && " *"}
                      </label>
                      {field.type === "json" ? (
                        <textarea
                          value={formValues[field.key] ?? ""}
                          onChange={(event) => handleFormChange(field.key, event.target.value)}
                          className="w-full min-h-24 font-mono text-xs border border-slate-300 rounded-md p-2 bg-white text-slate-900"
                          placeholder='{"key": "value"}'
                        />
                      ) : (
                        <input
                          type={
                            field.type === "number"
                              ? "number"
                              : field.type === "password"
                              ? "password"
                              : field.type === "datetime"
                              ? "datetime-local"
                              : "text"
                          }
                          value={formValues[field.key] ?? ""}
                          onChange={(event) => handleFormChange(field.key, event.target.value)}
                          className="w-full border border-slate-300 rounded-md px-3 py-2 text-sm bg-white text-slate-900"
                        />
                      )}
                    </div>
                  ))}
                </div>
                <button
                  type="button"
                  onClick={syncJsonFromForm}
                  className="w-full px-3 py-2 text-sm bg-slate-800 text-white rounded-md hover:bg-slate-900"
                >
                  Sync JSON Preview
                </button>
              </div>

              <label className="block text-sm font-semibold text-slate-800">JSON Preview</label>
              <textarea
                value={jsonInput}
                readOnly
                className="w-full min-h-70 font-mono text-xs border-2 border-slate-400 rounded-md p-3 bg-white text-slate-900"
              />

              <button
                type="submit"
                className="w-full px-4 py-2 bg-indigo-700 text-white rounded-md hover:bg-indigo-800 font-semibold shadow"
              >
                {editorMode === "create" ? "Create" : "Update"}
              </button>
            </form>

            {error && (
              <p className="mt-3 text-sm text-rose-600">{error}</p>
            )}
            {success && (
              <p className="mt-3 text-sm text-emerald-600">{success}</p>
            )}
          </div>

          <div className="xl:col-span-8 bg-white rounded-lg shadow p-5">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-slate-900">
                {selectedResource} ({total})
              </h2>
              <button
                onClick={() => loadItems(selectedResource)}
                className="px-3 py-2 text-sm bg-indigo-700 text-white hover:bg-indigo-800 rounded-md font-semibold shadow"
              >
                Refresh
              </button>
            </div>

            {loading ? (
              <p className="text-sm text-slate-500">Loading data...</p>
            ) : items.length === 0 ? (
              <p className="text-sm text-slate-500">No data found for this schema.</p>
            ) : (
              <div className="overflow-auto border border-slate-200 rounded-lg">
                <table className="min-w-full text-sm text-slate-900">
                  <thead className="bg-slate-200 text-slate-900">
                    <tr>
                      {columns.slice(0, 6).map((column) => (
                        <th key={column} className="text-left px-3 py-2 font-semibold">
                          {column}
                        </th>
                      ))}
                      <th className="text-left px-3 py-2 font-semibold">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item) => (
                      <tr key={item._id} className="border-t border-slate-200 odd:bg-white even:bg-slate-50">
                        {columns.slice(0, 6).map((column) => (
                          <td key={`${item._id}-${column}`} className="px-3 py-2 align-top text-slate-900">
                            <span className="line-clamp-2 break-all font-medium">
                              {typeof item[column] === "object"
                                ? JSON.stringify(item[column])
                                : String(item[column] ?? "")}
                            </span>
                          </td>
                        ))}
                        <td className="px-3 py-2 align-top">
                          <div className="flex items-center gap-2">
                            <button
                              onClick={() => handleEdit(item)}
                              className="px-2 py-1 text-xs rounded bg-amber-500 text-white hover:bg-amber-600"
                            >
                              Edit
                            </button>
                            <button
                              onClick={() => setPendingDeleteId(item._id)}
                              className="px-2 py-1 text-xs rounded bg-rose-600 text-white hover:bg-rose-700"
                            >
                              Delete
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        {pendingDeleteId && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4">
            <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl border border-slate-200">
              <h3 className="text-lg font-semibold text-slate-900">Confirm Delete</h3>
              <p className="mt-2 text-sm text-slate-700 break-all">
                This will permanently delete item:
              </p>
              <p className="mt-1 text-xs font-mono text-slate-800 break-all">{pendingDeleteId}</p>
              <p className="mt-3 text-sm text-rose-700">This action cannot be undone.</p>

              <div className="mt-5 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setPendingDeleteId(null)}
                  disabled={isDeleting}
                  className="px-3 py-2 text-sm bg-slate-200 text-slate-900 rounded-md hover:bg-slate-300 disabled:opacity-60"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={confirmDelete}
                  disabled={isDeleting}
                  className="px-3 py-2 text-sm bg-rose-600 text-white rounded-md hover:bg-rose-700 disabled:opacity-60"
                >
                  {isDeleting ? "Deleting..." : "Delete"}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
      </div>
    </ProtectedRoute>
  );
};

export default AdminDashboard;
