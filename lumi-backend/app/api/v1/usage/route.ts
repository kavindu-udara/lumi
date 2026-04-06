import { verifyFirebaseUser } from "@/lib/auth/firebase-user-auth";
import connectDB from "@/lib/db";
import Plan from "@/models/plans.model";
import Storage from "@/models/storage.model";
import Subscription from "@/models/subcription.model";
import { NextRequest } from "next/server";

export async function GET(request: NextRequest) {
    try {

        await connectDB();

        const firebaseUser = await verifyFirebaseUser(request);

        if (!firebaseUser) {
            return Response.json({ error: "Unauthorized" }, { status: 401 });
        }

        const { uid } = firebaseUser;

        const subscription = await Subscription.findOne({ userId: uid })
            .populate("planId", "-__v")
            .lean();       

            const storage = await Storage.findOne({ userId: uid })

        if (!subscription) {
            const freePlan = await Plan.findOne({ name: "Free" })
                .select("-__v")
                .lean();

            if (!freePlan) {
                return Response.json({ error: "Free plan not found" }, { status: 404 });
            }

            return Response.json(
                {
                    subscription: {
                        userId: uid,
                        planId: freePlan,
                        startDate: null,
                        endDate: null,
                    },
                    storage: storage 
                },
                { status: 200 },
            );
        }

        return Response.json({ subscription, storage }, { status: 200 });
    } catch (error) {
        const message =
            error instanceof Error ? error.message : "Internal server error";
        return Response.json({ error: message }, { status: 500 });
    }
}
