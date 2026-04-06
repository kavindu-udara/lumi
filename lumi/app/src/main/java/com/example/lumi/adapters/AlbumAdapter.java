package com.example.lumi.adapters;

import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lumi.R;
import com.example.lumi.models.Album;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder> {

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album);
    }

    private final List<Album> albums = new ArrayList<>();
    private final OnAlbumClickListener onAlbumClickListener;

    public AlbumAdapter(OnAlbumClickListener onAlbumClickListener) {
        this.onAlbumClickListener = onAlbumClickListener;
    }

    public void submitAlbums(List<Album> items) {
        albums.clear();
        if (items != null) {
            albums.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialCardView card = new MaterialCardView(parent.getContext());
        RecyclerView.LayoutParams cardLp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int vm = dp(parent, 6);
        cardLp.setMargins(0, vm, 0, vm);
        card.setLayoutParams(cardLp);
        card.setRadius(dp(parent, 14));
        card.setCardBackgroundColor(parent.getResources().getColor(android.R.color.white, null));
        card.setStrokeColor(parent.getResources().getColor(R.color.purple_500, null));
        card.setStrokeWidth(dp(parent, 1));

        LinearLayout container = new LinearLayout(parent.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int p = dp(parent, 16);
        container.setPadding(p, p, p, p);

        TextView albumName = new TextView(parent.getContext());
        albumName.setTextSize(16f);
        albumName.setTextColor(parent.getResources().getColor(android.R.color.black, null));

        container.addView(albumName);
        card.addView(container);

        return new AlbumViewHolder(card, albumName);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        Album album = albums.get(position);
        holder.albumName.setText(album.getName());
        holder.itemView.setOnClickListener(v -> {
            if (onAlbumClickListener != null) {
                onAlbumClickListener.onAlbumClick(album);
            }
        });
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public static class AlbumViewHolder extends RecyclerView.ViewHolder {
        final TextView albumName;

        AlbumViewHolder(@NonNull MaterialCardView itemView, TextView albumName) {
            super(itemView);
            this.albumName = albumName;
        }
    }

    private int dp(ViewGroup parent, int value) {
        float density = parent.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}

