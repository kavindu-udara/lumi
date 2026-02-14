# 🎨 Toast System - Visual Reference Card

## One-Page Quick Reference

### Basic Usage (Copy & Paste)
```java
// Success - Green, 2 seconds
Toast.success(this, "Operation successful!");

// Warning - Amber, 2 seconds
Toast.warning(this, "Please check this field");

// Error - Red, 4 seconds
Toast.error(this, "Something went wrong");
```

---

## All Available Methods

### Toast Class (6 methods)
```java
Toast.success(String)                    // Without activity
Toast.warning(String)                    // Without activity
Toast.error(String)                      // Without activity
Toast.success(Activity, String)          // With activity
Toast.warning(Activity, String)          // With activity
Toast.error(Activity, String)            // With activity
```

### ToastManager Class (6 methods)
```java
ToastManager.showSuccess(Activity, String)      // 2 seconds
ToastManager.showWarning(Activity, String)      // 2 seconds
ToastManager.showError(Activity, String)        // 4 seconds
ToastManager.showSuccessDelayed(Activity, String, long)  // Delayed
ToastManager.showWarningDelayed(Activity, String, long)  // Delayed
ToastManager.showErrorDelayed(Activity, String, long)    // Delayed
```

---

## Colors & Timing

### Color Scheme
| Mode | Success | Warning | Error |
|------|---------|---------|-------|
| Light | #4CAF50 | #FFC107 | #F44336 |
| Dark | #66BB6A | #FFCA28 | #EF5350 |

### Timing
- Success: 2000ms
- Warning: 2000ms
- Error: 4000ms
- Animation: 300ms (in/out)

---

## Visual Appearance

```
┌─────────────────────────────────────┐
│ ✓  Success Message                  │  Green background
└─────────────────────────────────────┘  White text
                                         24dp icon
                                         8dp radius

┌─────────────────────────────────────┐
│ ⚠  Warning Message                  │  Amber background
└─────────────────────────────────────┘  White text
                                         24dp icon
                                         8dp radius

┌─────────────────────────────────────┐
│ ✕  Error Message                    │  Red background
└─────────────────────────────────────┘  White text
                                         24dp icon
                                         8dp radius
```

---

## Common Patterns

### Pattern 1: Button Click
```java
button.setOnClickListener(v -> 
    Toast.success(MainActivity.this, "Button clicked!")
);
```

### Pattern 2: Form Validation
```java
if (email.isEmpty()) {
    Toast.warning(this, "Email required");
    return false;
}
Toast.success(this, "Valid!");
return true;
```

### Pattern 3: API Response
```java
api.fetch(response -> {
    if (response.ok()) {
        Toast.success(this, "Loaded");
    } else {
        Toast.error(this, "Failed");
    }
});
```

### Pattern 4: Try-Catch
```java
try {
    doSomething();
    Toast.success(this, "Done");
} catch (Exception e) {
    Toast.error(this, e.getMessage());
}
```

---

## File Locations

| File | Type | Location |
|------|------|----------|
| Toast.java | Code | `app/src/main/java/com/example/lumi/lib/` |
| ToastManager.java | Code | `app/src/main/java/com/example/lumi/lib/` |
| toast_layout.xml | Layout | `app/src/main/res/layout/` |
| Icons | Drawable | `app/src/main/res/drawable/` |
| Colors | Resource | `app/src/main/res/values/colors.xml` |
| Night Colors | Resource | `app/src/main/res/values-night/colors.xml` |

---

## Customization Cheat Sheet

### Change Duration
In `Toast.java`, modify constants:
```java
private static final int DURATION_SHORT = 2000;  // Success/Warning
private static final int DURATION_LONG = 4000;   // Error
```

### Change Color
In `values/colors.xml`:
```xml
<color name="toast_success">#FF4CAF50</color>
<color name="toast_warning">#FFFFC107</color>
<color name="toast_error">#FFF44336</color>
```

### Change Animation Speed
In `Toast.java`, modify:
```java
translateY.setDuration(300);  // Change this value (in ms)
```

### Change Slide Distance
In `Toast.java`, modify:
```java
ObjectAnimator.ofFloat(view, "translationY", -500f, 0f);  // Change -500f
```

---

## Troubleshooting Quick Fix

| Problem | Solution |
|---------|----------|
| Toast not showing | Call from main thread, check activity is not null |
| Wrong colors | Check light/dark mode colors match, verify color names |
| Animation not smooth | Verify duration is 300ms (default), check device isn't overloaded |
| Icons not showing | Verify drawable files exist in `/drawable/` folder |
| View not added | Ensure `R.layout.toast_layout` exists and is referenced |

---

## Import Statement
```java
import com.example.lumi.lib.Toast;
// or
import com.example.lumi.lib.ToastManager;
```

---

## Pro Tips

1. Use `this` in Activity, `activity` in Fragment
2. Success/Warning = 2s, Error = 4s (default)
3. Light colors for light mode, darker for dark mode
4. Icons are automatically 24dp sized
5. Text max 2 lines, automatically ellipsizes
6. Animations happen automatically
7. Views removed automatically after display
8. No configuration needed, just use it!

---

## Animation Timeline (Visual)

```
Success/Warning Toast:
0ms      ↓ Y:-500px     Start (off-screen top)
100ms    ↓ Y:-333px     Sliding down
200ms    ↓ Y:-167px     Sliding down
300ms    ↓ Y:0px        Fully visible ← DISPLAY STARTS
2000ms   ↓              DISPLAY ENDS ← 1700ms display time
2100ms   ↑ Y:-167px     Sliding up
2200ms   ↑ Y:-333px     Sliding up
2300ms   ↑ Y:-500px     Removed from view

Error Toast (same, but 4000ms total)
```

---

## Complete Method Signature

```java
public static void success(Activity activity, String message)
public static void warning(Activity activity, String message)
public static void error(Activity activity, String message)
public static void success(String message)
public static void warning(String message)
public static void error(String message)
```

All parameters:
- `activity`: The Activity context (can be null for fallback)
- `message`: String message to display

---

## Example One-Liners

```java
Toast.success(this, "✓ Saved!");
Toast.warning(this, "⚠ Check email");
Toast.error(this, "✕ Error occurred");
Toast.success("Quick toast");
ToastManager.showSuccess(this, "Done!");
ToastManager.showWarningDelayed(this, "Expires soon", 5000);
```

---

## Design Specs

| Aspect | Value |
|--------|-------|
| Margin | 16dp |
| Padding | 16dp horizontal, 12dp vertical |
| Icon Size | 24dp × 24dp |
| Icon Spacing | 12dp |
| Text Size | 14sp |
| Corner Radius | 8dp |
| Border Width | 1dp |
| Text Color | White |
| Max Lines | 2 |

---

## Icon Types

| Type | Icon | File |
|------|------|------|
| Success | ✓ (checkmark) | `ic_check_circle.xml` |
| Warning | ⚠ (triangle) | `ic_warning_circle.xml` |
| Error | ✕ (exclamation) | `ic_error_circle.xml` |

---

## Dependencies
- **External:** None!
- **Android APIs Used:** Standard UI, Animation, View inflation
- **Minimum API:** 24

---

## Status Check

- [x] Ready to use
- [x] No configuration needed
- [x] All resources included
- [x] Full documentation provided
- [x] Examples available
- [x] Works light & dark mode
- [x] Production quality

---

## Get Started in 3 Steps

1. Find `Toast.java` in your project
2. Use: `Toast.success(this, "Your message");`
3. Done! ✅

---

**Print this page for your desk!** 📄

