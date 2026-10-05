# Admin Login & JWT Authentication

This is a complete admin authentication system with JWT (JSON Web Tokens) for the Lumi backend application.

## Files Created

### 1. **Login Page** (`app/login/page.tsx`)
   - Beautiful, responsive admin login form
   - Stores JWT token in localStorage
   - Form validation and error handling
   - Loading states and success messages
   - Redirects to dashboard on successful login

### 2. **Login API** (`app/api/v1/admin/login/route.ts`)
   - Accepts username and password
   - Validates credentials against MongoDB
   - Compares encrypted passwords using bcrypt
   - Generates JWT token on successful login
   - Returns token and admin information

### 3. **JWT Utilities** (`lib/jwt.ts`)
   - `generateToken()`: Creates JWT tokens with 7-day expiration
   - `verifyToken()`: Validates and decodes tokens
   - `decodeToken()`: Safely decodes tokens without verification

### 4. **Token Verification API** (`app/api/v1/admin/verify-token/route.ts`)
   - Endpoint to verify token validity
   - Returns decoded admin information
   - Supports token from Authorization header or cookies

### 5. **Middleware** (`middleware.ts`)
   - Protects `/admin/*` routes
   - Redirects to login if no valid token
   - Checks token validity automatically

### 6. **Authentication Hook** (`hooks/useAdminAuth.ts`)
   - React hook for accessing auth state
   - Provides logout functionality
   - Manages token and user data from localStorage

### 7. **Protected Route Component** (`components/ProtectedRoute.tsx`)
   - Wrapper component for protected pages
   - Shows loading state while checking auth
   - Redirects to login if not authenticated

### 8. **Admin Dashboard** (`app/admin/dashboard/page.tsx`)
   - Example protected admin page
   - Shows dashboard stats
   - Logout button
   - Uses ProtectedRoute wrapper

## Setup Instructions

### 1. Environment Variables
Create a `.env.local` file in the root directory:

```env
# MongoDB Connection
MONGO_URI=mongodb://localhost:27017/lumi

# JWT Secret (change this in production!)
JWT_SECRET=your-super-secret-key-change-in-production

# Optional: Token expiration time
JWT_EXPIRES_IN=7d
```

### 2. Create Admin User
Set the server-only Supabase seeder variables and run:

```bash
ADMIN_EMAIL=admin@example.com \
ADMIN_PASSWORD='use-a-strong-password-at-least-12-characters' \
npm run db:seed:admin
```

The seeder creates or finds the Supabase Auth user, grants the `admin` role in
`public.user_roles`, and creates/updates the corresponding profile. It is
idempotent and does not print or reset an existing user's password.

### 3. Update Admin Model (if needed)
The admin model is already set up at `models/admin.model.ts` with:
- username (unique)
- password (hashed)

## API Usage

### Login Endpoint
```bash
POST /api/v1/admin/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Response:**
```json
{
  "message": "Login successful",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "admin": {
    "id": "507f1f77bcf86cd799439011",
    "username": "admin"
  }
}
```

### Verify Token Endpoint
```bash
GET /api/v1/admin/verify-token
Authorization: Bearer <your-jwt-token>
```

**Response:**
```json
{
  "message": "Token is valid",
  "admin": {
    "adminId": "507f1f77bcf86cd799439011",
    "username": "admin"
  }
}
```

## Using in Components

### 1. Protected Page with Hook
```tsx
'use client';

import { useAdminAuth } from '@/hooks/useAdminAuth';
import { ProtectedRoute } from '@/components/ProtectedRoute';

export default function Page() {
  const { adminData, logout } = useAdminAuth();

  return (
    <ProtectedRoute>
      <div>
        <p>Welcome, {adminData?.username}</p>
        <button onClick={logout}>Logout</button>
      </div>
    </ProtectedRoute>
  );
}
```

### 2. API Request with Token
```typescript
const token = localStorage.getItem('adminToken');

const response = await fetch('/api/some-admin-endpoint', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
```

## Security Considerations

1. **JWT_SECRET**: Change from the default in production
2. **HTTPS Only**: Use HTTPS in production
3. **Secure Cookies**: Consider storing tokens in httpOnly cookies
4. **Token Rotation**: Implement refresh tokens for better security
5. **Password Policy**: Enforce strong passwords for admin accounts
6. **Rate Limiting**: Add rate limiting to login endpoint
7. **CORS**: Configure CORS properly for your frontend domain

## Testing

### Test Credentials
- Username: `admin`
- Password: `admin123`

### Steps to Test
1. Start your development server: `npm run dev`
2. Navigate to `http://localhost:3000/login`
3. Enter credentials and submit
4. Should redirect to `/admin/dashboard`
5. Try accessing `/admin/*` without login to test protection

## Features

✅ JWT-based authentication  
✅ Password hashing with bcrypt  
✅ Protected routes with middleware  
✅ Token verification endpoint  
✅ React hooks for auth state  
✅ Protected route components  
✅ Error handling and validation  
✅ Beautiful UI with Tailwind CSS  
✅ Loading states and feedback  

## Next Steps

1. Seed the database with an admin user
2. Set `JWT_SECRET` in `.env.local`
3. Test login functionality
4. Customize the dashboard as needed
5. Add additional admin routes and protect them
6. Implement refresh tokens for production
7. Add admin user management features
