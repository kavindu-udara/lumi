package com.example.lumi.adapters;

import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.PopupMenu;

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

    public interface OnAlbumActionListener {
        void onEditAlbum(Album album);
        void onDeleteAlbum(Album album);
    }

    private final List<Album> albums = new ArrayList<>();
    private final OnAlbumClickListener onAlbumClickListener;
    private final OnAlbumActionListener onAlbumActionListener;

    public AlbumAdapter(OnAlbumClickListener onAlbumClickListener) {
        this(onAlbumClickListener, null);
    }

    public AlbumAdapter(OnAlbumClickListener onAlbumClickListener, OnAlbumActionListener onAlbumActionListener) {
        this.onAlbumClickListener = onAlbumClickListener;
        this.onAlbumActionListener = onAlbumActionListener;
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
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = dp(parent, 16);
        container.setPadding(p, p, p, p);

        TextView albumName = new TextView(parent.getContext());
        albumName.setTextSize(16f);
        albumName.setTextColor(parent.getResources().getColor(android.R.color.black, null));

        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        albumName.setLayoutParams(nameLp);
        container.addView(albumName);

        ImageButton optionsButton = new ImageButton(parent.getContext());
        optionsButton.setImageResource(android.R.drawable.ic_menu_more);
        optionsButton.setContentDescription("Album options");
        optionsButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        optionsButton.setOnClickListener(v -> showOptions(v, getBoundAlbum(v)));
        container.addView(optionsButton, new LinearLayout.LayoutParams(dp(parent, 48), dp(parent, 48)));
        card.addView(container);

        return new AlbumViewHolder(card, albumName, optionsButton);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        Album album = albums.get(position);
        holder.albumName.setText(album.getName());
        holder.optionsButton.setTag(album);
        holder.itemView.setOnClickListener(v -> {
            if (onAlbumClickListener != null) {
                onAlbumClickListener.onAlbumClick(album);
            }
        });
    }

    private Album getBoundAlbum(View view) {
        Object tag = view.getTag();
        return tag instanceof Album ? (Album) tag : null;
    }

    private void showOptions(View anchor, Album album) {
        if (album == null || onAlbumActionListener == null) return;
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add("Edit");
        menu.getMenu().add("Delete");
        menu.setOnMenuItemClickListener(item -> {
            if ("Edit".contentEquals(item.getTitle())) {
                onAlbumActionListener.onEditAlbum(album);
            } else {
                onAlbumActionListener.onDeleteAlbum(album);
            }
            return true;
        });
        menu.show();
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    public static class AlbumViewHolder extends RecyclerView.ViewHolder {
        final TextView albumName;
        final ImageButton optionsButton;

        AlbumViewHolder(@NonNull MaterialCardView itemView, TextView albumName, ImageButton optionsButton) {
            super(itemView);
            this.albumName = albumName;
            this.optionsButton = optionsButton;
        }
    }

    private int dp(ViewGroup parent, int value) {
        float density = parent.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
