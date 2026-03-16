import * as admin from "firebase-admin";
import {getFunctions} from "firebase-admin/functions";
import * as functions from "firebase-functions/v1";

const db = admin.firestore();

export const onUserCreate = functions.auth.user().onCreate(async (user) => {
  const {uid, email, displayName, photoURL} = user;

  try {
    // Create user profile document
    await db.collection("users").doc(uid).set({
      userId: uid,
      email: email || "",
      displayName: displayName || "Anonymous",
      avatarUrl: photoURL || null,
      storageUsed: 0,
      storageQuota: 15 * 1024 * 1024 * 1024, // 15GB free tier
      isPremium: false,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Create user settings
    await db.collection("users").doc(uid)
      .collection("settings").doc("preferences").set({
        theme: "light",
        notifications: {
          email: true,
          push: true,
          storageWarnings: true,
        },
        privacy: {
          profileVisible: false,
          photosIndexed: true,
        },
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

    // Create quotas document
    await db.collection("users").doc(uid)
      .collection("quotas").doc("current").set({
        photosCount: 0,
        albumsCount: 0,
        storageUsed: 0,
        lastCalculated: admin.firestore.FieldValue.serverTimestamp(),
      });

    // Log activity
    await db.collection("activityLogs").doc(uid).collection("logs").add({
      action: "user_created",
      resourceType: "user",
      resourceId: uid,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Trigger welcome email (via Cloud Tasks to avoid timeout)
    await getFunctions().taskQueue("email-queue").enqueue({
      type: "welcome",
      userId: uid,
      email: email,
    });

    functions.logger.info(`User profile created for ${uid}`);
  } catch (error) {
    functions.logger.error("Error creating user profile:", error);
    throw error;
  }
});
