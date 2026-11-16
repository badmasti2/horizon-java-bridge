//! Local Java mod layers on the title and single-player pause menus.
use super::*;
use serde_json::{Value, json};

#[derive(Default)]
pub(super) struct JavaTitle {
    ops: Vec<Value>,
    custom: bool,
    failed: bool,
    next_frame: Option<Instant>,
    last_cursor: Option<(f32, f32)>,
    last_activity: Option<Instant>,
    pause: bool,
    empty_since: Option<Instant>,
}

impl Game<'_> {
    /// Exercise the same Java key handlers from the isolated control harness.
    pub(super) fn java_title_scripted_key(&mut self, key: KeyCode, action: &str) -> bool {
        if !self.java_title_available() {
            return false;
        }
        let Some(code) = super::java_screen::sdl_key(key) else {
            return false;
        };
        let send = |game: &mut Self, action: i32| {
            game.java_title_exchange(json!({"kind":"key", "key":code,
                "keycode":super::java_screen::sdl_keycode(key, code), "action":action, "mods":0, "text":""}))
        };
        let handled = send(
            self,
            if action == "up" {
                0
            } else if action == "repeat" {
                2
            } else {
                1
            },
        );
        if action == "tap" {
            send(self, 0);
        }
        !self.java_pause_available() || handled
    }

    fn java_title_available(&self) -> bool {
        (self.java_pause_available()
            || self
                .title
                .as_ref()
                .is_some_and(|t| matches!(t.mode, horizon_client::title::Mode::Main)))
            && self.screen != Screen::Options
            && !self.java.title.failed
            && horizon_jvm::events::ready()
    }

    fn java_pause_available(&self) -> bool {
        self.screen == Screen::Paused
            && self.pause_menu
            && self.player.is_some()
            && self.pauses_game()
            // The last gameplay tick may still be using the Java render thread.
            && self.integrated.as_ref().is_none_or(|server| server.pause_applied())
            && (self.java.title.pause || !self.streaming.terrain_loading())
    }

    pub(super) fn close_java_pause_if_hidden(&mut self) {
        if self.java.title.pause && !self.java_pause_available() {
            let _ = horizon_jvm::events::title_ui(r#"{"kind":"close"}"#);
            self.java.title = JavaTitle::default();
        }
    }

    fn java_title_exchange(&mut self, mut input: Value) -> bool {
        if matches!(
            input["kind"].as_str(),
            Some("press" | "release" | "scroll" | "key")
        ) {
            self.java.title.last_activity = Some(Instant::now());
            self.java.title.next_frame = None;
        }
        input["pause"] = json!(self.java_pause_available());
        let size = self
            .renderer
            .as_ref()
            .map_or([1280, 720], |r| [r.config.width, r.config.height]);
        let batch = HudBatch::new(size);
        input["width"] = json!(batch.width as i32);
        input["height"] = json!(batch.height as i32);
        input["pixelWidth"] = json!(size[0]);
        input["pixelHeight"] = json!(size[1]);
        input["guiScale"] = json!(batch.scale as i32);
        input["x"] = json!(self.cursor.0);
        input["y"] = json!(self.cursor.1);
        match horizon_jvm::events::title_ui(&input.to_string()) {
            Ok((frame, images)) => {
                let Ok(mut frame) = serde_json::from_str::<Value>(&frame) else {
                    return false;
                };
                for image in images {
                    let [width, height] = image.size;
                    self.store_java_pixels(&image.name, width, height, image.rgba);
                }
                if let Some(Value::Array(ops)) = frame.get_mut("ops").map(Value::take) {
                    if self.java.title.pause && ops.is_empty() && !self.java.title.ops.is_empty() {
                        self.java.title.empty_since.get_or_insert_with(Instant::now);
                    } else {
                        self.java.title.ops = self.take_java_images(ops);
                        self.java.title.empty_since = None;
                    }
                }
                if frame.get("screenChanged").and_then(Value::as_bool) == Some(true) {
                    // An input can open or close a screen between Java frames.
                    // Draw the new screen before reusing the old screen's ops.
                    self.java.title.ops.clear();
                    self.java.title.empty_since = None;
                    self.java.title.next_frame = None;
                }
                if let Some(custom) = frame.get("custom").and_then(Value::as_bool) {
                    self.java.title.custom = custom;
                }
                frame.get("taken").and_then(Value::as_bool).unwrap_or(false)
            }
            Err(e) => {
                log::warn!("Java mods: title-screen session failed: {e}");
                self.java.title = JavaTitle {
                    failed: true,
                    pause: self.java_pause_available(),
                    ..Default::default()
                };
                false
            }
        }
    }

    pub(super) fn draw_java_title(&mut self, batch: &mut HudBatch) {
        if !self.java_title_available() {
            return;
        }
        let pause = self.java_pause_available();
        if pause != self.java.title.pause {
            self.java.title = JavaTitle {
                pause,
                ..Default::default()
            };
        }
        let now = Instant::now();
        if self.java.title.last_cursor != Some(self.cursor) {
            self.java.title.last_cursor = Some(self.cursor);
            if self
                .java
                .title
                .last_activity
                .is_none_or(|at| now.duration_since(at) >= Duration::from_millis(150))
            {
                self.java.title.next_frame = None;
            }
            self.java.title.last_activity = Some(now);
        }
        let interactive = self.java.title.custom
            || self
                .java
                .title
                .last_activity
                .is_some_and(|at| now.duration_since(at) < Duration::from_millis(150));
        let interval = if pause && interactive {
            Duration::from_nanos(1_000_000_000 / 120)
        } else {
            Duration::from_millis(16)
        };
        let due = self.java.title.next_frame.unwrap_or(now);
        if now >= due {
            // Keep the schedule on its frame grid, without replaying missed frames.
            let next = due + interval;
            self.java.title.next_frame = Some(if next > now { next } else { now + interval });
            self.java_title_exchange(json!({
                "kind": "frame",
                "occupied": horizon_client::widgets::current_button_bounds(),
            }));
        }
        if self.java.title.custom {
            // The Java child screen replaces the menu. Leave the panorama or
            // the world and HUD beneath it, along with their blur boundary.
            batch.vertices.truncate(batch.blur_at as usize);
            batch.blends.retain_mut(|(range, _)| {
                range.end = range.end.min(batch.blur_at);
                range.start < range.end
            });
        }
        if self
            .java
            .title
            .empty_since
            .is_some_and(|since| now.saturating_duration_since(since) >= Duration::from_millis(120))
        {
            self.java.title.ops.clear();
            self.java.title.empty_since = None;
        }
        let ops = std::mem::take(&mut self.java.title.ops);
        self.draw_java_ops(batch, &ops);
        self.java.title.ops = ops;
    }

    pub(super) fn java_title_custom(&self) -> bool {
        self.java.title.custom && self.java_title_available()
    }

    pub(super) fn java_title_input(&mut self, event: &WindowEvent) -> bool {
        if !self.java_title_available() {
            return false;
        }
        let input = match event {
            WindowEvent::MouseInput { state, button, .. } => {
                let button = match button {
                    MouseButton::Left => 1,
                    MouseButton::Middle => 2,
                    MouseButton::Right => 3,
                    MouseButton::Back => 4,
                    MouseButton::Forward => 5,
                    _ => return self.java.title.custom,
                };
                let mods =
                    i32::from(self.has_shift_down()) * 3 | i32::from(self.has_control_down()) * 192;
                json!({"kind": if *state == ElementState::Pressed {"press"} else {"release"}, "button": button, "mods": mods})
            }
            WindowEvent::MouseWheel { delta, .. } => {
                let (h, v) = match delta {
                    winit::event::MouseScrollDelta::LineDelta(x, y) => {
                        (f64::from(*x), f64::from(*y))
                    }
                    winit::event::MouseScrollDelta::PixelDelta(p) => (p.x / 40.0, p.y / 40.0),
                };
                json!({"kind": "scroll", "h": h, "v": v})
            }
            WindowEvent::KeyboardInput { event, .. } => {
                let PhysicalKey::Code(key) = event.physical_key else {
                    return true;
                };
                let Some(code) = super::java_screen::sdl_key(key) else {
                    return true;
                };
                let mods =
                    i32::from(self.has_shift_down()) * 3 | i32::from(self.has_control_down()) * 192;
                let action = if event.state == ElementState::Released {
                    0
                } else if event.repeat {
                    2
                } else {
                    1
                };
                json!({"kind": "key", "action": action, "key": code, "keycode": super::java_screen::sdl_keycode(key, code), "mods": mods,
                    "text": event.text.as_deref().unwrap_or("")})
            }
            _ => return false,
        };
        self.java_title_exchange(input)
    }
}
