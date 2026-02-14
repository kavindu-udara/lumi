package com.example.lumi.lib;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;

import com.example.lumi.R;

public class Toast {

    public enum ToastType {
        SUCCESS,
        WARNING,
        ERROR
    }

    private static final int DURATION_SHORT = 2000;
    private static final int DURATION_LONG = 4000;
    private static final int ANIMATION_DURATION = 300;

    public Toast() {
        // constructor
    }

    public static void success(String message) {
        showToast(null, message, ToastType.SUCCESS, DURATION_SHORT);
    }

    public static void warning(String message) {
        showToast(null, message, ToastType.WARNING, DURATION_SHORT);
    }

    public static void error(String message) {
        showToast(null, message, ToastType.ERROR, DURATION_LONG);
    }

    public static void success(Activity activity, String message) {
        showToast(activity, message, ToastType.SUCCESS, DURATION_SHORT);
    }

    public static void warning(Activity activity, String message) {
        showToast(activity, message, ToastType.WARNING, DURATION_SHORT);
    }

    public static void error(Activity activity, String message) {
        showToast(activity, message, ToastType.ERROR, DURATION_LONG);
    }

    private static void showToast(Activity activity, String message, ToastType type, int duration) {
        if (activity == null) {
            // Fallback to system toast if activity is not provided
            // Note: This requires activity context, so if null, silently skip
            return;
        }

        // Get the root view of the activity
        ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView();

        // Inflate the toast layout
        View toastView = LayoutInflater.from(activity).inflate(R.layout.toast_layout, rootView, false);

        // Get views
        LinearLayout toastContainer = toastView.findViewById(R.id.toast_container);
        ImageView toastIcon = toastView.findViewById(R.id.toast_icon);
        TextView toastMessage = toastView.findViewById(R.id.toast_message);

        // Set message
        toastMessage.setText(message);

        // Set type-specific styling
        int backgroundColor;
        int iconResource;

        switch (type) {
            case SUCCESS:
                backgroundColor = activity.getColor(R.color.toast_success);
                iconResource = R.drawable.ic_check_circle;
                break;
            case WARNING:
                backgroundColor = activity.getColor(R.color.toast_warning);
                iconResource = R.drawable.ic_warning_circle;
                break;
            case ERROR:
                backgroundColor = activity.getColor(R.color.toast_error);
                iconResource = R.drawable.ic_error_circle;
                break;
            default:
                backgroundColor = activity.getColor(R.color.toast_success);
                iconResource = R.drawable.ic_check_circle;
        }

        toastContainer.setBackgroundColor(backgroundColor);
        toastIcon.setImageResource(iconResource);

        // Add toast to root view
        rootView.addView(toastView);

        // Animate in
        animateIn(toastView);

        // Schedule removal
        toastView.postDelayed(() -> animateOut(toastView, rootView), duration);
    }

    private static void animateIn(View view) {
        // Slide in from top
        ObjectAnimator translateY = ObjectAnimator.ofFloat(view, "translationY", -500f, 0f);
        translateY.setDuration(ANIMATION_DURATION);

        // Fade in
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        alpha.setDuration(ANIMATION_DURATION);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(translateY, alpha);
        animatorSet.start();
    }

    private static void animateOut(View view, ViewGroup rootView) {
        // Slide out to top
        ObjectAnimator translateY = ObjectAnimator.ofFloat(view, "translationY", 0f, -500f);
        translateY.setDuration(ANIMATION_DURATION);

        // Fade out
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f);
        alpha.setDuration(ANIMATION_DURATION);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(translateY, alpha);
        animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                rootView.removeView(view);
            }
        });
        animatorSet.start();
    }
}
