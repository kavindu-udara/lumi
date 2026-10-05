package com.example.lumi.fragments;

import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.lumi.R;
import com.example.lumi.activities.PhotoViewerActivity;
import com.example.lumi.adapters.HomeGalleryAdapter;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapFragment extends Fragment {

    private static final GeoPoint SRI_LANKA_CENTER = new GeoPoint(7.8731, 80.7718);
    private static final double SRI_LANKA_ZOOM = 7.0;

    private AppCompatActivity parent;
    private MapView mapView;
    private ProgressBar loadingIndicator;
    private final List<PhotoLocation> photoLocations = new ArrayList<>();
    private final List<Marker> currentMarkers = new ArrayList<>();

    public MapFragment() {
        // Required empty public constructor for Fragment recreation.
    }

    public MapFragment(AppCompatActivity parent) {
        this.parent = parent;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            parent = (AppCompatActivity) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);
        Configuration.getInstance().setUserAgentValue(
                "Lumi/1.0 (Android; " + requireContext().getPackageName() + ")"
        );
        FrameLayout mapContainer = view.findViewById(R.id.map_container);
        mapView = new MapView(requireContext());
        mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setTilesScaledToDpi(true);
        mapView.setMultiTouchControls(true);
        mapView.getController().setCenter(SRI_LANKA_CENTER);
        mapView.getController().setZoom(SRI_LANKA_ZOOM);

        loadPhotosAndPlaceMarkers();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            loadPhotosAndPlaceMarkers();
        }
    }

    private void loadPhotosAndPlaceMarkers() {
        loadingIndicator.setVisibility(View.VISIBLE);
        SessionManager sessionManager = new SessionManager(requireContext());
        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            loadingIndicator.setVisibility(View.GONE);
            return;
        }

        new Thread(() -> {
            try {
                JsonElement response = API.GET("/photos?albumId=all", token);
                List<PhotoLocation> locations = parsePhotoLocations(response);
                photoLocations.clear();
                photoLocations.addAll(locations);

                if (!isAdded()) {
                    return;
                }

                parent.runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    placeMarkersOnMap();
                });
            } catch (Exception e) {
                Log.e("MapFragment", "Failed to load map photos", e);
                if (isAdded()) {
                    parent.runOnUiThread(() -> loadingIndicator.setVisibility(View.GONE));
                }
            }
        }).start();
    }

    private List<PhotoLocation> parsePhotoLocations(JsonElement response) {
        List<PhotoLocation> locations = new ArrayList<>();
        JsonArray arr = toPhotoArray(response);
        if (arr == null) {
            return locations;
        }

        for (int i = 0; i < arr.size(); i++) {
            JsonElement element = arr.get(i);
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("location") || !obj.get("location").isJsonObject()) {
                continue;
            }
            if (!obj.has("imageId") || obj.get("imageId").isJsonNull()) {
                continue;
            }

            JsonObject locObj = obj.getAsJsonObject("location");
            if (!hasNumber(locObj, "latitude") || !hasNumber(locObj, "longitude")) {
                continue;
            }

            double latitude = locObj.get("latitude").getAsDouble();
            double longitude = locObj.get("longitude").getAsDouble();
            if (latitude < -90d || latitude > 90d || longitude < -180d || longitude > 180d) {
                continue;
            }
            String imageId = obj.get("imageId").getAsString();

            String photoId = obj.has("_id") && !obj.get("_id").isJsonNull()
                    ? obj.get("_id").getAsString()
                    : null;
            long createdAt = parseTimestamp(obj);
            PhotoLocation location = new PhotoLocation(latitude, longitude, imageId, photoId, createdAt);
            locations.add(location);
        }

        return locations;
    }

    private boolean hasNumber(JsonObject object, String fieldName) {
        if (!object.has(fieldName) || object.get(fieldName).isJsonNull()
                || !object.get(fieldName).isJsonPrimitive()) {
            return false;
        }
        try {
            double value = object.get(fieldName).getAsDouble();
            return !Double.isNaN(value) && !Double.isInfinite(value);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void placeMarkersOnMap() {
        if (mapView == null || photoLocations.isEmpty()) {
            return;
        }

        // Clear existing markers
        for (Marker marker : currentMarkers) {
            mapView.getOverlays().remove(marker);
        }
        currentMarkers.clear();

        // Add markers
        for (PhotoLocation loc : photoLocations) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(loc.latitude, loc.longitude));
            marker.setTitle(loc.imageId);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setRelatedObject(loc);
            currentMarkers.add(marker);
            mapView.getOverlays().add(marker);

            // Load preview image and set custom icon
            loadPreviewForMarker(marker, loc.imageId);

            marker.setOnMarkerClickListener((clickedMarker, mapView) -> {
                Object related = clickedMarker.getRelatedObject();
                if (related instanceof PhotoLocation) {
                    openPhotoViewer((PhotoLocation) related);
                    return true;
                }
                return false;
            });
        }
        mapView.invalidate();
    }

    private void openPhotoViewer(PhotoLocation location) {
        if (location == null || location.imageId == null || location.imageId.trim().isEmpty()) {
            return;
        }

        ArrayList<HomeGalleryAdapter.GalleryItem> items = new ArrayList<>();
        items.add(HomeGalleryAdapter.GalleryItem.remote(
                location.photoId,
                location.imageId,
                location.createdAt,
                location.latitude,
                location.longitude
        ));

        Intent intent = new Intent(requireContext(), PhotoViewerActivity.class);
        intent.putExtra(PhotoViewerActivity.EXTRA_START_INDEX, 0);
        intent.putExtra(PhotoViewerActivity.EXTRA_ITEMS, items);
        startActivity(intent);
    }

    private void loadPreviewForMarker(Marker marker, String imageId) {
        if (marker == null) {
            return;
        }

        SessionManager sessionManager = new SessionManager(requireContext());
        GlideUrl previewUrl = new GlideUrl(
            new API().getPreviewUrl(imageId),
            new LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer " + sessionManager.getToken())
                .build()
        );

        Glide.with(requireContext())
                .asBitmap()
                .load(previewUrl)
                .override(100, 100)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(resource, 64, 64, false);
                        marker.setIcon(new BitmapDrawable(requireContext().getResources(), scaledBitmap));
                        if (mapView != null) {
                            mapView.invalidate();
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        marker.setIcon(new BitmapDrawable(
                                requireContext().getResources(),
                                BitmapFactory.decodeResource(requireContext().getResources(), org.osmdroid.library.R.drawable.marker_default)
                        ));
                        if (mapView != null) {
                            mapView.invalidate();
                        }
                    }
                });
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
        } catch (Exception ignored) {
            // Keep stable behavior if timestamp parsing fails.
        }
        return 0L;
    }

    private static class PhotoLocation {
        final double latitude;
        final double longitude;
        final String imageId;
        final String photoId;
        final long createdAt;

        PhotoLocation(double latitude, double longitude, String imageId, String photoId, long createdAt) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.imageId = imageId;
            this.photoId = photoId;
            this.createdAt = createdAt;
        }
    }
}
