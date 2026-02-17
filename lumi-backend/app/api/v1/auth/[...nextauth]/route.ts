import NextAuth from "next-auth";
import GoogleProvider from "next-auth/providers/google";
import CredentialsProvider from "next-auth/providers/credentials";
import { findUserByEmail } from "@/actions/user-actions";
import bcrypt from "bcrypt";
import { Provider } from "@prisma/client";
import prisma from "@/lib/db";

const handler = NextAuth({
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

          return new Error("Invalid password");
        }

        return null;
      },
    }),
  ],
  callbacks: {
    async signIn({ user, account }) {
      // Handle Google OAuth login
      if (account?.provider === "google") {
        const existingUser = await findUserByEmail(user.email!);

        if (!existingUser) {
          // Create new user for Google OAuth
          await prisma.user.create({
            data: {
              email: user.email!,
              name: user.name,
              provider: Provider.GOOGLE,
              emailVerified: new Date(),
            },
          });
        } else if (existingUser.provider === Provider.CREDENTIALS) {
          // User exists with credentials, allow linking Google
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
        session.user.id = token.id as string;
        session.user.provider = token.provider as string;
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
});

export { handler as GET, handler as POST };
