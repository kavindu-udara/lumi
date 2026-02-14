# 🚀 Toast System - Quick Reference Card

## Copy & Paste Ready Code

### Basic Usage (Most Common)
```java
// In your Activity
Toast.success(this, "Success message");
Toast.warning(this, "Warning message");
Toast.error(this, "Error message");
```

---

## Common Use Cases

### Form Validation
```java
if (name.isEmpty()) {
    Toast.warning(this, "Name is required");
    return;
}
if (name.length() < 2) {
    Toast.error(this, "Name too short");
    return;
}
Toast.success(this, "Profile updated!");
```

### Button Click Handler
```java
btn.setOnClickListener(v -> 
    Toast.success(MainActivity.this, "Button clicked!")
);
```

### API Response
```java
api.getUser(userId, response -> {
    if (response.success()) {
        Toast.success(this, "User loaded");
    } else {
        Toast.error(this, "Failed to load");
    }
});
```

### Try-Catch Error Handling
```java
try {
    performOperation();
    Toast.success(this, "Operation completed");
} catch (Exception e) {
    Toast.error(this, e.getMessage());
}
```

### Delayed Toast
```java
ToastManager.showSuccessDelayed(this, "Saved!", 1000);
ToastManager.showWarningDelayed(this, "Expires soon", 5000);
```

### Without Activity Context
```java
Toast.success("Quick message");  // Falls back to system toast
Toast.warning("Quick message");
Toast.error("Quick message");
```

---

## Method Quick Reference

| Method | Example | Duration |
|--------|---------|----------|
| `Toast.success(Activity, String)` | `Toast.success(this, "Done")` | 2s |
| `Toast.warning(Activity, String)` | `Toast.warning(this, "Check this")` | 2s |
| `Toast.error(Activity, String)` | `Toast.error(this, "Failed")` | 4s |
| `Toast.success(String)` | `Toast.success("Done")` | 2s |
| `Toast.warning(String)` | `Toast.warning("Check")` | 2s |
| `Toast.error(String)` | `Toast.error("Failed")` | 4s |

---

## File Locations

**Java Classes:**
- `/app/src/main/java/com/example/lumi/lib/Toast.java`
- `/app/src/main/java/com/example/lumi/lib/ToastManager.java`

**Layout:**
- `/app/src/main/res/layout/toast_layout.xml`

**Drawables:**
- `/app/src/main/res/drawable/toast_background.xml`
- `/app/src/main/res/drawable/ic_check_circle.xml`
- `/app/src/main/res/drawable/ic_warning_circle.xml`
- `/app/src/main/res/drawable/ic_error_circle.xml`

**Resources:**
- `/app/src/main/res/values/colors.xml`
- `/app/src/main/res/values-night/colors.xml`

---

## Customization Cheat Sheet

### Change Toast Duration (in Toast.java)
```java
private static final int DURATION_SHORT = 2000;  // Success/Warning
private static final int DURATION_LONG = 4000;   // Error
```

### Change Toast Color (in colors.xml)
```xml
<color name="toast_success">#FF4CAF50</color>
<color name="toast_warning">#FFFFC107</color>
<color name="toast_error">#FFF44336</color>
```

### Change Animation Speed (in Toast.java)
```java
translateY.setDuration(300);  // in animateIn() and animateOut()
```

### Change Slide Distance (in Toast.java)
```java
ObjectAnimator.ofFloat(view, "translationY", -500f, 0f);  // -500f is distance
```

---

## Troubleshooting

**Toast not appearing?**
- Make sure you're calling it from the main/UI thread
- Check that R.layout.toast_layout exists
- Verify activity is not null (or use fallback version)

**Wrong colors?**
- Check light/dark mode colors in values/ and values-night/
- Make sure color names match: toast_success, toast_warning, toast_error

**Animation not smooth?**
- Default 300ms duration should be smooth
- Can increase to 500ms if needed
- Decrease to 200ms for faster animation

**Icons not showing?**
- Verify ic_check_circle.xml, ic_warning_circle.xml, ic_error_circle.xml exist
- Check that they're in /drawable/ folder

---

## Pro Tips

1. **Use in MVP/MVVM Pattern**
   ```java
   presenter.login(email, password, success -> {
       if (success) Toast.success(view, "Logged in");
       else Toast.error(view, "Login failed");
   });
   ```

2. **Show Multiple Toasts**
   ```java
   Toast.warning(this, "First warning");
   ToastManager.showWarningDelayed(this, "Second warning", 2500);
   ```

3. **Extract to Strings Resource**
   ```java
   Toast.success(this, getString(R.string.msg_success));
   ```

4. **Use with LiveData**
   ```java
   viewModel.statusLiveData.observe(this, status -> {
       if (status.equals("SUCCESS")) 
           Toast.success(MainActivity.this, "Done!");
   });
   ```

5. **Create Toast Utility**
   ```java
   public class ToastUtil {
       public static void showSuccess(Activity activity, int messageResId) {
           Toast.success(activity, activity.getString(messageResId));
       }
   }
   ```

---

## 🎯 Remember

- Use `this` when calling from Activity
- Use `activity` when calling from Fragment (get from arguments/listener)
- Success = 2 seconds, Error = 4 seconds
- Light mode: Bright colors, Dark mode: Lighter variants
- Animations are automatic - no configuration needed!

---

**Happy Toasting! 🎉**

