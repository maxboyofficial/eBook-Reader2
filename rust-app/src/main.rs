// src/main.rs
// Minimal Android NativeActivity using android-activity and skia-safe.
// It draws a solid background that follows the system dark/light mode.

use android_activity::{AndroidApp, AndroidAppBuilder, InputStatus, WindowConfig};
use ndk_glue::ndk_context;
use skia_safe::{Color, Canvas, Color4f, Paint, Surface};
use std::sync::Arc;

fn main() {
    // Build the Android app with a simple redraw loop.
    AndroidAppBuilder::new()
        .on_create(|app| {
            // Store a placeholder surface; we'll create it on the first draw.
            let _ = app;
        })
        .on_input(|_app, _event| InputStatus::Ignored)
        .on_window_resized(|app, new_config| {
            // When window size changes, recreate the surface.
            let window = app.window().expect("no window");
            let width = new_config.width as i32;
            let height = new_config.height as i32;
            let mut surface = Surface::new_raster_n32_premul((width, height)).expect("surface");
            app.set_user_data(Arc::new(surface));
        })
        .on_draw(|app| {
            // Obtain the surface that we stored in user_data.
            let surface = app.user_data::<Arc<Surface>>()
                .expect("surface not set");
            let canvas: Canvas = surface.canvas();
            // Determine dark/light mode from UI mode flag.
            let night_mode = ndk_context().configuration().ui_mode & 0x30 == 0x20; // UI_MODE_NIGHT_YES
            let bg_color = if night_mode { Color::BLACK } else { Color::WHITE };
            canvas.clear(bg_color);
            // Draw a simple centered text.
            let mut paint = Paint::default();
            paint.set_color(if night_mode { Color::WHITE } else { Color::BLACK });
            paint.set_anti_alias(true);
            paint.set_text_size(48.0);
            let text = "Mochi Rust Reader";
            let (width, height) = (canvas.image_info().width() as f32, canvas.image_info().height() as f32);
            let text_width = paint.measure_text(text, None);
            canvas.draw_str(text, ((width - text_width) / 2.0, height / 2.0), &paint);
            // Flush to the screen.
            surface.flush_and_submit();
        })
        .build()
        .expect("Failed to build Android app")
        .run();
}
