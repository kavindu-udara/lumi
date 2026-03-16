/**
 * HTTP Callable: Set custom claims for user (premium, admin, etc.)
 */

import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";

const auth = admin.auth();
const db = admin.firestore();

export const setCustomClaims = functions.https.onCall(async (data, context) => {
  // Verify authentication
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be logged in"
    );
  }

  // Only admins can set claims
  const callerClaims = context.auth.token;
  if (!callerClaims.admin) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only admins can set custom claims"
    );
  }

  const {userId, claims} = data;

  if (!userId || !claims) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "userId and claims are required"
    );
  }

  try {
    // Set custom claims
    await auth.setCustomUserClaims(userId, claims);

    // Update user document if premium status changed
    //  100GB for premium users, 15GB for free users
    if (claims.premium !== undefined) {
      await db.collection("users").doc(userId).update({
        isPremium: claims.premium,
        storageQuota: claims.premium ?
          100 * 1024 * 1024 * 1024 :
          15 * 1024 * 1024 * 1024,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }

    functions.logger.info(`Custom claims set for user ${userId}`, {claims});

    return {success: true};
  } catch (error: any) {
    functions.logger.error("Error setting custom claims:", error);
    throw new functions.https.HttpsError(
      "internal",
      "Failed to set custom claims",
      error.message
    );
  }
});
