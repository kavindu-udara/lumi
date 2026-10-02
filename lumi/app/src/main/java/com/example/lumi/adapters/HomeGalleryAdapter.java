package com.example.lumi.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class HomeGalleryAdapter extends RecyclerView.Adapter<HomeGalleryAdapter.PlaceholderViewHolder> {

    private final List<GalleryItem> items = new ArrayList<>();
    private final Context context;
    private final RequestOptions requestOptions;
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(int position, GalleryItem item);
    }

    public HomeGalleryAdapter(Context context, List<GalleryItem> photos) {
        this.context = context;
        ColorDrawable grayPlaceholder = new ColorDrawable(Color.parseColor("#BDBDBD"));
        requestOptions = new RequestOptions()
                .placeholder(grayPlaceholder)
                .error(grayPlaceholder)
                .centerCrop();
        setPhotos(photos);
    }

    public void setPhotos(List<GalleryItem> photos) {
        items.clear();
        if (photos != null) {
            items.addAll(photos);
        }
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public List<GalleryItem> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public PlaceholderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView imageView = new ImageView(context);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(84)
        );
        int margin = dp(2);
        params.setMargins(margin, margin, margin, margin);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundColor(Color.parseColor("#BDBDBD"));

        return new PlaceholderViewHolder(imageView);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceholderViewHolder holder, int position) {
        GalleryItem item = items.get(position);
        holder.imageView.setImageDrawable(new ColorDrawable(Color.parseColor("#BDBDBD")));

        if (item == null) {
            return;
        }

        if (item.isLocal() && item.getLocalPath() != null && !item.getLocalPath().trim().isEmpty()) {
            Glide.with(context)
                    .load(new File(item.getLocalPath()))
                    .apply(requestOptions)
                    .into(holder.imageView);
            return;
        }

        if (item.getImageId() != null && !item.getImageId().trim().isEmpty()) {
            String url = item.getPreviewUrl(new API(), new SessionManager(context));
            GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                    .addHeader("Authorization", "Bearer " + new SessionManager(context).getToken())
                    .build());
            Glide.with(context)
                    .load(glideUrl)
                    .apply(requestOptions)
                    .into(holder.imageView);
        }

        holder.imageView.setOnClickListener(v -> {
            if (onItemClickListener != null && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                onItemClickListener.onItemClick(holder.getBindingAdapterPosition(), item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    public static class PlaceholderViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        public PlaceholderViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = (ImageView) itemView;
        }
    }

    public static class GalleryItem implements Serializable {
        private final String photoId;
        private final String imageId;
        private final String albumId;
        private final String localPath;
        private final long createdAt;
        private final String queueId;
        private final Double latitude;
        private final Double longitude;

        private GalleryItem(
                String photoId,
                String imageId,
                String albumId,
                String localPath,
                long createdAt,
                String queueId,
                Double latitude,
                Double longitude
        ) {
            this.photoId = photoId;
            this.imageId = imageId;
            this.albumId = albumId;
            this.localPath = localPath;
            this.createdAt = createdAt;
            this.queueId = queueId;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public static GalleryItem remote(String photoId, String imageId, long createdAt, Double latitude, Double longitude) {
            return remote(photoId, imageId, null, createdAt, latitude, longitude);
        }

        public static GalleryItem remote(String photoId, String imageId, String albumId, long createdAt, Double latitude, Double longitude) {
            return new GalleryItem(photoId, imageId, albumId, null, createdAt, null, latitude, longitude);
        }

        public static GalleryItem local(String queueId, String localPath, long createdAt, Double latitude, Double longitude) {
            return new GalleryItem(null, null, null, localPath, createdAt, queueId, latitude, longitude);
        }

        public String getPhotoId() {
            return photoId;
        }

        public String getImageId() {
            return imageId;
        }

        public String getAlbumId() {
            return albumId;
        }

        public String getPreviewUrl(API api, SessionManager sessionManager) {
            if (imageId != null && imageId.contains("/")) {
                return api.getPreviewUrl(imageId);
            }

            String userId = sessionManager.getUser() != null && sessionManager.getUser().has("id")
                    ? sessionManager.getUser().get("id").getAsString() : "";
            return api.getPreviewUrl(userId, albumId, imageId);
        }

        public String getLocalPath() {
            return localPath;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public String getQueueId() {
            return queueId;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public boolean isLocal() {
            return localPath != null;
        }
    }
}
