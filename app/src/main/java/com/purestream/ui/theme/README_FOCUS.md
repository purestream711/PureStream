# TV Focus Indicator System

This document explains how to use the custom focus indicator system in PureStream Android TV app.

## Overview

The focus indicator system provides consistent scale-based visual feedback for all focusable UI elements when they gain focus via D-pad navigation. Instead of colored borders, focused elements subtly increase in size (1-2dp), creating a modern and professional focus indication.

## Key Features

- **Dynamic Scaling**: Focused elements increase in size by 1-8% with smooth animations
- **Multiple Variants**: Different scale amounts for different UI element types
- **Smooth Animations**: 150ms animation duration for natural transitions
- **Easy Integration**: Simple extension functions that work with existing code
- **Performance Optimized**: Uses Compose's built-in animation system

## Usage

### 1. Basic Focus Indicator

For any focusable element (5% scale increase):

```kotlin
Box(
    modifier = Modifier
        .tvFocusIndicator()
        .focusable()
        .clickable { /* action */ }
) {
    // Content
}
```

### 2. Button Focus Indicator

For buttons (4% scale increase):

```kotlin
Button(
    onClick = { /* action */ },
    modifier = Modifier.tvButtonFocus()
) {
    Text("Click me")
}
```

### 3. Card Focus Indicator

For cards with larger scale increase (8%):

```kotlin
Card(
    modifier = Modifier
        .tvCardFocusIndicator()
        .focusable()
        .clickable { /* action */ }
) {
    // Card content
}
```

### 4. Icon Focus Indicator

For icons and small elements (3% scale increase):

```kotlin
Icon(
    imageVector = Icons.Default.Home,
    contentDescription = "Home",
    modifier = Modifier
        .tvIconFocusIndicator()
        .focusable()
        .clickable { /* action */ }
)
```

### 5. Generic Focus Helper

Quick shorthand for standard elements:

```kotlin
MyComposable(
    modifier = Modifier
        .tvFocus()
        .focusable()
)
```

## Available Modifiers

| Modifier | Use Case | Scale Increase | Best For |
|----------|----------|----------------|----------|
| `tvFocusIndicator()` | Basic focus indicator | 5% (1.05f) | General elements |
| `tvButtonFocus()` | Buttons | 4% (1.04f) | Buttons, interactive text |
| `tvCardFocusIndicator()` | Cards | 8% (1.08f) | Movie/TV cards, large content |
| `tvIconFocusIndicator()` | Icons & small elements | 3% (1.03f) | Icons, sidebar items |
| `tvFocus()` | Generic with default scale | 5% (1.05f) | Quick implementation |

## Customization

All modifiers accept optional parameters:

```kotlin
// Custom scale and animation duration
Modifier.tvFocusIndicator(
    focusScale = 1.1f,           // 10% increase
    animationDuration = 200      // 200ms animation
)
```

## Implementation Notes

- Focus indicators are applied using Compose's `scale()` modifier
- The system automatically handles focus state tracking with `onFocusChanged`
- All animations use `animateFloatAsState` for smooth transitions
- Scale is applied around the element's center point
- Animations can be customized per element if needed

## Migration from Border-based Focus Handling

### Before (Border-based):
```kotlin
var isFocused by remember { mutableStateOf(false) }

Box(
    modifier = Modifier
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) Color(0xFFEF4444) else Color.Transparent,
            shape = RoundedCornerShape(8.dp)
        )
        .focusable()
        .onFocusChanged { isFocused = it.isFocused }
        .clickable { /* action */ }
)
```

### After (Scale-based):
```kotlin
Box(
    modifier = Modifier
        .tvFocusIndicator()
        .focusable()
        .clickable { /* action */ }
)
```

## Best Practices

1. **Apply focus indicators to all interactive elements** (buttons, cards, images, etc.)
2. **Use the appropriate modifier variant** for your UI element type:
   - Small elements (icons): `tvIconFocusIndicator()`
   - Buttons: `tvButtonFocus()`
   - Cards/content: `tvCardFocusIndicator()`
   - General: `tvFocusIndicator()`
3. **Apply focus modifiers BEFORE focusable()** in the modifier chain
4. **Keep scale amounts consistent** within similar UI element groups
5. **Test focus navigation flow** to ensure proper D-pad behavior
6. **Consider element size** - larger elements can handle bigger scale increases

## Animation Details

The focus indicator system uses:

- **Animation Duration**: 150ms by default
- **Animation Type**: `tween()` with linear interpolation
- **Scale Origin**: Center of the element
- **Focus Detection**: `onFocusChanged` callback
- **Performance**: Optimized with `remember` and `animateFloatAsState`

## Examples in Codebase

- **LeftSidebar icons**: Uses `tvIconFocusIndicator()` (3% scale)
- **Movie/TV cards**: Uses `tvCardFocusIndicator()` (8% scale)
- **Hero section buttons**: Uses `tvButtonFocus()` (4% scale)
- **Search components**: Uses `tvCardFocusIndicator()` and `tvIconFocusIndicator()`
- **Settings options**: Uses `tvButtonFocus()` (4% scale)

## Focus Indicator Hierarchy

For complex layouts with nested focusable elements:

1. **Container**: Apply focus indicator to the main interactive container
2. **Content**: Child elements don't need additional focus indicators
3. **Grouping**: Use consistent scale amounts within element groups
4. **Navigation**: Ensure focus flow works naturally with D-pad input

## Troubleshooting

- **Element not scaling**: Ensure the focus indicator is applied before `.focusable()`
- **Animation not smooth**: Check that the element isn't being recreated on each recomposition
- **Scale too subtle**: Use a larger scale value or switch to `tvCardFocusIndicator()`
- **Performance issues**: Avoid applying focus indicators to non-interactive elements