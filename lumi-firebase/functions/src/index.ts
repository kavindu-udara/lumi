import * as admin from "firebase-admin";
import type {Response} from "express";
import * as functions from "firebase-functions";

admin.initializeApp();

// Export all function modules
export * from "./albums";
export * from "./auth";
export * from "./notifications";
export * from "./photos";
export * from "./scheduled";
export * from "./search";
export * from "./sharing";
export * from "./storage";

// health check endpoint
export const healthCheck = functions.https.onRequest(
  (req: functions.https.Request, res: Response) => {
    res.status(200).json({
      status: "healthy",
      timestamp: new Date().toISOString(),
      version: "1.0.0",
    });
  }
);
