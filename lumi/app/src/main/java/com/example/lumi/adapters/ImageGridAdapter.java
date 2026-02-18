package com.example.lumi.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.lumi.R;
import com.example.lumi.models.Image;

import java.util.List;

public class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.ImageViewHolder> {

    private List<Image> images;
    private Context context;
    private static final int ITEM_SIZE = 200;
    private static final int MARGIN = 1;

    public ImageGridAdapter(List<Image> images, Context context) {
        this.images = images;
        this.context = context;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
        FrameLayout frameLayout = new FrameLayout(context);
        RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(ITEM_SIZE, ITEM_SIZE);
        layoutParams.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);
        frameLayout.setLayoutParams(layoutParams);
        frameLayout.setBackgroundColor(Color.parseColor("#E0E0E0"));

        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frameLayout.addView(imageView);

        return  new ImageViewHolder(frameLayout, imageView);
    }


    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        Image image = images.get(position);
        Glide.with(context)
                .load(image.getUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ImageViewHolder(@NonNull FrameLayout itemView, ImageView imageView) {
            super(itemView);
            this.imageView = imageView;
        }
    }
}
