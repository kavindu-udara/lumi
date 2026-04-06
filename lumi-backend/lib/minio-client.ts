import { exec } from "child_process";
import * as Minio from "minio";

export const CLIENT_PHOTO_BUCKET_NAME =
  process.env.MINIO_BUCKET_NAME || "lumi-photos";

export const minioClient = new Minio.Client({
  endPoint: process.env.MINIO_ENDPOINT!,
  port: parseInt(process.env.MINIO_PORT!, 10),
  useSSL: process.env.MINIO_USE_SSL === "true",
  accessKey: process.env.MINIO_ACCESS_KEY!,
  secretKey: process.env.MINIO_SECRET_KEY!,
});

export const createBucketIfNotExists = async (bucketName: string) => {
  const exists = await minioClient.bucketExists(bucketName);
  if (!exists) {
    await minioClient.makeBucket(`${bucketName}`, "us-east-1");
    console.log(`Bucket "${bucketName}" created successfully.`);
  } else {
    console.log(`Bucket "${bucketName}" already exists.`);
  }
};

export const uploadFile = async (
  bucketName: string,
  buffer: Buffer,
  objectName: string,
) => {
  try {
    return await minioClient.putObject(bucketName, objectName, buffer);
    // console.log(
    //   `File "${objectName}" uploaded to bucket "${bucketName}".`,
    // );
  } catch (error) {
    console.error("Error uploading file:", error);
    const message = error instanceof Error ? error.message : String(error);
    throw new Error(`Failed to upload file: ${message}`);
  }
};

// list objects in bucket
export const listObjects = async (bucketName: string) => {
  try {
    const objectsList = await minioClient.listObjectsV2(bucketName, "", true);
    const objects = [];
    for await (const obj of objectsList) {
      objects.push(obj);
    }
    console.log(`Objects in bucket "${bucketName}":`, objects);
    return objects;
  } catch (error) {
    console.error("Error listing objects:", error);
    const message = error instanceof Error ? error.message : String(error);
    throw new Error(`Failed to list objects: ${message}`);
  }
};
