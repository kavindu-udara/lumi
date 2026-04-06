import { getAdminToken } from "@/hooks/useAdminAuth";

interface ApiOptions extends RequestInit {
  headers?: Record<string, string>;
}

/**
 * Makes an authenticated API request with JWT token
 * Automatically adds Authorization header if token exists
 */
export const authenticatedFetch = async (
  url: string,
  options: ApiOptions = {}
): Promise<Response> => {
  const token = getAdminToken();
  
  const headers = {
    ...options.headers,
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  return fetch(url, {
    ...options,
    headers,
  });
};

/**
 * Makes a JSON POST request with authentication
 */
export const adminPostRequest = async <T>(
  url: string,
  data: Record<string, any>
): Promise<T> => {
  const response = await authenticatedFetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`API error: ${response.statusText}`);
  }

  return response.json();
};

/**
 * Makes a JSON GET request with authentication
 */
export const adminGetRequest = async <T>(url: string): Promise<T> => {
  const response = await authenticatedFetch(url, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`API error: ${response.statusText}`);
  }

  return response.json();
};

/**
 * Makes a JSON PUT request with authentication
 */
export const adminPutRequest = async <T>(
  url: string,
  data: Record<string, any>
): Promise<T> => {
  const response = await authenticatedFetch(url, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`API error: ${response.statusText}`);
  }

  return response.json();
};

/**
 * Makes a JSON DELETE request with authentication
 */
export const adminDeleteRequest = async <T>(url: string): Promise<T> => {
  const response = await authenticatedFetch(url, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`API error: ${response.statusText}`);
  }

  return response.json();
};
