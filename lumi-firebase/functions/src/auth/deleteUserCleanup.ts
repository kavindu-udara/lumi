import * as functions from "firebase-functions/v1";
import * as admin from "firebase-admin";

const db = admin.firestore();
const bucket = admin.storage().bucket();

export const deleteUserCleanup = functions.auth
  .user()
  .onDelete(async (user) => {
    const {uid} = user;

    try {
      functions.logger.info(`Starting cleanup for deleted user ${uid}`);

      // Get all user photos
      const photosSnapshot = await db
        .collection("photos")
        .where("userId", "==", uid)
        .get();

      // Delete photos from storage
      const deletePromises: Promise<void>[] = [];

      photosSnapshot.docs.forEach((doc) => {
        const data = doc.data();

        // Delete original
        deletePromises.push(
          bucket
            .file(data.storagePath)
            .delete()
            .then(() => undefined, () => undefined),
        );

        // Delete thumbnails
        if (data.thumbnails) {
          Object.values(
            data.thumbnails as Record<string, string>
          ).forEach((path) => {
            deletePromises.push(
              bucket
                .file(path)
                .delete()
                .then(() => undefined, () => undefined),
            );
          });
        }
      });

      // Wait for storage deletions (with timeout)
      await Promise.allSettled(deletePromises);

      // Delete Firestore documents (use batch for large collections)
      const batches: admin.firestore.WriteBatch[] = [];
      let currentBatch = db.batch();
      let operationCount = 0;

      // Delete photos
      photosSnapshot.docs.forEach((doc) => {
        currentBatch.delete(doc.ref);
        operationCount++;

        if (operationCount >= 450) {
          batches.push(currentBatch);
          currentBatch = db.batch();
          operationCount = 0;
        }
      });

      if (operationCount > 0) {
        batches.push(currentBatch);
      }

      // Execute batches
      for (const batch of batches) {
        await batch.commit();
      }

      // Delete user profile and subcollections
      await db.collection("users").doc(uid).delete();
      await db
        .collection("albums")
        .where("userId", "==", uid)
        .get()
        .then((snapshot) => {
          const batch = db.batch();
          snapshot.docs.forEach((doc) => batch.delete(doc.ref));
          return batch.commit();
        });

      functions.logger.info(`Cleanup completed for user ${uid}`);
    } catch (error) {
      functions.logger.error("Error during user cleanup:", error);
      // Don't throw - user is already deleted
    }
  });
