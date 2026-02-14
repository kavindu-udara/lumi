# Modern Toast Notification System

A beautiful, modern Android toast notification system with smooth animations and three distinct toast types: Success, Warning, and Error.

## Features

✨ **Modern Design**
- Rounded corners with elegant styling
- Smooth slide-in/out animations
- Type-specific color schemes
- Semi-transparent background with white border

🎨 **Three Toast Types**
- **Success** (Green #4CAF50) - For successful operations
- **Warning** (Amber #FFC107) - For warning messages
- **Error** (Red #F44336) - For error messages

⚡ **Easy to Use**
- Simple static methods
- Optional Activity context (falls back to system toast)
- Customizable duration
- Support for dark mode

🌓 **Dark Mode Support**
- Automatic color adjustment for night mode
- Separate color definitions for better visibility

## Usage

### Basic Usage

```java
// Success toast
Toast.success(activity, "Operation successful!");

// Warning toast
Toast.warning(activity, "Please check this field");

// Error toast
Toast.error(activity, "Something went wrong");
```

### Without Activity Context (Falls back to System Toast)

```java
// Quick success message
Toast.success("Operation complete!");

// Quick warning
Toast.warning("Please be careful");

// Quick error
Toast.error("An error occurred");
```

### Using ToastManager (Alternative API)

```java
// Show success
ToastManager.showSuccess(activity, "User created successfully");

// Show warning with delay
ToastManager.showWarningDelayed(activity, "Session expires soon", 5000);

// Show error
ToastManager.showError(activity, "Failed to save data");
```

## Implementation Details

### Files Created

1. **Toast.java** - Main toast implementation with animations
2. **ToastManager.java** - Alternative convenient wrapper API
3. **toast_layout.xml** - UI layout for the toast
4. **toast_background.xml** - Rounded corner background drawable
5. **ic_check_circle.xml** - Success icon
6. **ic_warning_circle.xml** - Warning icon
7. **ic_error_circle.xml** - Error icon
8. **colors.xml** - Toast color definitions (light mode)
9. **values-night/colors.xml** - Toast color definitions (dark mode)

### Animation Details

- **Slide-in**: Slides down from top with fade in effect (300ms)
- **Slide-out**: Slides up to top with fade out effect (300ms)
- **Duration**: 2000ms for success/warning, 4000ms for error

### Color Scheme

#### Light Mode
- Success: `#FF4CAF50` (Material Green 500)
- Warning: `#FFFFC107` (Material Amber 500)
- Error: `#FFF44336` (Material Red 500)

#### Dark Mode
- Success: `#FF66BB6A` (Material Green 400)
- Warning: `#FFFFCA28` (Material Amber 400)
- Error: `#FFEF5350` (Material Red 400)

## Customization

### Change Toast Duration

You can modify the constants in Toast.java:

```java
private static final int DURATION_SHORT = 2000;  // Default for success/warning
private static final int DURATION_LONG = 4000;   // Default for error
private static final int ANIMATION_DURATION = 300;  // Animation duration
```

### Change Animation Distance

Modify the translation values in `animateIn()` and `animateOut()` methods:

```java
ObjectAnimator translateY = ObjectAnimator.ofFloat(view, "translationY", -500f, 0f);
```

### Change Toast Colors

Edit `values/colors.xml` and `values-night/colors.xml`:

```xml
<color name="toast_success">#FF4CAF50</color>
<color name="toast_warning">#FFFFC107</color>
<color name="toast_error">#FFF44336</color>
```

## Example Activity Integration

```java
public class MyActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);
        
        findViewById(R.id.btn_success).setOnClickListener(v -> 
            Toast.success(this, "Success! Your action completed.")
        );
        
        findViewById(R.id.btn_warning).setOnClickListener(v -> 
            Toast.warning(this, "Warning! Check your input.")
        );
        
        findViewById(R.id.btn_error).setOnClickListener(v -> 
            Toast.error(this, "Error! Something went wrong.")
        );
    }
}
```

## Technical Details

- **API Level**: Supports API 24+
- **Animation Framework**: Uses Android's ObjectAnimator for smooth animations
- **Threading**: All operations are UI thread safe
- **Memory**: Properly removes views from hierarchy after animation

## Browser Compatibility

The system is designed for Android applications and uses Android APIs:
- `android.view.LayoutInflater`
- `android.animation.ObjectAnimator`
- `android.animation.AnimatorSet`
- `android.widget.LinearLayout`, `ImageView`, `TextView`

