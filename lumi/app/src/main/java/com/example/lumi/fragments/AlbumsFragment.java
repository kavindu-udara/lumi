package com.example.lumi.fragments;

import android.Manifest;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ProgressBar;

import com.example.lumi.R;
import com.example.lumi.activities.PhotoViewerActivity;
import com.example.lumi.adapters.AlbumAdapter;
import com.example.lumi.adapters.HomeGalleryAdapter;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.Toast;
import com.example.lumi.models.Album;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A simple {@link Fragment} subclass for managing photo albums.
 */
public class AlbumsFragment extends Fragment {

    private static final int PHOTO_GRID_SPAN_COUNT = 5;

    AppCompatActivity parent;
    private SessionManager sessionManager;
    private AlbumAdapter albumAdapter;
    private HomeGalleryAdapter photoAdapter;
    private RecyclerView albumsRecyclerView;
    private FloatingActionButton fabCreateAlbum;
    private FloatingActionButton fabCaptureAlbumPhoto;
    private View albumPhotosHeader;
    private TextView selectedAlbumTitle;
    private ProgressBar albumPhotosLoading;
    private ProgressBar albumsLoading;
    private boolean isShowingAlbumPhotos;
    private String selectedAlbumId;

    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<Uri> captureVideoLauncher;
    private File pendingCaptureFile;
    private File pendingVideoFile;

    public AlbumsFragment() {
        // Required empty public constructor for Fragment recreation.
    }

    public AlbumsFragment(AppCompatActivity parent) {
        this.parent = parent;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            parent = (AppCompatActivity) context;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionManager = new SessionManager(requireContext());
        registerLaunchers();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_albums, container, false);

        albumsRecyclerView = view.findViewById(R.id.albums_recycler_view);
        albumsRecyclerView.setLayoutManager(new LinearLayoutManager(parent));
        albumAdapter = new AlbumAdapter(this::onAlbumSelected, new AlbumAdapter.OnAlbumActionListener() {
            @Override
            public void onEditAlbum(Album album) {
                showEditAlbumDialog(album);
            }

            @Override
            public void onDeleteAlbum(Album album) {
                confirmDeleteAlbum(album);
            }

            @Override
            public void onShareAlbum(Album album) {
                shareAlbum(album);
            }

            @Override
            public void onCloseShare(Album album) {
                confirmCloseShare(album);
            }
        });
        albumsRecyclerView.setAdapter(albumAdapter);

        albumPhotosHeader = view.findViewById(R.id.album_photos_header);
        selectedAlbumTitle = view.findViewById(R.id.selected_album_title);
        albumPhotosLoading = view.findViewById(R.id.album_photos_loading);
        albumsLoading = view.findViewById(R.id.albums_loading);
        TextView backToAlbums = view.findViewById(R.id.back_to_albums);
        backToAlbums.setOnClickListener(v -> showAlbumList());

        fabCreateAlbum = view.findViewById(R.id.fab_create_album);
        fabCreateAlbum.setOnClickListener(v -> showCreateAlbumDialog());

        fabCaptureAlbumPhoto = view.findViewById(R.id.fab_capture_album_photo);
        fabCaptureAlbumPhoto.setOnClickListener(v -> onCameraFabClicked());

        loadAlbums();

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (isShowingAlbumPhotos) {
                            showAlbumList();
                        } else {
                            setEnabled(false);
                            requireActivity().onBackPressed();
                        }
                    }
                });
    }

    private void onAlbumSelected(Album album) {
        if (album == null || album.getId() == null || album.getId().trim().isEmpty()) {
            Toast.warning(parent, "Invalid album");
            return;
        }

        if (photoAdapter == null) {
            photoAdapter = new HomeGalleryAdapter(parent, new ArrayList<>());
            photoAdapter.setOnItemClickListener((position, item) -> openPhotoViewer(position));
        }

        selectedAlbumId = album.getId();
        albumsRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), PHOTO_GRID_SPAN_COUNT));
        albumsRecyclerView.setAdapter(photoAdapter);
        if (fabCreateAlbum != null) {
            fabCreateAlbum.setVisibility(View.GONE);
        }
        if (fabCaptureAlbumPhoto != null) {
            fabCaptureAlbumPhoto.setVisibility(View.VISIBLE);
        }
        if (selectedAlbumTitle != null) {
            selectedAlbumTitle.setText(getString(R.string.album_photos_title, album.getName()));
        }
        if (albumPhotosHeader != null) {
            albumPhotosHeader.setVisibility(View.VISIBLE);
        }
        isShowingAlbumPhotos = true;
        setAlbumPhotosLoading(true);

        loadPhotosForAlbum(album.getId());
    }

    private void showAlbumList() {
        albumsRecyclerView.setLayoutManager(new LinearLayoutManager(parent));
        albumsRecyclerView.setAdapter(albumAdapter);
        if (fabCreateAlbum != null) {
            fabCreateAlbum.setVisibility(View.VISIBLE);
        }
        if (fabCaptureAlbumPhoto != null) {
            fabCaptureAlbumPhoto.setVisibility(View.GONE);
        }
        if (albumPhotosHeader != null) {
            albumPhotosHeader.setVisibility(View.GONE);
        }
        selectedAlbumId = null;
        isShowingAlbumPhotos = false;
        setAlbumPhotosLoading(false);
    }

    private void setAlbumPhotosLoading(boolean loading) {
        if (albumPhotosLoading != null) {
            albumPhotosLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            albumPhotosLoading.setClickable(loading);
        }
        if (albumsRecyclerView != null) {
            albumsRecyclerView.setAlpha(loading ? 0.45f : 1f);
        }
    }

    private void setAlbumsLoading(boolean loading) {
        if (albumsLoading != null) {
            albumsLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (albumsRecyclerView != null && !isShowingAlbumPhotos) {
            albumsRecyclerView.setAlpha(loading ? 0.45f : 1f);
        }
    }

    private void registerLaunchers() {
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        launchCamera();
                    } else if (isAdded()) {
                        android.widget.Toast.makeText(requireContext(), getString(R.string.camera_permission_required), android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && pendingCaptureFile != null) {
                        onPhotoCaptured(pendingCaptureFile);
                    } else if (pendingCaptureFile != null && pendingCaptureFile.exists()) {
                        // Clean up canceled captures.
                        pendingCaptureFile.delete();
                    }
                    pendingCaptureFile = null;
                }
        );

        captureVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.CaptureVideo(),
                success -> {
                    if (success && pendingVideoFile != null) {
                        onVideoCaptured(pendingVideoFile);
                    } else if (pendingVideoFile != null && pendingVideoFile.exists()) {
                        pendingVideoFile.delete();
                    }
                    pendingVideoFile = null;
                }
        );
    }

    private void onCameraFabClicked() {
        if (!isShowingAlbumPhotos) {
            return;
        }

        if (hasCameraPermission()) {
            showCaptureModeDialog();
            return;
        }
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void showCaptureModeDialog() {
        if (!isAdded()) {
            return;
        }

        String[] options = new String[]{getString(R.string.capture_photo), getString(R.string.record_video)};
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.create))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchCamera();
                    } else {
                        launchVideoRecorder();
                    }
                })
                .show();
    }

    private boolean hasCameraPermission() {
        Context context = requireContext();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void launchCamera() {
        try {
            File imageFile = createImageFile();
            pendingCaptureFile = imageFile;
            Uri outputUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    imageFile
            );
            takePictureLauncher.launch(outputUri);
        } catch (IOException e) {
            if (isAdded()) {
                android.widget.Toast.makeText(requireContext(), getString(R.string.upload_photo_failed), android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchVideoRecorder() {
        try {
            File videoFile = createVideoFile();
            pendingVideoFile = videoFile;
            Uri outputUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    videoFile
            );
            captureVideoLauncher.launch(outputUri);
        } catch (IOException e) {
            if (isAdded()) {
                android.widget.Toast.makeText(requireContext(), getString(R.string.upload_video_failed), android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile() throws IOException {
        File picturesDir = requireContext().getExternalFilesDir("Pictures");
        if (picturesDir == null) {
            throw new IOException("Pictures directory is unavailable");
        }
        if (!picturesDir.exists() && !picturesDir.mkdirs()) {
            throw new IOException("Failed to create pictures directory");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(picturesDir, "LUMI_" + timestamp + ".jpg");
    }

    private File createVideoFile() throws IOException {
        File picturesDir = requireContext().getExternalFilesDir("Pictures");
        if (picturesDir == null) {
            throw new IOException("Pictures directory is unavailable");
        }
        if (!picturesDir.exists() && !picturesDir.mkdirs()) {
            throw new IOException("Failed to create pictures directory");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(picturesDir, "LUMI_" + timestamp + ".mp4");
    }

    private void onPhotoCaptured(File imageFile) {
        uploadCapturedMedia(imageFile, selectedAlbumId, getString(R.string.photo_uploaded_successfully), getString(R.string.upload_photo_failed));
    }

    private void onVideoCaptured(File videoFile) {
        uploadCapturedMedia(videoFile, selectedAlbumId, getString(R.string.video_uploaded_successfully), getString(R.string.upload_video_failed));
    }

    private void uploadCapturedMedia(File mediaFile, String albumId, String successMessage, String failedMessage) {
        String token = sessionManager.getToken();
        if (token == null) {
            setAlbumPhotosLoading(false);
            Toast.error(parent, "User not logged in");
            return;
        }

        new Thread(() -> {
                try {
                    JsonObject metadata = new JsonObject();
                    long now = System.currentTimeMillis();
                    metadata.addProperty("capturedAt", String.valueOf(now));
                    metadata.addProperty("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(now)));
                    metadata.addProperty("storagePath", mediaFile.getAbsolutePath());

                    String endpoint = buildUploadEndpoint(albumId);
                    boolean uploaded = API.uploadImage(endpoint, token, mediaFile, metadata);

                    if (parent != null) {
                        parent.runOnUiThread(() -> {
                            if (uploaded) {
                                Toast.success(parent, successMessage);
                                if (albumId != null && !albumId.trim().isEmpty()) {
                                    loadPhotosForAlbum(albumId);
                                } else {
                                    loadAlbums();
                                }
                            } else {
                                Toast.error(parent, failedMessage);
                            }
                        });
                    }

                    if (uploaded && mediaFile.exists()) {
                        mediaFile.delete();
                    }
                } catch (IOException e) {
                    if (parent != null) {
                        parent.runOnUiThread(() -> Toast.error(parent, failedMessage));
                    }
                }
            }).start();
    }

    private String buildUploadEndpoint( String albumId) {
        String base = "/photos/upload";
        if (albumId == null || albumId.trim().isEmpty()) {
            // Backend defaults to recent album when albumId is omitted.
            return base;
        }
        return base + "?albumId=" + Uri.encode(albumId);
    }

    private void openPhotoViewer(int startIndex) {
        if (photoAdapter == null) {
            return;
        }
        Intent intent = new Intent(requireContext(), PhotoViewerActivity.class);
        intent.putExtra(PhotoViewerActivity.EXTRA_START_INDEX, startIndex);
        intent.putExtra(PhotoViewerActivity.EXTRA_ITEMS, new ArrayList<>(photoAdapter.getItems()));
        startActivity(intent);
    }

    private void showCreateAlbumDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(parent);
        LayoutInflater inflater = LayoutInflater.from(parent);
        View dialogView = inflater.inflate(R.layout.dialog_create_album, null);
        
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        
        TextInputEditText albumNameInput = dialogView.findViewById(R.id.album_name_input);
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_create).setOnClickListener(v -> {
            String albumName = albumNameInput.getText() != null
                    ? albumNameInput.getText().toString().trim()
                    : "";
            if (albumName.isEmpty()) {
                Toast.warning(parent, "Album name cannot be empty");
            } else {
                createAlbum(albumName);
                dialog.dismiss();
            }

        });
        
        dialog.show();
    }

    private void showEditAlbumDialog(Album album) {
        if (album == null || !isAdded()) return;
        View dialogView = LayoutInflater.from(parent).inflate(R.layout.dialog_create_album, null);
        TextView title = dialogView.findViewById(R.id.album_dialog_title);
        title.setText("Edit Album");
        TextInputEditText input = dialogView.findViewById(R.id.album_name_input);
        input.setText(album.getName());
        input.setSelection(input.length());
        AlertDialog dialog = new AlertDialog.Builder(parent).setView(dialogView).create();
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        TextView saveButton = dialogView.findViewById(R.id.btn_create);
        saveButton.setText("Save");
        saveButton.setOnClickListener(v -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.warning(parent, "Album name cannot be empty");
                return;
            }
            dialog.dismiss();
            updateAlbum(album, name);
        });
        dialog.show();
    }

    private void confirmDeleteAlbum(Album album) {
        if (album == null || !isAdded()) return;
        new AlertDialog.Builder(parent)
                .setTitle("Delete album?")
                .setMessage("This will permanently delete the album and all photos in it.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> deleteAlbum(album))
                .show();
    }

    private void shareAlbum(Album album) {
        String token = sessionManager.getToken();
        if (token == null) {
            Toast.error(parent, "User not logged in");
            return;
        }
        new Thread(() -> {
            try {
                JsonElement response;
                if (album.isShared()) {
                    response = API.GET("/albums/" + Uri.encode(album.getId()) + "/share", token);
                } else {
                    response = API.POST("/albums/" + Uri.encode(album.getId()) + "/share", token, new JsonObject());
                }
                String url = extractShareUrl(response);
                parent.runOnUiThread(() -> {
                    if (url == null) {
                        Toast.error(parent, getString(R.string.album_share_failed));
                        return;
                    }
                    album.setShared(true);
                    album.setShareUrl(url);
                    showShareDialog(url);
                });
            } catch (Exception error) {
                parent.runOnUiThread(() -> Toast.error(parent, getString(R.string.album_share_failed)));
            }
        }).start();
    }

    private String extractShareUrl(JsonElement response) {
        if (response == null || !response.isJsonObject()) return null;
        JsonObject root = response.getAsJsonObject();
        if (!root.has("share") || root.get("share").isJsonNull() || !root.get("share").isJsonObject()) return null;
        JsonElement url = root.getAsJsonObject("share").get("url");
        return url != null && url.isJsonPrimitive() && !url.getAsString().trim().isEmpty()
                ? url.getAsString().trim() : null;
    }

    private void showShareDialog(String url) {
        if (!isAdded()) return;
        android.widget.LinearLayout content = new android.widget.LinearLayout(parent);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, 0);

        TextView link = new TextView(parent);
        link.setText(url);
        link.setTextIsSelectable(true);
        link.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        content.addView(link);

        new AlertDialog.Builder(parent)
                .setTitle(R.string.album_share_title)
                .setMessage(R.string.album_share_message)
                .setView(content)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.copy_link, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) parent.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Lumi album link", url));
                        Toast.success(parent, getString(R.string.album_link_copied));
                    }
                })
                .show();
    }

    private void confirmCloseShare(Album album) {
        if (!isAdded()) return;
        new AlertDialog.Builder(parent)
                .setTitle(R.string.close_album_website_title)
                .setMessage(R.string.close_album_website_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.close_website, (dialog, which) -> closeShare(album))
                .show();
    }

    private void closeShare(Album album) {
        String token = sessionManager.getToken();
        if (token == null) {
            Toast.error(parent, "User not logged in");
            return;
        }
        new Thread(() -> {
            try {
                JsonElement response = API.DELETE("/albums/" + Uri.encode(album.getId()) + "/share", token);
                parent.runOnUiThread(() -> {
                    if (response == null) {
                        Toast.error(parent, getString(R.string.album_share_close_failed));
                        return;
                    }
                    album.setShared(false);
                    album.setShareUrl(null);
                    Toast.success(parent, getString(R.string.album_share_closed));
                    loadAlbums();
                });
            } catch (Exception error) {
                parent.runOnUiThread(() -> Toast.error(parent, getString(R.string.album_share_close_failed)));
            }
        }).start();
    }

    private void updateAlbum(Album album, String name) {
        String token = sessionManager.getToken();
        if (token == null) {
            Toast.error(parent, "User not logged in");
            return;
        }
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("name", name);
                JsonElement response = API.PUT("/albums/" + Uri.encode(album.getId()), token, body);
                parent.runOnUiThread(() -> {
                    if (response != null) {
                        Toast.success(parent, "Album updated successfully");
                        loadAlbums();
                    } else {
                        Toast.error(parent, "Failed to update album");
                    }
                });
            } catch (Exception error) {
                parent.runOnUiThread(() -> Toast.error(parent, "Failed to update album"));
            }
        }).start();
    }

    private void deleteAlbum(Album album) {
        String token = sessionManager.getToken();
        if (token == null) {
            Toast.error(parent, "User not logged in");
            return;
        }
        new Thread(() -> {
            try {
                JsonElement response = API.DELETE("/albums/" + Uri.encode(album.getId()), token);
                parent.runOnUiThread(() -> {
                    if (response != null) {
                        if (album.getId().equals(selectedAlbumId)) showAlbumList();
                        Toast.success(parent, "Album deleted successfully");
                        loadAlbums();
                    } else {
                        Toast.error(parent, "Failed to delete album");
                    }
                });
            } catch (Exception error) {
                parent.runOnUiThread(() -> Toast.error(parent, "Failed to delete album"));
            }
        }).start();
    }

    private void createAlbum(String albumName) {
        String token = sessionManager.getToken();
        if (token == null) {
            Toast.error(parent, "User not logged in");
            return;
        }

        new Thread(() -> {
                    try {
                        JsonObject requestBody = new JsonObject();
                        requestBody.addProperty("name", albumName);

                        String endpoint = "/albums";
                        JsonObject response = API.POST(endpoint, token, requestBody);

                        // Show success message on UI thread
                        if (parent != null) {
                            parent.runOnUiThread(() -> {
                                if (response != null) {
                                    Toast.success(parent, "Album created successfully");
                                    loadAlbums();
                                } else {
                                    Toast.error(parent, "Failed to create album");
                                }
                            });
                        }
                    } catch (IOException e) {
                        if (parent != null) {
                            parent.runOnUiThread(() -> {
                                Toast.error(parent, "Error creating album: " + e.getMessage());
                            });
                        }
                    }
                }).start();
    }

    private void loadAlbums() {
        String token = sessionManager.getToken();
        if (token == null) {
            setAlbumsLoading(false);
            return;
        }

        setAlbumsLoading(true);
        new Thread(() -> {
                try {
                    String endpoint = "/albums";
                    JsonElement response = API.GET(endpoint, token);
                    List<Album> albums = parseAlbums(response);

                    if (parent != null) {
                        parent.runOnUiThread(() -> {
                            showAlbumList();
                            albumAdapter.submitAlbums(albums);
                            setAlbumsLoading(false);
                        });
                    }
                } catch (IOException e) {
                    if (parent != null) {
                        parent.runOnUiThread(() -> {
                            setAlbumsLoading(false);
                            Toast.error(parent, getString(R.string.albums_load_failed));
                        });
                    }
                }
            }).start();
    }

    private List<Album> parseAlbums(JsonElement response) {
        List<Album> albums = new ArrayList<>();
        if (response == null || !response.isJsonObject()) {
            return albums;
        }

        JsonObject responseObject = response.getAsJsonObject();
        if (!responseObject.has("albums") || !responseObject.get("albums").isJsonArray()) {
            return albums;
        }

        JsonArray albumArray = responseObject.getAsJsonArray("albums");
        for (JsonElement albumElement : albumArray) {
            if (!albumElement.isJsonObject()) {
                continue;
            }

            JsonObject albumObject = albumElement.getAsJsonObject();
            String id = readStringField(albumObject, "_id");
            if (id.isEmpty()) {
                id = readStringField(albumObject, "id");
            }
            if (id.isEmpty()) {
                id = readStringField(albumObject, "albumId");
            }
            if (id.isEmpty()) {
                id = readStringField(albumObject, "album_id");
            }
            String name = albumObject.has("name") && !albumObject.get("name").isJsonNull()
                    ? albumObject.get("name").getAsString()
                    : "Untitled";
            Album album = new Album(id, name);
            if (albumObject.has("album_shares") && albumObject.get("album_shares").isJsonArray()) {
                boolean hasActiveShare = false;
                for (JsonElement shareElement : albumObject.getAsJsonArray("album_shares")) {
                    if (!shareElement.isJsonObject()) {
                        continue;
                    }
                    JsonElement revokedAt = shareElement.getAsJsonObject().get("revoked_at");
                    if (revokedAt == null || revokedAt.isJsonNull() || revokedAt.getAsString().trim().isEmpty()) {
                        hasActiveShare = true;
                        break;
                    }
                }
                album.setShared(hasActiveShare);
            }
            albums.add(album);
        }

        return albums;
    }

    private String readStringField(JsonObject object, String fieldName) {
        if (!object.has(fieldName) || object.get(fieldName).isJsonNull()) {
            return "";
        }
        JsonElement value = object.get(fieldName);
        if (value.isJsonPrimitive()) {
            return value.getAsString().trim();
        }
        if (value.isJsonObject() && value.getAsJsonObject().has("$oid")) {
            return value.getAsJsonObject().get("$oid").getAsString().trim();
        }
        return "";
    }

    private void loadPhotosForAlbum(String albumId) {
        String token = sessionManager.getToken();
        if (token == null) {
            Toast.error(parent, "User not logged in");
            return;
        }

        new Thread(() -> {
                try {
                    String endpoint = "/photos?albumId=" + Uri.encode(albumId)
                            ;
                    JsonElement response = API.GET(endpoint, token);
                    List<HomeGalleryAdapter.GalleryItem> photos = extractPhotoSources(response);
                    Collections.sort(photos, Comparator.comparingLong(HomeGalleryAdapter.GalleryItem::getCreatedAt).reversed());

                    if (parent != null) {
                        parent.runOnUiThread(() -> {
                            photoAdapter.setPhotos(photos);
                            setAlbumPhotosLoading(false);
                        });
                    }
                } catch (Exception e) {
                    if (parent != null) {
                        parent.runOnUiThread(() -> {
                            setAlbumPhotosLoading(false);
                            Toast.error(parent, "Failed to load album photos");
                        });
                    }
                }
            }).start();
    }

    private List<HomeGalleryAdapter.GalleryItem> extractPhotoSources(JsonElement response) {
        List<HomeGalleryAdapter.GalleryItem> sources = new ArrayList<>();
        JsonArray photosArray = toPhotoArray(response);
        if (photosArray == null) {
            return sources;
        }

        for (int i = 0; i < photosArray.size(); i++) {
            JsonElement element = photosArray.get(i);
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject photoObj = element.getAsJsonObject();
            String photoId = null;
            if (photoObj.has("_id") && !photoObj.get("_id").isJsonNull()) {
                photoId = photoObj.get("_id").getAsString();
            }

            if (!photoObj.has("imageId") || photoObj.get("imageId").isJsonNull()) {
                continue;
            }

            String imageId = photoObj.get("imageId").getAsString();
            if (imageId.trim().isEmpty()) {
                continue;
            }

            long createdAt = parseTimestamp(photoObj);
            Double latitude = null;
            Double longitude = null;
            if (photoObj.has("location") && photoObj.get("location").isJsonObject()) {
                JsonObject location = photoObj.getAsJsonObject("location");
                if (location.has("latitude") && !location.get("latitude").isJsonNull()) {
                    latitude = location.get("latitude").getAsDouble();
                }
                if (location.has("longitude") && !location.get("longitude").isJsonNull()) {
                    longitude = location.get("longitude").getAsDouble();
                }
            }

            sources.add(HomeGalleryAdapter.GalleryItem.remote(photoId, imageId, selectedAlbumId, createdAt, latitude, longitude));
        }

        return sources;
    }

    private JsonArray toPhotoArray(JsonElement response) {
        if (response == null) {
            return null;
        }
        if (response.isJsonArray()) {
            return response.getAsJsonArray();
        }
        if (!response.isJsonObject()) {
            return null;
        }

        JsonObject object = response.getAsJsonObject();
        if (object.has("photos") && object.get("photos").isJsonArray()) {
            return object.getAsJsonArray("photos");
        }
        if (object.has("data") && object.get("data").isJsonObject()) {
            JsonObject data = object.getAsJsonObject("data");
            if (data.has("photos") && data.get("photos").isJsonArray()) {
                return data.getAsJsonArray("photos");
            }
        }
        return null;
    }

    private long parseTimestamp(JsonObject photoObj) {
        if (!photoObj.has("timestamp") || photoObj.get("timestamp").isJsonNull()) {
            return 0L;
        }

        try {
            String timestamp = photoObj.get("timestamp").getAsString();
            Date parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).parse(timestamp);
            if (parsedDate != null) {
                return parsedDate.getTime();
            }
        } catch (Exception ignore) {
            // Keep order stable if timestamp parsing fails.
        }
        return 0L;
    }
}