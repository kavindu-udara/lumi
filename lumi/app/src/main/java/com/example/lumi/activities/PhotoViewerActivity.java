package com.example.lumi.activities;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.lumi.R;
import com.example.lumi.adapters.HomeGalleryAdapter;
import com.example.lumi.adapters.PhotoViewerPagerAdapter;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.UploadQueueStore;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class PhotoViewerActivity extends AppCompatActivity {

    public static final String EXTRA_START_INDEX = "start_index";
    public static final String EXTRA_ITEMS = "items";

    private ViewPager2 viewPager;
    private TextView positionText;
    private PhotoViewerPagerAdapter adapter;
    private ArrayList<HomeGalleryAdapter.GalleryItem> items;
    private int currentIndex;
    private UploadQueueStore uploadQueueStore;

    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);
        enterFullscreen();

        viewPager = findViewById(R.id.photoPager);
        positionText = findViewById(R.id.positionText);
        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton shareButton = findViewById(R.id.shareButton);
        ImageButton deleteButton = findViewById(R.id.deleteButton);
        ImageButton detailsButton = findViewById(R.id.detailsButton);
        ImageButton playButton = findViewById(R.id.playButton);

        uploadQueueStore = new UploadQueueStore(this);
        sessionManager = new SessionManager(this);

        items = readItemsFromIntent(getIntent());
        currentIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);
        if (currentIndex < 0) {
            currentIndex = 0;
        }

        adapter = new PhotoViewerPagerAdapter(this, items);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(Math.min(currentIndex, Math.max(items.size() - 1, 0)), false);

        updatePositionText(viewPager.getCurrentItem());
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentIndex = position;
                updatePositionText(position);
                updatePlayButton(playButton);
            }
        });

        backButton.setOnClickListener(v -> finish());
        playButton.setOnClickListener(v -> {
            VideoView videoView = findVideoView(viewPager.getChildAt(0));
            if (videoView != null) {
                videoView.start();
                playButton.setVisibility(View.GONE);
            }
        });
        shareButton.setOnClickListener(v -> shareCurrentImage());
        deleteButton.setOnClickListener(v -> showDeleteBottomSheet());
        detailsButton.setOnClickListener(v -> showCurrentImageDetails());
        updatePlayButton(playButton);
    }

    private void updatePlayButton(ImageButton playButton) {
        playButton.setVisibility(getCurrentItem() != null && getCurrentItem().isVideo()
                ? View.VISIBLE : View.GONE);
    }

    private VideoView findVideoView(View view) {
        if (view instanceof VideoView) {
            return (VideoView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                VideoView videoView = findVideoView(group.getChildAt(i));
                if (videoView != null) {
                    return videoView;
                }
            }
        }
        return null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller == null) {
            return;
        }
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.statusBars());
    }

    @SuppressWarnings("unchecked")
    private ArrayList<HomeGalleryAdapter.GalleryItem> readItemsFromIntent(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(EXTRA_ITEMS);
        if (serializable instanceof ArrayList) {
            return (ArrayList<HomeGalleryAdapter.GalleryItem>) serializable;
        }
        return new ArrayList<>();
    }

    private void updatePositionText(int index) {
        int total = items == null ? 0 : items.size();
        if (total == 0) {
            positionText.setText("0/0");
            return;
        }
        positionText.setText((index + 1) + "/" + total);
    }

    private HomeGalleryAdapter.GalleryItem getCurrentItem() {
        if (items == null || items.isEmpty() || currentIndex < 0 || currentIndex >= items.size()) {
            return null;
        }
        return items.get(currentIndex);
    }

    private void shareCurrentImage() {
        HomeGalleryAdapter.GalleryItem item = getCurrentItem();
        if (item == null) {
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File sourceFile;
                if (item.isLocal() && !TextUtils.isEmpty(item.getLocalPath())) {
                    sourceFile = new File(item.getLocalPath());
                } else {
                    String url = item.getPreviewUrl(new API(), sessionManager);
                    GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                            .addHeader("Authorization", "Bearer " + sessionManager.getToken())
                            .build());
                    sourceFile = Glide.with(this)
                            .asFile()
                            .load(glideUrl)
                            .submit()
                            .get();
                }

                if (sourceFile == null || !sourceFile.exists()) {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "Unable to share image", android.widget.Toast.LENGTH_SHORT).show());
                    return;
                }

                File shareFile = stageFileForShare(sourceFile);
                if (shareFile == null || !shareFile.exists()) {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "Unable to share image", android.widget.Toast.LENGTH_SHORT).show());
                    return;
                }

                Uri uri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        shareFile
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.setClipData(ClipData.newUri(getContentResolver(), "shared_image", uri));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                runOnUiThread(() -> {
                    if (shareIntent.resolveActivity(getPackageManager()) == null) {
                        android.widget.Toast.makeText(this, "No app available to share", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share image"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> android.widget.Toast.makeText(this, "Unable to share image", android.widget.Toast.LENGTH_SHORT).show());
            }
        });
    }

    private File stageFileForShare(File sourceFile) throws IOException {
        File shareDir = new File(getCacheDir(), "share");
        if (!shareDir.exists() && !shareDir.mkdirs()) {
            throw new IOException("Failed to create share cache directory");
        }

        String extension = ".jpg";
        String sourceName = sourceFile.getName();
        int dot = sourceName.lastIndexOf('.');
        if (dot >= 0) {
            extension = sourceName.substring(dot);
        }

        File staged = new File(shareDir, "shared_" + System.currentTimeMillis() + extension);
        copyFile(sourceFile, staged);
        return staged;
    }

    private void copyFile(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private void showDeleteBottomSheet() {
        HomeGalleryAdapter.GalleryItem item = getCurrentItem();
        if (item == null) {
            return;
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Delete photo?");
        title.setTextSize(18f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

        TextView message = new TextView(this);
        message.setText("This photo will be removed from your library.");
        android.widget.LinearLayout.LayoutParams msgParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        msgParams.topMargin = dp(8);
        message.setLayoutParams(msgParams);

        android.widget.Button deleteBtn = new android.widget.Button(this);
        deleteBtn.setText("Delete");
        android.widget.LinearLayout.LayoutParams deleteParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        deleteParams.topMargin = dp(16);
        deleteBtn.setLayoutParams(deleteParams);

        android.widget.Button cancelBtn = new android.widget.Button(this);
        cancelBtn.setText("Cancel");
        android.widget.LinearLayout.LayoutParams cancelParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cancelParams.topMargin = dp(8);
        cancelBtn.setLayoutParams(cancelParams);

        deleteBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            doDelete(item);
        });
        cancelBtn.setOnClickListener(v -> bottomSheetDialog.dismiss());

        root.addView(title);
        root.addView(message);
        root.addView(deleteBtn);
        root.addView(cancelBtn);

        bottomSheetDialog.setContentView(root);
        bottomSheetDialog.show();
    }

    private void doDelete(HomeGalleryAdapter.GalleryItem item) {
        if (item.isLocal()) {
            if (!TextUtils.isEmpty(item.getQueueId())) {
                uploadQueueStore.removeById(item.getQueueId());
            }
            if (!TextUtils.isEmpty(item.getLocalPath())) {
                File file = new File(item.getLocalPath());
                if (file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
            android.widget.Toast.makeText(this, "Photo deleted", android.widget.Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
            return;
        }

        String photoId = item.getImageId();

        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty() || photoId == null || photoId.trim().isEmpty()) {
            android.widget.Toast.makeText(this, "Missing delete info", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    JsonObject reqObj = new JsonObject();
                    reqObj.addProperty("photoId", photoId);

                    Log.i("PhotoViewerActivity", "Sending delete request for photoId: " + photoId);
                    API.DELETE("/photos", token, reqObj);

                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(this, "Photo deleted", android.widget.Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "Failed to delete photo", android.widget.Toast.LENGTH_SHORT).show());
                }
            });
    }

    private void showCurrentImageDetails() {
        HomeGalleryAdapter.GalleryItem item = getCurrentItem();
        if (item == null) {
            return;
        }

        String createdAt = item.getCreatedAt() <= 0
                ? "Unknown"
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(item.getCreatedAt()));
        String latitude = item.getLatitude() == null ? "Unknown" : String.valueOf(item.getLatitude());
        String longitude = item.getLongitude() == null ? "Unknown" : String.valueOf(item.getLongitude());
        String source = item.isLocal() ? "Queued local photo" : "Server photo";
        String id = item.isLocal() ? item.getQueueId() : item.getImageId();

        String details = "Source: " + source
                + "\nID: " + (id == null ? "Unknown" : id)
                + "\nCreated: " + createdAt
                + "\nLatitude: " + latitude
                + "\nLongitude: " + longitude;

        new AlertDialog.Builder(this)
                .setTitle("Photo details")
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
