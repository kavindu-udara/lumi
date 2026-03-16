import {Timestamp} from "firebase-admin/firestore";

export interface Photo {
  photoId: string;
  userId: string;
  storagePath: string;
  originalName: string;
  mimeType: string;
  fileSize: number;
  width: number;
  height: number;
  takenAt: Timestamp | null;
  uploadedAt: Timestamp;
  location?: GeoPoint;
  isFavorite: boolean;
  isTrashed: boolean;
  trashedAt?: Timestamp | null;
  checksum: string;
  thumbnails: Thumbnails;
  metadata: PhotoMetadata;
  albumIds: string[];
  aiProcessed: boolean;
  aiProcessedAt?: Timestamp;
}

export interface Thumbnails {
  thumb: string;
  small: string;
  medium: string;
  large: string;
}

export interface PhotoMetadata {
  exif: ExifData;
  aiTags: string[];
  faces: FaceDetection[];
  text?: string;
  safeSearch?: SafeSearch;
}

export interface ExifData {
  make?: string;
  model?: string;
  software?: string;
  exposureTime?: number;
  fNumber?: number;
  iso?: number;
  focalLength?: number;
}

export interface FaceDetection {
  boundingBox: BoundingBox;
  confidence: number;
  personId?: string;
}

export interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface SafeSearch {
  adult: string;
  spoof: string;
  medical: string;
  violence: string;
  racy: string;
}

export interface GeoPoint {
  lat: number;
  lng: number;
}

export interface User {
  userId: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
  storageUsed: number;
  storageQuota: number;
  isPremium: boolean;
  createdAt: Timestamp;
}

export interface Album {
  albumId: string;
  userId: string;
  title: string;
  description?: string;
  coverPhotoId?: string;
  photoIds: string[];
  isShared: boolean;
  sharedWith: string[];
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface SharedLink {
  linkId: string;
  userId: string;
  token: string;
  albumId?: string;
  photoIds: string[];
  expiresAt?: Timestamp;
  passwordHash?: string;
  viewCount: number;
  isActive: boolean;
  createdAt: Timestamp;
}

export interface ActivityLog {
  logId: string;
  userId: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  metadata?: Record<string, any>;
  ipAddress?: string;
  userAgent?: string;
  timestamp: Timestamp;
}

export interface ProcessingJob {
  jobId: string;
  photoId: string;
  userId: string;
  filePath: string;
  status: "pending" | "processing" | "completed" | "failed";
  error?: string;
  createdAt: Timestamp;
  completedAt?: Timestamp;
}
