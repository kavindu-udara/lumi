# Auth

## Stripe subscription webhooks

Stripe activates subscriptions through the webhook route; returning to the Android app only confirms the Checkout redirect. Configure a **test-mode** Stripe webhook endpoint at:

```text
https://<public-backend-host>/api/v1/webhooks/stripe
```

Enable these events:

- `checkout.session.completed`
- `customer.subscription.created`
- `customer.subscription.updated`
- `customer.subscription.deleted`
- `invoice.paid`
- `invoice.payment_failed`

Copy the endpoint signing secret into the backend deployment environment as `STRIPE_WEBHOOK_SECRET`, then redeploy/restart the backend. It must be the signing secret for this exact endpoint and Stripe mode.

For local development, forward Stripe test events with the Stripe CLI:

```bash
stripe listen --forward-to localhost:3000/api/v1/webhooks/stripe
```

Set the `whsec_...` value printed by the CLI as `STRIPE_WEBHOOK_SECRET` and restart Next.js. The hosted Supabase database should then receive rows in `billing_events` and `subscriptions` after a successful checkout.

## Google
Google OAuth callback endpoint - `POST /api/v1/auth/google-callback`

## TODO
Before testing, you must add your Google Maps API key to AndroidManifest.xml:
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY_HERE" />
Get a free API key from: https://console.cloud.google.com/
 
- implement firebase

## Convert everything to firebase auth
- [x] /albums
- [x] /subscription
- [x] /photos
- [x] /photos/upload
