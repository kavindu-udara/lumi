import { findUserByEmail } from "@/actions/user-actions";
import prisma from "@/lib/db";
import { Provider } from "@prisma/client";
import bcrypt from "bcrypt";
import { NextAuthOptions } from "next-auth";
import CredentialsProvider from "next-auth/providers/credentials";
import GoogleProvider from "next-auth/providers/google";
import { getServerSession } from "next-auth";
import { NextRequest } from "next/server";
import jwt from "jsonwebtoken";

export const JWT_SECRET = process.env.NEXTAUTH_SECRET || "your-secret-key";

type AuthenticatedRequestUser = {
  id?: string;
  email: string;
  name?: string | null;
  provider?: string;
};

export async function getRequestAuthUser(
  request: NextRequest,
): Promise<AuthenticatedRequestUser | null> {
  const session = await getServerSession(authOptions);

  if (session?.user?.email) {
    return {
      id: (session.user as { id?: string }).id,
      email: session.user.email,
      name: session.user.name,
      provider: (session.user as { provider?: string }).provider,
    };
  }

  const authorizationHeader = request.headers.get("authorization") || "";

  if (!authorizationHeader.startsWith("Bearer ")) {
    return null;
  }

  const token = authorizationHeader.slice(7).trim();

  if (!token) {
    return null;
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET);

    if (!decoded || typeof decoded === "string") {
      return null;
    }

    const payload = decoded as jwt.JwtPayload & {
      id?: string;
      email?: string;
      name?: string;
      provider?: string;
    };

    if (!payload.email) {
      return null;
    }

    return {
      id: payload.id,
      email: payload.email,
      name: payload.name,
      provider: payload.provider,
    };
  } catch {
    return null;
  }
}

export const authOptions: NextAuthOptions = {
  providers: [
    GoogleProvider({
      clientId: process.env.GOOGLE_CLIENT_ID!,
      clientSecret: process.env.GOOGLE_CLIENT_SECRET!,
    }),
    CredentialsProvider({
      name: "Credentials",
      credentials: {
        email: {
          label: "Email",
          type: "email",
          placeholder: "user@example.com",
        },
        password: { label: "Password", type: "password" },
      },
      async authorize(credentials) {
        if (!credentials?.email || !credentials?.password) {
          return null;
        }

        const findUser = await findUserByEmail(credentials.email);

        if (findUser && findUser.provider === Provider.CREDENTIALS) {
          const isPasswordValid = await bcrypt.compare(
            credentials.password,
            findUser.password!,
          );
          if (isPasswordValid) {
            return {
              id: findUser.id,
              email: findUser.email,
              name: findUser.name,
            };
          }

          return null;
        }

        return null;
      },
    }),
  ],
  callbacks: {
    async signIn({ user, account }) {
      if (account?.provider === "google") {
        const existingUser = await findUserByEmail(user.email!);

        if (!existingUser) {
          await prisma.user.create({
            data: {
              email: user.email!,
              name: user.name,
              provider: Provider.GOOGLE,
              emailVerified: new Date(),
            },
          });
        } else if (existingUser.provider === Provider.CREDENTIALS) {
          await prisma.user.update({
            where: { id: existingUser.id },
            data: { provider: Provider.GOOGLE },
          });
        }
      }

      return true;
    },
    async jwt({ token, user, account }) {
      if (user) {
        token.id = user.id;
        token.email = user.email;
        token.name = user.name;
      }
      if (account) {
        token.provider = account.provider;
      }
      return token;
    },
    async session({ session, token }) {
      if (session.user) {
        const sessionUser = session.user as typeof session.user & {
          id?: string;
          provider?: string;
        };

        sessionUser.id = token.id as string;
        sessionUser.provider = token.provider as string;
      }
      return session;
    },
  },
  pages: {
    signIn: "/auth/signin",
    error: "/auth/error",
  },
  session: {
    strategy: "jwt",
  },
};