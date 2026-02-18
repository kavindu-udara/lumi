package com.example.lumi.fragments;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.lumi.R;
import com.example.lumi.activities.SignIn;
import com.example.lumi.adapters.ImageGridAdapter;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.Toast;
import com.example.lumi.models.Image;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Arrays;

public class HomeFragment extends Fragment {

    AppCompatActivity parent;

    public HomeFragment(AppCompatActivity parent) {
        this.parent = parent;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);
        RecyclerView imageGrid = view.findViewById(R.id.imageGrid);

        SessionManager sessionManager = new SessionManager(HomeFragment.this.getContext());
//        check if user is logged in, if not navigate to sign in activity
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(getContext(), SignIn.class));
            getActivity().finish();
        }

//        send request to get library and display it in a recyclerview
        new Thread(() -> {
            try {
                JsonObject responseObj = API.GET("/lib", sessionManager.getToken());

                if(responseObj.get("success").getAsBoolean()) {
                    Log.i("Library","image count :" + responseObj.get("images").getAsJsonArray().size());

                    Image[] imagesArr = new Gson().fromJson(responseObj.get("images").getAsJsonArray().toString(), Image[].class);

                    safeUi(() -> {
                        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 5);
                        imageGrid.setLayoutManager(gridLayoutManager);
                        imageGrid.setAdapter(new ImageGridAdapter(Arrays.asList(imagesArr), getContext()));
                        imageGrid.setVisibility(View.VISIBLE);
                    });

                    Log.i("Library", "Library Response success: " + responseObj.toString());
                }else {
                    Log.e("Library", "Library Response failed: " + responseObj.toString());
                    if(responseObj.get("message").getAsString().equals("Unauthorized") ) {
                        sessionManager.clearSession();
                        startActivity(new Intent(getContext(), SignIn.class));
                        getActivity().finish();
                    }
                    safeUi(() -> {;
                        // show error message to user
                        Toast.error( "Failed to load library: " + responseObj.get("message").getAsString());
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Inflate the layout for this fragment
        return view;
    }

    private void safeUi(Runnable r) {
        if (!parent.isFinishing() && !parent.isDestroyed()) {
            parent.runOnUiThread(r);
        }
    }

}