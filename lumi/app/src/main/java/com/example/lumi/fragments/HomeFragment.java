package com.example.lumi.fragments;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.lumi.R;
import com.example.lumi.activities.SignIn;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.Toast;
import com.google.gson.JsonObject;

public class HomeFragment extends Fragment {

    AppCompatActivity parent;

    public HomeFragment(AppCompatActivity parent) {
        this.parent = parent;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        SessionManager sessionManager = new SessionManager(HomeFragment.this.getContext());
//        check if user is logged in, if not navigate to sign in activity
        if (!sessionManager.isLoggedIn()) {
            startActivity(new android.content.Intent(getContext(), SignIn.class));
            getActivity().finish();
        }

//        send request to get library and display it in a recyclerview
        new Thread(() -> {
            try {
                JsonObject responseObj = API.GET("/lib", sessionManager.getToken());
                System.out.println(responseObj);

                if(responseObj.get("success").getAsBoolean()) {
                    Log.i("Library", "Library Response success: " + responseObj.toString());
                }else {
                    Log.e("Library", "Library Response failed: " + responseObj.toString());
                    if(responseObj.get("message").getAsString().equals("Unauthorized") ) {
                        sessionManager.clearSession();
                        startActivity(new android.content.Intent(getContext(), SignIn.class));
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
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    private void safeUi(Runnable r) {
        if (!parent.isFinishing() && !parent.isDestroyed()) {
            parent.runOnUiThread(r);
        }
    }

}