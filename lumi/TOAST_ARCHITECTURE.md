# 📊 Toast System Architecture & Flow Diagram

## System Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Activity Code                        │
│  Toast.success(this, "Message")                             │
│  Toast.warning(this, "Message")                             │
│  Toast.error(this, "Message")                               │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│           Toast Class (Main Implementation)                  │
│  • Static method routing                                     │
│  • Toast type detection                                      │
│  • Activity validation                                       │
│  • Fallback system toast handling                           │
└──────────────────────┬──────────────────────────────────────┘
                       │
         ┌─────────────┼─────────────┐
         │             │             │
         ▼             ▼             ▼
    ┌────────┐   ┌────────┐   ┌────────┐
    │Success │   │Warning │   │ Error  │
    │(Green) │   │(Amber) │   │(Red)   │
    └────────┘   └────────┘   └────────┘
         │             │             │
         └─────────────┼─────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              LayoutInflater & View Setup                     │
│  • Inflate toast_layout.xml                                 │
│  • Get references to views                                  │
│  • Set message text                                         │
│  • Set background color                                     │
│  • Set icon drawable                                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│             Add to Root View (Activity Window)              │
│  • Get root ViewGroup from activity window                  │
│  • Add toast view to hierarchy                              │
└──────────────────────┬──────────────────────────────────────┘
                       │
         ┌─────────────┴─────────────┐
         │                           │
         ▼                           ▼
    ┌──────────┐              ┌──────────────┐
    │ Animate  │              │ Schedule     │
    │   In     │              │ Removal      │
    └──────────┘              └──────────────┘
         │                           │
         ▼                           ▼
    ┌──────────────────────┐  ┌──────────────┐
    │ • Slide from top     │  │ Wait for     │
    │ • Fade in 0% to 100% │  │ duration:    │
    │ • Duration: 300ms    │  │ • 2000ms or  │
    │                      │  │ • 4000ms     │
    └──────────────────────┘  └──────────────┘
         │                           │
         │                           ▼
         │                    ┌──────────────┐
         │                    │ Call Animate │
         │                    │    Out       │
         │                    └──────────────┘
         │                           │
         │                           ▼
         │                    ┌──────────────┐
         │                    │ • Slide out  │
         │                    │ • Fade out   │
         │                    │ • Duration   │
         │                    │   300ms      │
         │                    └──────────────┘
         │                           │
         └───────────────┬───────────┘
                         │
                         ▼
             ┌──────────────────────────┐
             │ Remove View from Window  │
             │ (Garbage Collection)     │
             └──────────────────────────┘
```

---

## Method Call Flow

### Standard Usage Flow
```
User Code
   │
   ├─→ Toast.success(activity, message)
   │      └─→ showToast(activity, message, SUCCESS, 2000)
   │           └─→ Inflate layout
   │           └─→ Set colors & icons
   │           └─→ Add to root view
   │           └─→ animateIn()
   │           └─→ postDelayed(2000ms)
   │                └─→ animateOut()
   │                     └─→ removeView()
   │
   ├─→ Toast.warning(activity, message)
   │      └─→ showToast(activity, message, WARNING, 2000)
   │           └─→ [same as above]
   │
   └─→ Toast.error(activity, message)
          └─→ showToast(activity, message, ERROR, 4000)
              └─→ [same as above, 4000ms duration]
```

---

## Component Interaction Diagram

```
┌──────────────────────────────────────────────┐
│          Toast.java (156 lines)              │
├──────────────────────────────────────────────┤
│ • ToastType enum                             │
│ • Constants (DURATION_SHORT, _LONG)          │
│ • success(String) method                     │
│ • success(Activity, String) method           │
│ • warning(String) method                     │
│ • warning(Activity, String) method           │
│ • error(String) method                       │
│ • error(Activity, String) method             │
│ • showToast() private method                 │
│ • animateIn() private method                 │
│ • animateOut() private method                │
└──────────────────────────────────────────────┘

                    ↓
┌──────────────────────────────────────────────┐
│        toast_layout.xml (37 lines)           │
├──────────────────────────────────────────────┤
│ • LinearLayout (horizontal)                  │
│   ├─ ImageView (icon)                        │
│   └─ TextView (message)                      │
└──────────────────────────────────────────────┘

         ↙              ↓              ↘

┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  toast_background│  │   colors.xml     │  │   ic_check.xml   │
│     .xml         │  │                  │  │  ic_warning.xml  │
├──────────────────┤  ├──────────────────┤  │  ic_error.xml    │
│ • Rounded 8dp    │  │ • toast_success  │  ├──────────────────┤
│ • White border   │  │ • toast_warning  │  │ • Vector shapes  │
│ • Shape def      │  │ • toast_error    │  │ • White tint     │
│ • Corners        │  │ • Light & dark   │  │ • 24dp size      │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

---

## Animation Timeline

### Success/Warning Toast Timeline
```
Timeline (milliseconds)
0ms     ┌─ Start
        │ Y: -500px, Alpha: 0%
        │
        ├─ Slide In + Fade In
100ms   │ Y: -333px, Alpha: 33%
200ms   │ Y: -167px, Alpha: 67%
        │
300ms   ├─ Animation Complete
        │ Y: 0px, Alpha: 100%
        │
        │ ▓▓▓ DISPLAY TOAST ▓▓▓ (1700ms)
        │
2000ms  ├─ Schedule Removal
        │
        ├─ Slide Out + Fade Out
2100ms  │ Y: -167px, Alpha: 67%
2200ms  │ Y: -333px, Alpha: 33%
        │
2300ms  ├─ Animation Complete
        │ Y: -500px, Alpha: 0%
        │ Remove from view
```

### Error Toast Timeline
```
Timeline (milliseconds)
0ms     ┌─ Start
        │ Y: -500px, Alpha: 0%
        │
300ms   ├─ Display begins
        │
        │ ▓▓▓ DISPLAY TOAST ▓▓▓ (3700ms)
        │
4000ms  ├─ Start removal animation
        │
4300ms  └─ Animation complete, view removed
```

---

## Data Flow Diagram

```
┌─────────────────┐
│   User Input    │
│  ("Message")    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Toast Method   │
│  .success()     │
│  .warning()     │
│  .error()       │
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│  ToastType Enum         │
│  • SUCCESS              │
│  • WARNING              │
│  • ERROR                │
└────────┬────────────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│  showToast() Processing                  │
│  • Validate Activity                     │
│  • Inflate Layout                        │
│  • Retrieve View References              │
│  • Set Message Text                      │
│  • Determine Colors                      │
│  • Determine Icons                       │
└────────┬─────────────────────────────────┘
         │
    ┌────┴─────┬────────┬────────┐
    ▼          ▼        ▼        ▼
 ┌─────┐  ┌────────┐ ┌──────┐ ┌─────┐
 │ BG  │  │ Text   │ │ Icon │ │ Add │
 │Color│  │ Color  │ │ Res  │ │View │
 └─────┘  └────────┘ └──────┘ └─────┘
    │          │        │        │
    └──────────┼────────┼────────┘
               │        │
               ▼        ▼
          ┌─────────────────┐
          │  Root ViewGroup │
          │  (Activity      │
          │   Window)       │
          └────────┬────────┘
                   │
               ┌───┴──────┐
               │          │
               ▼          ▼
          ┌────────┐  ┌──────────┐
          │Animate │  │ Schedule │
          │   In   │  │ Removal  │
          └────────┘  └──────────┘
               │           │
               ▼           ▼
          ┌──────────────────────┐
          │   Toast Displayed    │
          │   User Sees Toast    │
          └──────────────────────┘
```

---

## State Machine Diagram

```
                    ┌─────────────────┐
                    │   INITIALIZED   │
                    │  (View Created)  │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  ANIMATING_IN   │
                    │  (300ms slide)  │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
        ┌──────────→│   DISPLAYING    │←────────┐
        │           │ (2s or 4s wait) │         │
        │           └────────┬────────┘         │
        │                    │                  │
        │                    ▼                  │
        │           ┌─────────────────┐         │
        │           │ ANIMATING_OUT   │         │
        │           │  (300ms slide)  │         │
        │           └────────┬────────┘         │
        │                    │                  │
        │                    ▼                  │
        │           ┌─────────────────┐         │
        │           │   REMOVED       │         │
        │           │  (View Cleaned) │         │
        │           └─────────────────┘         │
        │                                       │
        └───────────────────────────────────────┘
                  Error Handling Flow
```

---

## Resource Reference Diagram

```
Toast Class
    │
    ├─→ R.layout.toast_layout
    │      │
    │      ├─→ @id/toast_container
    │      ├─→ @id/toast_icon
    │      └─→ @id/toast_message
    │
    ├─→ R.drawable.toast_background
    │      │
    │      └─→ Shape: rectangle
    │           • Corners: 8dp
    │           • Border: 1dp white
    │
    ├─→ R.drawable.ic_check_circle
    ├─→ R.drawable.ic_warning_circle
    ├─→ R.drawable.ic_error_circle
    │
    ├─→ R.color.toast_success
    ├─→ R.color.toast_warning
    └─→ R.color.toast_error
```

---

## Class Structure

```
public class Toast {
    
    public enum ToastType {
        SUCCESS,
        WARNING,
        ERROR
    }
    
    // Constants
    private static final int DURATION_SHORT = 2000;
    private static final int DURATION_LONG = 4000;
    private static final int ANIMATION_DURATION = 300;
    
    // Public API (6 methods)
    public static void success(String)
    public static void warning(String)
    public static void error(String)
    public static void success(Activity, String)
    public static void warning(Activity, String)
    public static void error(Activity, String)
    
    // Private Implementation (3 methods)
    private static void showToast(...)
    private static void animateIn(View)
    private static void animateOut(View, ViewGroup)
}
```

---

## Dependency Graph

```
Toast.java
  ├─ Imports
  │  ├─ android.app.Activity
  │  ├─ android.view.*
  │  ├─ android.widget.*
  │  ├─ android.animation.*
  │  └─ com.example.lumi.R
  │
  ├─ Uses Resources
  │  ├─ R.layout.toast_layout
  │  ├─ R.drawable.*
  │  ├─ R.color.*
  │  └─ R.id.*
  │
  ├─ Depends On
  │  └─ Android Framework APIs (No external libraries)
  │
  └─ Provides
     ├─ Toast class
     └─ ToastType enum
```

---

## Integration Points

```
Your Activity
    │
    ├─→ Import Toast
    │   import com.example.lumi.lib.Toast;
    │
    ├─→ Use in onCreate()
    │   Toast.success(this, "Activity started");
    │
    ├─→ Use in onClickListener()
    │   button.setOnClickListener(v -> {
    │       Toast.success(this, "Clicked!");
    │   });
    │
    ├─→ Use in API callback
    │   API.fetch(response -> {
    │       if (response.ok()) {
    │           Toast.success(this, "Loaded");
    │       }
    │   });
    │
    └─→ Use in error handling
        try {
            operation();
            Toast.success(this, "Done");
        } catch (Exception e) {
            Toast.error(this, e.getMessage());
        }
```

---

## Summary

The toast system works as a **layered architecture**:

1. **API Layer** - Static methods for easy calling
2. **Processing Layer** - Toast type detection and routing
3. **UI Layer** - Layout inflation and view setup
4. **Animation Layer** - Smooth slide-in/out effects
5. **Resource Layer** - Colors, icons, and layout definitions

All layers work together to create a **smooth, modern notification experience**!

