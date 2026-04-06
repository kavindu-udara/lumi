import Stripe from "stripe";

const stripeSecretKey = process.env.STRIPE_SECRET_KEY;

const stripe = stripeSecretKey
  ? new Stripe(stripeSecretKey, {
            apiVersion: "2026-03-25.dahlia",
    })
  : null;

export default stripe;