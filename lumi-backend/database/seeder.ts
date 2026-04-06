import "dotenv/config";

import bcrypt from "bcrypt";
import connectDB from "@/lib/db";
import Admin from "@/models/admin.model";

const admin = {
    username : process.env.ADMIN_USERNAME || "admin",
    password : process.env.ADMIN_PASSWORD || "admin123",
}

async function seedPlans() {
    await connectDB();

    const hashedPassword = await bcrypt.hash(admin.password, 10);

    await Admin.findOneAndUpdate(
        { username: admin.username },
        { username: admin.username, password: hashedPassword },
        { upsert: true, new: true, setDefaultsOnInsert: true }
    );
    console.log(`Admin user "${admin.username}" seeded/updated.`);

    console.log("Seeding completed.");
    process.exit(0);
}

seedPlans().catch((error) => {
    console.error("Error seeding plans:", error);
    process.exit(1);
});