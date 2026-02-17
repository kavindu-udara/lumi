"use server";
import prisma from "@/lib/db";
import { User, Provider } from "@prisma/client";

export async function findUserByEmail(email: string) : Promise<User | null> {
    const findUser = await prisma.user.findUnique({
        where: {
            email,
        },
    });

    return findUser;
}

export async function createUser(email: string, password: string, name?: string): Promise<User> {
    const user = await prisma.user.create({
        data: {
            email,
            password: password, // Password should already be hashed by the caller
            name: name || email.split("@")[0],
            provider: Provider.CREDENTIALS,
        },
    });

    return user;
}
