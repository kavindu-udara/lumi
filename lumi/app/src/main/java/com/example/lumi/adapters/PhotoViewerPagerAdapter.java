package com.example.lumi.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.views.ZoomableImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PhotoViewerPagerAdapter extends RecyclerView.Adapter<PhotoViewerPagerAdapter.PhotoViewHolder> {

    private final Context context;
    private final List<HomeGalleryAdapter.GalleryItem> items = new ArrayList<>();
    private final RequestOptions requestOptions;

    public PhotoViewerPagerAdapter(Context context, List<HomeGalleryAdapter.GalleryItem> items) {
        this.context = context;
        this.requestOptions = new RequestOptions()
                .placeholder(new ColorDrawable(Color.BLACK))
                .error(new ColorDrawable(Color.DKGRAY))
                .fitCenter();
        if (items != null) {
            this.items.addAll(items);
        }
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ZoomableImageView imageView = new ZoomableImageView(context);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        imageView.setBackgroundColor(Color.BLACK);
        return new PhotoViewHolder(imageView);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        HomeGalleryAdapter.GalleryItem item = items.get(position);
        holder.imageView.resetZoom();
        if (item.isLocal() && item.getLocalPath() != null) {
            Glide.with(context)
                    .load(new File(item.getLocalPath()))
                    .apply(requestOptions)
                    .into(holder.imageView);
            return;
        }

        String url = item.getPreviewUrl(new API(), new SessionManager(context));
        GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer " + new SessionManager(context).getToken())
                .build());
        Glide.with(context)
                .load(glideUrl)
                .apply(requestOptions)
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public HomeGalleryAdapter.GalleryItem getItem(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ZoomableImageView imageView;

        PhotoViewHolder(@NonNull ZoomableImageView itemView) {
            super(itemView);
            this.imageView = itemView;
        }
    }
}
