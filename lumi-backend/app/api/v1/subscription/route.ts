import connectDB from "@/lib/db";
import stripe from "@/lib/stripe";
import Plan from "@/models/plans.model";
import Subscription from "@/models/subcription.model";
import Storage from "@/models/storage.model";
import { NextRequest } from "next/server";
import { verifyFirebaseUser } from "@/lib/auth/firebase-user-auth";

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
        },
        { status: 200 },
      );
    }

    return Response.json({ subscription }, { status: 200 });
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Internal server error";
    return Response.json({ error: message }, { status: 500 });
  }
}

export async function PUT(request: NextRequest) {
  try {
    await connectDB();

    const firebaseUser = await verifyFirebaseUser(request);

    if (!firebaseUser) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const { uid } = firebaseUser;

    if (!stripe) {
      return Response.json(
        { error: "Stripe is not configured" },
        { status: 500 },
      );
    }

    const subscription = await Subscription.findOne({ userId: uid });

    // get the update plan from request body
    const { planId, stripeMerchantId, paymentIntentId } = await request.json();
    const effectivePaymentIntentId = paymentIntentId || stripeMerchantId;

    if (!planId) {
      return Response.json(
        { error: "Missing planName in request body" },
        { status: 400 },
      );
    }

    if (!effectivePaymentIntentId) {
      return Response.json(
        { error: "Missing paymentIntentId in request body" },
        { status: 400 },
      );
    }

    const newPlan = await Plan.findOne({ _id: planId });

    if (!newPlan) {
      return Response.json({ error: "Plan not found" }, { status: 404 });
    }

    // We currently receive a PaymentIntent id (pi_...) from the client.
    const paymentIntent = await stripe.paymentIntents.retrieve(
      effectivePaymentIntentId,
    );

    if (!paymentIntent || paymentIntent.status !== "succeeded") {
      return Response.json(
        { error: "Payment not successful for the new plan" },
        { status: 402 },
      );
    }

    if (paymentIntent.metadata?.firebaseUserId !== uid) {
      return Response.json(
        { error: "PaymentIntent does not belong to this user" },
        { status: 403 },
      );
    }

    if (String(paymentIntent.metadata?.planId || "") !== String(newPlan._id)) {
      return Response.json(
        { error: "PaymentIntent plan does not match selected plan" },
        { status: 400 },
      );
    }

    const startDate = new Date();
    const endDate = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30 days from today

    if (subscription) {
      await Subscription.findOneAndUpdate(
        { userId: uid },
        {
          planId: newPlan._id,
          stripeMerchantId: subscription.stripeMerchantId || null,
          paymentIntentId: effectivePaymentIntentId,
          startDate,
          endDate,
        },
        { new: true, runValidators: true },
      );
    } else {
      await Subscription.create({
        userId: uid,
        planId: newPlan._id,
        stripeMerchantId: null,
        paymentIntentId: effectivePaymentIntentId,
        startDate,
        endDate,
      });
    }

    // Update storage planId to match the new subscription plan
    await Storage.findOneAndUpdate(
      { userId: uid },
      { planId: newPlan._id },
      { new: true, upsert: true, runValidators: true },
    );

    return Response.json(
      { message: "Subscription updated successfully" },
      { status: 200 },
    );
  } catch (error) {
    console.log("Error updating subscription:", error);
    const message =
      error instanceof Error ? error.message : "Internal server error";
    return Response.json({ error: message }, { status: 500 });
  }
}
