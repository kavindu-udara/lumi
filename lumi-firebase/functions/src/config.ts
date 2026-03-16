export const config = {
  // Storage
  storage: {
    maxFileSize: 50 * 1024 * 1024, // 50MB
    maxPhotosPerUpload: 50,
    allowedMimeTypes: [
      "image/jpeg",
      "image/jpg",
      "image/png",
      "image/webp",
      "image/heic",
      "image/heif",
    ],
  },

  // Thumbnails
  thumbnails: {
    sizes: [
      {name: "thumb", width: 150, height: 150},
      {name: "small", width: 400, height: 400},
      {name: "medium", width: 800, height: 800},
      {name: "large", width: 1600, height: 1600},
    ],
    quality: 85,
    format: "jpeg",
  },

  // Storage Quotas
  quotas: {
    free: 15 * 1024 * 1024 * 1024, // 15GB
    premium: 100 * 1024 * 1024 * 1024, // 100GB
    business: 1000 * 1024 * 1024 * 1024, // 1TB
  },

  // Trash
  trash: {
    retentionDays: 30,
    autoDeleteEnabled: true,
  },

  // Search
  search: {
    algoliaIndex: "photos",
    maxSearchResults: 100,
  },

  // Notifications
  notifications: {
    storageWarningThreshold: 0.9, // 90% usage
    welcomeEmailEnabled: true,
  },

  // Rate Limiting
  rateLimit: {
    uploadPerHour: 100,
    apiCallsPerMinute: 60,
  },
};

export default config;
