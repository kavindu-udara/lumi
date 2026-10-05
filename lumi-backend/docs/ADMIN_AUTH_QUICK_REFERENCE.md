# Admin Authentication Quick Reference

## 🚀 Quick Start

### 1. Setup Environment
```bash
# Copy and update .env.local
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SECRET_KEY=your-server-only-service-role-key
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=use-a-strong-password-at-least-12-characters
ADMIN_DISPLAY_NAME=Lumi Administrator
```

### 2. Create Admin User
```bash
npm run db:seed:admin
```

The seeder is idempotent: it creates the Supabase Auth user when absent and
upserts the `admin` role and profile when the user already exists. It does not
print or reset the password for an existing user. Keep `SUPABASE_SECRET_KEY`
server-side and never put it in the Android app or browser.

### 3. Start Development
```bash
npm run dev
# Visit http://localhost:3000/login
```

---

## 📝 Login Page

**URL:** `/login`
- Beautiful form with Tailwind styling
- Auto-redirects to `/admin/dashboard` on success
- Error/success messages
- Loading states

### Broadcast notifications

Authorized admins can open `/admin/notifications` from the dashboard to send a
Supabase Realtime broadcast to eligible authenticated Android users. Broadcasts
and per-user notification rows are stored in Supabase and can be reviewed in
the history list. This feature is in-app/reconnect delivery only; it does not
provide native OS push while the Android process is fully stopped.

---

## 🔐 API Endpoints

### Login
```bash
POST /api/v1/admin/login
{
  "username": "admin",
  "password": "admin123"
}

Response:
{
  "token": "eyJ...",
  "admin": { "id": "...", "username": "admin" }
}
```

### Verify Token
```bash
GET /api/v1/admin/verify-token
Authorization: Bearer <token>

Response:
{
  "admin": { "adminId": "...", "username": "admin" }
}
```

---

## 🛡️ Protected Routes

Any route under `/admin/*` is automatically protected by middleware.
- No valid token → redirects to `/login`
- Checks `adminToken` cookie

---

## 🎣 React Hook Usage

```tsx
'use client';

import { useAdminAuth } from '@/hooks/useAdminAuth';

export default function Page() {
  const { adminData, logout, isAuthenticated } = useAdminAuth();

  return (
    <div>
      <p>Welcome, {adminData?.username}</p>
      <button onClick={logout}>Logout</button>
    </div>
  );
}
```

---

## 🧩 Protected Component

```tsx
import { ProtectedRoute } from '@/components/ProtectedRoute';

export default function Page() {
  return (
    <ProtectedRoute>
      <div>This is only visible to authenticated admins</div>
    </ProtectedRoute>
  );
}
```

---

## 🌐 Authenticated API Requests

```tsx
import { adminGetRequest, adminPostRequest } from '@/lib/admin-api';

// GET request
const data = await adminGetRequest('/api/v1/admin/users');

// POST request
const result = await adminPostRequest('/api/v1/admin/users', {
  username: 'newuser',
  email: 'user@example.com'
});
```

---

## 📁 File Structure

```
lib/
  ├── jwt.ts                 # Token generation/verification
  └── admin-api.ts           # Authenticated API helpers

app/
  ├── login/page.tsx         # Login form
  └── api/v1/admin/
      ├── login/route.ts     # Login endpoint
      └── verify-token/route.ts  # Token check

hooks/
  └── useAdminAuth.ts        # Auth hook

components/
  └── ProtectedRoute.tsx     # Protected route wrapper

middleware.ts               # Route protection

app/admin/dashboard/page.tsx # Example dashboard
```

---

## 🔧 Customization

### Change Token Expiration
Edit in `lib/jwt.ts`:
```typescript
export const generateToken = (payload: TokenPayload): string => {
  return jwt.sign(payload, JWT_SECRET, {
    expiresIn: "30d",  // Change here
  });
};
```

### Protect Additional Routes
The middleware already protects `/admin/*`. To add more:
```typescript
// middleware.ts
export const config = {
  matcher: ["/admin/:path*", "/dashboard/:path*"],
};
```

### Style Login Page
Tailwind classes are used throughout. Edit `app/login/page.tsx` to customize colors/layout.

---

## 🧪 Testing

**Demo Credentials:**
- Username: `admin`
- Password: `admin123`

**Test Scenarios:**
1. ✅ Login with correct credentials
2. ✅ Login with wrong password (should fail)
3. ✅ Access `/admin/dashboard` directly (auto-redirects to login)
4. ✅ Logout clears token
5. ✅ Verify token endpoint with expired token (returns 401)

---

## 🚨 Security Checklist

- [ ] Changed `JWT_SECRET` from default
- [ ] Using HTTPS in production
- [ ] Added rate limiting to login endpoint
- [ ] Strong password policy for admin users
- [ ] Configured CORS properly
- [ ] Regular security audits
- [ ] Refresh token implementation (optional)

---

## ❓ Troubleshooting

**Token not persisting?**
- Check browser's localStorage in DevTools

**Getting 401 on protected routes?**
- Verify token in localStorage
- Check if JWT_SECRET matches

**Login button not working?**
- Check network tab for API errors
- Verify MongoDB is running
- Check admin credentials in database

**Redirect loop on /admin pages?**
- Middleware may need restart: `npm run dev`
- Browser cache might have old data

---

## 📚 Learn More

See `AUTH_SETUP.md` for detailed documentation.
