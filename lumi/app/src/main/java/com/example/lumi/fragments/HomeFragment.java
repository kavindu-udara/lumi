package com.example.lumi.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.lumi.R;
import com.example.lumi.activities.PhotoViewerActivity;
import com.example.lumi.activities.SignIn;
import com.example.lumi.adapters.HomeGalleryAdapter;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.Toast;
import com.example.lumi.lib.UploadQueueStore;
import com.example.lumi.models.QueuedUploadItem;
import com.example.lumi.workers.UploadQueueWorker;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class HomeFragment extends Fragment {

    private static final int SPAN_COUNT = 5;

    private AppCompatActivity parent;

    private RecyclerView imageGrid;
    private ProgressBar loadingIndicator;
    private HomeGalleryAdapter adapter;
    private SessionManager sessionManager;
    private UploadQueueStore uploadQueueStore;
    private FusedLocationProviderClient locationClient;
    private final List<HomeGalleryAdapter.GalleryItem> remotePhotoItems = new ArrayList<>();

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<Uri> captureVideoLauncher;
    private ActivityResultLauncher<Intent> viewerLauncher;
    private File pendingCaptureFile;
    private File pendingVideoFile;

    private FirebaseAuth mAuth;

    public HomeFragment() {
        // Required empty public constructor for Fragment recreation.
    }

    public HomeFragment(AppCompatActivity parent) {
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
        registerLaunchers();
//        init firebase auth
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);
        imageGrid = view.findViewById(R.id.imageGrid);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);
        FloatingActionButton cameraFab = view.findViewById(R.id.cameraFab);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), SPAN_COUNT);
        imageGrid.setLayoutManager(gridLayoutManager);

        sessionManager = new SessionManager(HomeFragment.this.getContext());
        uploadQueueStore = new UploadQueueStore(requireContext());
        locationClient = LocationServices.getFusedLocationProviderClient(parent);

//        check if user is logged in, if not navigate to sign in activity
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(getContext(), SignIn.class));
            if (getActivity() != null) {
                getActivity().finish();
            }
            return view;
        }

        adapter = new HomeGalleryAdapter(parent, new ArrayList<>());
        adapter.setOnItemClickListener((position, item) -> openPhotoViewer(position));
        imageGrid.setAdapter(adapter);
        cameraFab.setOnClickListener(v -> onCameraFabClicked());

        renderMergedGallery();
        enqueueUploadWorker();
        loadGalleryFromApi();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (sessionManager != null && sessionManager.isLoggedIn()) {
            // User is logged in - do nothing
            // Refresh gallery when returning to the screen, in case uploads completed while away.
            renderMergedGallery();

        } else {
            // User is not logged in - redirect to SignIn
            startActivity(new Intent(getContext(), SignIn.class));
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh local queue section when returning to the screen.
        renderMergedGallery();
    }

    private void registerLaunchers() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean cameraGranted = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));
                    boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                    boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (cameraGranted && (fineGranted || coarseGranted)) {
                        showCaptureModeDialog();
                    } else {
                        android.widget.Toast.makeText(parent, "Camera and location permissions are required", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && pendingCaptureFile != null) {
                        onPhotoCaptured(pendingCaptureFile);
                    } else if (pendingCaptureFile != null && pendingCaptureFile.exists()) {
                        if (!pendingCaptureFile.delete()) {
                            Log.w("Camera", "Failed to delete canceled capture file: " + pendingCaptureFile.getAbsolutePath());
                        }
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
                        if (!pendingVideoFile.delete()) {
                            Log.w("Camera", "Failed to delete canceled video file: " + pendingVideoFile.getAbsolutePath());
                        }
                    }
                    pendingVideoFile = null;
                }
        );

        viewerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                        renderMergedGallery();
                        loadGalleryFromApi();
                    }
                }
        );
    }

    private void onCameraFabClicked() {
        if (hasCapturePermissions()) {
            showCaptureModeDialog();
            return;
        }

        permissionLauncher.launch(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void showCaptureModeDialog() {
        if (!isAdded()) {
            return;
        }

        String[] options = new String[]{"Take Photo", "Record Video"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Create")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchCamera();
                    } else {
                        launchVideoRecorder();
                    }
                })
                .show();
    }

    private boolean hasCapturePermissions() {
        Context context = requireContext();
        boolean camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return camera && (fine || coarse);
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
        } catch (IOException ex) {
            android.widget.Toast.makeText(parent, "Failed to open camera", android.widget.Toast.LENGTH_SHORT).show();
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
        } catch (IOException ex) {
            android.widget.Toast.makeText(parent, "Failed to open video recorder", android.widget.Toast.LENGTH_SHORT).show();
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

    private void onPhotoCaptured(@NonNull File imageFile) {
        getCurrentLocation(location -> {
            long createdAt = System.currentTimeMillis();
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(createdAt));
            String userId = "anonymous";
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            QueuedUploadItem item = new QueuedUploadItem(
                    UUID.randomUUID().toString(),
                    imageFile.getAbsolutePath(),
                    date,
                    createdAt,
                    location == null ? null : location.latitude,
                    location == null ? null : location.longitude,
                    userId
            );

            uploadQueueStore.enqueue(item);
            renderMergedGallery();
            scrollToTop();
            enqueueUploadWorker();
            android.widget.Toast.makeText(parent, "Photo queued for upload", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    private void onVideoCaptured(@NonNull File videoFile) {
        getCurrentLocation(location -> {
            long createdAt = System.currentTimeMillis();
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(createdAt));
            String userId = "anonymous";
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            QueuedUploadItem item = new QueuedUploadItem(
                    UUID.randomUUID().toString(),
                    videoFile.getAbsolutePath(),
                    date,
                    createdAt,
                    location == null ? null : location.latitude,
                    location == null ? null : location.longitude,
                    userId
            );

            uploadQueueStore.enqueue(item);
            renderMergedGallery();
            scrollToTop();
            enqueueUploadWorker();
            android.widget.Toast.makeText(parent, "Video queued for upload", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation(LocationCallback callback) {
        if (!hasCapturePermissions()) {
            callback.onResult(null);
            return;
        }

        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                callback.onResult(new LocationData(location.getLatitude(), location.getLongitude()));
                return;
            }

            locationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(current -> {
                        if (current != null) {
                            callback.onResult(new LocationData(current.getLatitude(), current.getLongitude()));
                        } else {
                            callback.onResult(null);
                        }
                    })
                    .addOnFailureListener(err -> callback.onResult(null));
        }).addOnFailureListener(err -> callback.onResult(null));
    }

    private void loadGalleryFromApi() {
        loadingIndicator.setVisibility(View.VISIBLE);
        imageGrid.setVisibility(View.GONE);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            loadingIndicator.setVisibility(View.GONE);
            imageGrid.setVisibility(View.VISIBLE);
            Toast.error("User not authenticated");
            return;
        }

        currentUser.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Log.w("HomeFragment", "Failed to get session token", task.getException());
                safeUi(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    imageGrid.setVisibility(View.VISIBLE);
                    renderMergedGallery();
                    Toast.error("Failed to authenticate request");
                });
                return;
            }

            String token = task.getResult().getToken();
            if (token == null || token.trim().isEmpty()) {
                safeUi(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    imageGrid.setVisibility(View.VISIBLE);
                    renderMergedGallery();
                    Toast.error("Invalid session token");
                });
                return;
            }

            new Thread(() -> {
                try {
                    JsonElement response = API.GET( "/photos", token);
                    List<HomeGalleryAdapter.GalleryItem> apiPhotos = extractPhotoSources(response);

                    remotePhotoItems.clear();
                    remotePhotoItems.addAll(apiPhotos);

                    safeUi(() -> {
                        renderMergedGallery();
                        loadingIndicator.setVisibility(View.GONE);
                        imageGrid.setVisibility(View.VISIBLE);
                        scrollToTop();
                    });
                } catch (Exception e) {
                    Log.e("HomeFragment", "Failed to load /photos", e);
                    safeUi(() -> {
                        loadingIndicator.setVisibility(View.GONE);
                        imageGrid.setVisibility(View.VISIBLE);
                        renderMergedGallery();
                        Toast.error("Failed to load library");
                    });
                }
            }).start();
        });
    }

    private void enqueueUploadWorker() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        String firebaseUserId = currentUser.getUid();
        currentUser.getIdToken(false).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Log.w("HomeFragment", "Failed to start upload queue: missing token", task.getException());
                return;
            }

            String token = task.getResult().getToken();
            if (token == null || token.trim().isEmpty() || !isAdded()) {
                return;
            }

            UploadQueueWorker.enqueue(requireContext(), firebaseUserId, token);
        });
    }

    private void renderMergedGallery() {
        List<HomeGalleryAdapter.GalleryItem> merged = mergeRemoteAndQueuedPhotos(remotePhotoItems, uploadQueueStore.getAll());
        adapter.setPhotos(merged);
        imageGrid.setVisibility(View.VISIBLE);
    }

    private void openPhotoViewer(int startIndex) {
        Intent intent = new Intent(requireContext(), PhotoViewerActivity.class);
        intent.putExtra(PhotoViewerActivity.EXTRA_START_INDEX, startIndex);
        intent.putExtra(PhotoViewerActivity.EXTRA_ITEMS, new ArrayList<>(adapter.getItems()));
        viewerLauncher.launch(intent);
    }

    private void safeUi(Runnable r) {
        if (!parent.isFinishing() && !parent.isDestroyed()) {
            parent.runOnUiThread(r);
        }
    }

    private void scrollToTop() {
        if (adapter != null && adapter.getItemCount() > 0) {
            imageGrid.post(() -> imageGrid.scrollToPosition(0));
        }
    }

    private List<HomeGalleryAdapter.GalleryItem> mergeRemoteAndQueuedPhotos(
            List<HomeGalleryAdapter.GalleryItem> apiPhotos,
            List<QueuedUploadItem> queuedItems
    ) {
        List<HomeGalleryAdapter.GalleryItem> merged = new ArrayList<>();
        if (apiPhotos != null) {
            merged.addAll(apiPhotos);
        }

        for (QueuedUploadItem item : queuedItems) {
            if (item == null || item.getFilePath() == null || item.getFilePath().trim().isEmpty()) {
                continue;
            }
            merged.add(HomeGalleryAdapter.GalleryItem.local(
                    item.getId(),
                    item.getFilePath(),
                    item.getCreatedAt(),
                    item.getLatitude(),
                    item.getLongitude()
            ));
        }

        // Newest first so latest API items and newly captured photos are shown first.
        Collections.sort(merged, Comparator.comparingLong(HomeGalleryAdapter.GalleryItem::getCreatedAt).reversed());
        return merged;
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
            sources.add(HomeGalleryAdapter.GalleryItem.remote(photoId, imageId, createdAt, latitude, longitude));
        }

        return sources;
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
            // Keep placeholder order stable even if timestamp parsing fails.
        }
        return 0L;
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

    private interface LocationCallback {
        void onResult(LocationData location);
    }

    private static class LocationData {
        private final double latitude;
        private final double longitude;

        private LocationData(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

}