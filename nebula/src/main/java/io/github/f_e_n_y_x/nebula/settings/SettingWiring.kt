package io.github.f_e_n_y_x.nebula.settings

/**
 * What each V+ setting shown in Nebula actually does. Every entry in [legacySettings] must have
 * one (SettingWiringTest checks, and also checks the evidence each kind claims), so a setting can't
 * look functional while nothing reads it.
 */
sealed interface Wiring {
    /**
     * Read by the engine: V+'s PreferenceConfiguration loads it into [field], and code on Nebula's
     * stream path uses that field. [callbacks] are the StreamSession callbacks it depends on; none
     * of them may be a `= Unit` stub.
     */
    data class Engine(val field: String, val callbacks: Set<String> = emptySet()) : Wiring

    /** Read by Nebula's own code under this key. [callbacks] as for [Engine]. */
    data class Nebula(val callbacks: Set<String> = emptySet()) : Wiring

    /** Shown by a Nebula control of its own (not the generic row), which reads the key. */
    data object Native : Wiring

    /** A V+ action Nebula performs (export, import, custom resolutions, reset). */
    data object Action : Wiring

    /** Not built yet: shown greyed with "Coming in [release]" and [why]. */
    data class ComingSoon(val release: String, val why: String) : Wiring

    /** Deliberately not part of Nebula; shown greyed with [why]. */
    data class NotInNebula(val why: String) : Wiring
}

private const val DS5 = "DualSense haptics and the USB controller driver are coming in 0.4."
private const val CROWN = "Nebula's controls profiles replace Crown: Settings → On-screen controls → Open controls editor, where V+ Crown profiles can be imported."
private const val DIAG = "The diagnostics screens are being rebuilt for 0.3.0."

private val RUMBLE = setOf("rumble", "rumbleTriggers", "setControllerLED")
private val MOTION = setOf("setMotionEventState")

/** Key → what it does. Keep in the catalog's order. */
val settingWiring: Map<String, Wiring> = mapOf(
    // Stream
    "list_resolution" to Wiring.Engine("width"),
    "list_fps" to Wiring.Engine("fps"),
    "seekbar_bitrate_kbps" to Wiring.Engine("bitrate"),
    "seekbar_resolutions_scale" to Wiring.Engine("resolutionScale"),
    "custom_resolutions" to Wiring.Action,
    "checkbox_adaptive_bitrate" to Wiring.ComingSoon("0.5", "Adaptive bitrate needs Nova's ABR endpoints."),
    "list_abr_mode" to Wiring.ComingSoon("0.5", "Adaptive bitrate needs Nova's ABR endpoints."),
    "frame_pacing" to Wiring.Engine("framePacing"),
    "checkbox_enable_host_cadence_precise_sync" to Wiring.Engine("enableHostCadencePreciseSync"),
    "checkbox_unlock_fps" to Wiring.NotInNebula("Nebula's frame-rate control always offers every rate, up to 240 fps."),
    "checkbox_show_low_resolution_presets" to Wiring.NotInNebula("Nebula's resolution control has its own sizes; add any other under Custom."),
    "checkbox_reduce_refresh_rate" to Wiring.ComingSoon("0.4", "Arrives with the 120 Hz display work."),
    "seekbar_output_buffer_queue_limit" to Wiring.Engine("outputBufferQueueLimit"),
    "checkbox_force_mtk_max_operating_rate" to Wiring.Engine("forceMtkMaxOperatingRate"),
    "list_hevc_low_latency_mode" to Wiring.Engine("hevcLowLatencyMode"),
    // Frame generation
    "pref_framegen_pick_lossless_dll" to Wiring.Native,
    "checkbox_framegen_enabled" to Wiring.Native,
    "checkbox_framegen_adaptive_enabled" to Wiring.Native,
    "list_framegen_quality_preset" to Wiring.Native,
    "pref_framegen_selftest" to Wiring.Native,
    "seekbar_framegen_internal_width" to Wiring.Native,
    "seekbar_framegen_slow_threshold_ms" to Wiring.Native,
    "checkbox_framegen_present_real_first" to Wiring.Native,
    // Host & network
    "checkbox_enable_sops" to Wiring.Engine("enableSops"),
    "list_screen_combination_mode" to Wiring.Engine("screenCombinationMode"),
    "checkbox_host_audio" to Wiring.Engine("playHostAudio"),
    "checkbox_lock_screen_after_disconnect" to Wiring.ComingSoon("0.4", "Locking the PC after a stream needs a Nova host command."),
    "list_quit_behavior" to Wiring.Nebula(),
    "checkbox_swap_quit_and_disconnect" to Wiring.Nebula(),
    "checkbox_control_only" to Wiring.Engine("controlOnly"),
    "list_background_stream_behavior" to Wiring.Nebula(),
    "checkbox_clipboard_sync_text" to Wiring.Engine("enableClipboardSyncText"),
    "checkbox_clipboard_sync_image" to Wiring.Engine("enableClipboardSyncImage"),
    "checkbox_resume_stream" to Wiring.ComingSoon("0.4", "Resume from the Continue tile on Home meanwhile."),
    "checkbox_extreme_resume" to Wiring.NotInNebula("Set this with \"When Moonlight goes to the background\" and the grace period under Advanced."),
    "checkbox_background_audio" to Wiring.Nebula(),
    "checkbox_enable_stun" to Wiring.ComingSoon("0.4", "Arrives with remote (Tailscale) awareness."),
    // Display
    "video_format" to Wiring.Engine("videoFormat"),
    "checkbox_enable_hdr" to Wiring.Engine("enableHdr"),
    "checkbox_enable_hdr_high_brightness" to Wiring.Nebula(),
    "checkbox_hdr_brightness_override" to Wiring.Engine("hdrBrightnessOverride"),
    "seekbar_hdr_peak_brightness_nits" to Wiring.Engine("hdrPeakBrightnessNits"),
    "list_hdr_mode" to Wiring.Engine("hdrMode"),
    "checkbox_full_range" to Wiring.Engine("fullRange"),
    "capability_diagnostic" to Wiring.ComingSoon("0.3.0", DIAG),
    "checkbox_enable_pip" to Wiring.ComingSoon("0.4", "Picture-in-picture isn't built yet."),
    "checkbox_stretch_video" to Wiring.Native,
    "checkbox_reverse_resolution" to Wiring.Engine("reverseResolution"),
    "checkbox_rotable_screen" to Wiring.Nebula(),
    "use_external_display" to Wiring.ComingSoon("0.4", "External display output isn't built yet."),
    "list_screen_position" to Wiring.Nebula(),
    "seekbar_screen_offset_x" to Wiring.Nebula(),
    "seekbar_screen_offset_y" to Wiring.Nebula(),
    // Stats & stream menu
    "checkbox_disable_warnings" to Wiring.NotInNebula("Nebula doesn't pop stream warnings; connection quality shows in the stats overlay."),
    "game_menu_cards" to Wiring.ComingSoon("0.4", "Choosing which stream-menu sections show isn't built yet."),
    "checkbox_enable_float_ball" to Wiring.Nebula(),
    "list_float_ball_position" to Wiring.Nebula(),
    "seekbar_float_ball_auto_hide_delay" to Wiring.Nebula(),
    "list_float_ball_single_click_action" to Wiring.Nebula(),
    "list_float_ball_double_click_action" to Wiring.Nebula(),
    "list_float_ball_long_click_action" to Wiring.Nebula(),
    "background_source" to Wiring.NotInNebula("Nebula uses game art instead of a background image."),
    "background_image_url" to Wiring.NotInNebula("Nebula uses game art instead of a background image."),
    "local_image_picker" to Wiring.NotInNebula("Nebula uses game art instead of a background image."),
    "reset_background_image" to Wiring.NotInNebula("Nebula uses game art instead of a background image."),
    "checkbox_enable_perf_overlay" to Wiring.Native,
    "list_perf_overlay_orientation" to Wiring.Native,
    "list_perf_overlay_position" to Wiring.Native,
    "perf_overlay_display_items" to Wiring.Native,
    "seekbar_perf_overlay_bg_opacity" to Wiring.NotInNebula("The stats overlay uses the shared overlay transparency above."),
    "checkbox_enable_post_stream_toast" to Wiring.Nebula(),
    // Audio
    "list_audio_config" to Wiring.Engine("audioConfiguration"),
    "checkbox_enable_audiofx" to Wiring.Engine("enableAudioFx"),
    "checkbox_enable_spatializer" to Wiring.Engine("enableSpatializer"),
    "checkbox_enable_audio_passthrough" to Wiring.Engine("enableAudioPassthrough"),
    "list_audio_codec" to Wiring.Engine("audioCodec"),
    "list_audio_passthrough_buffer" to Wiring.Engine("audioPassthroughBufferBytes"),
    "checkbox_ac3_iec61937" to Wiring.Engine("useAc3Iec61937"),
    "checkbox_audio_vibration" to Wiring.Engine("enableAudioVibration"),
    "seekbar_audio_vibration_strength" to Wiring.Engine("audioVibrationStrength"),
    "list_audio_vibration_mode" to Wiring.Engine("audioVibrationMode", RUMBLE),
    "list_audio_vibration_scene" to Wiring.Engine("audioVibrationScene"),
    "checkbox_enable_mic" to Wiring.Engine("enableMic"),
    "list_mic_initial_state" to Wiring.Engine("micInitialState"),
    "list_mic_menu_action_mode" to Wiring.Nebula(),
    "checkbox_show_mic_button" to Wiring.Nebula(),
    "list_mic_button_position" to Wiring.Nebula(),
    "seekbar_mic_bitrate_kbps" to Wiring.Engine("micBitrate"),
    "list_mic_icon_color" to Wiring.Nebula(),
    "list_mic_volume_processing_mode" to Wiring.Engine("micVolumeProcessingEnabled"),
    "checkbox_mic_volume_processing" to Wiring.Engine("micVolumeProcessingEnabled"),
    "checkbox_mic_gain" to Wiring.Engine("micGainEnabled"),
    "seekbar_mic_gain_db" to Wiring.Engine("micGainDb"),
    "checkbox_mic_balance" to Wiring.Engine("micBalanceEnabled"),
    "seekbar_mic_balance_target" to Wiring.Engine("micBalanceTargetPercent"),
    "checkbox_mic_voice_enhancement" to Wiring.Engine("micVoiceEnhancementEnabled"),
    // Gamepads
    "controller_diagnostic" to Wiring.ComingSoon("0.3.0", DIAG),
    "controller_mouse_settings" to Wiring.ComingSoon("0.4", "Using a controller as a mouse isn't built yet."),
    "seekbar_deadzone" to Wiring.Nebula(),
    "stick_calibration" to Wiring.ComingSoon("0.3.0", DIAG),
    "checkbox_combine_joycons" to Wiring.ComingSoon("0.4", "Joy-Con pairs show as two controllers for now."),
    "checkbox_multi_controller" to Wiring.Nebula(),
    "checkbox_enable_start_key_menu" to Wiring.NotInNebula("Start + Select opens Nebula's stream menu instead."),
    "checkbox_usb_driver" to Wiring.ComingSoon("0.4", DS5),
    "list_host_gamepad_selection" to Wiring.Engine("hostGamepadSelection"),
    "checkbox_experimental_haptic_protocols" to Wiring.ComingSoon("0.4", DS5),
    "checkbox_usb_bind_all" to Wiring.ComingSoon("0.4", DS5),
    "list_dualsense_output_mode" to Wiring.ComingSoon("0.4", DS5),
    "checkbox_dualsense_direct_bluetooth" to Wiring.ComingSoon("0.4", DS5),
    "checkbox_dualsense_wireless_bridge" to Wiring.ComingSoon("0.4", DS5),
    "list_game_rumble_mode" to Wiring.Native,
    "seekbar_vibrate_fallback_strength" to Wiring.Native,
    "checkbox_flip_face_buttons" to Wiring.Nebula(),
    "checkbox_gamepad_touchpad_as_mouse" to Wiring.ComingSoon("0.4", "Controller touchpads aren't passed through yet."),
    "checkbox_gamepad_motion_sensors" to Wiring.Native,
    "checkbox_gamepad_motion_fallback" to Wiring.Native,
    // Touch, mouse & keyboard
    "list_native_mouse_mode_preset" to Wiring.Nebula(),
    "checkbox_touchscreen_trackpad" to Wiring.Nebula(),
    "checkbox_touch_keyboard_auto_invoke" to Wiring.Engine("touchKeyboardAutoInvoke"),
    "pref_enable_double_click_drag" to Wiring.Nebula(),
    "seekbar_double_tap_time_threshold" to Wiring.Nebula(),
    "checkbox_special_key_map" to Wiring.ComingSoon("0.4", "Keyboard shortcuts for stream actions aren't built yet; double Esc opens the menu."),
    "checkbox_mouse_middle" to Wiring.ComingSoon("0.4", "This V+ workaround for some mice isn't ported yet."),
    "checkbox_mouse_wheel" to Wiring.ComingSoon("0.4", "This V+ workaround for some mice isn't ported yet."),
    "checkbox_sync_touch_event_with_display" to Wiring.ComingSoon("0.4", "Arrives with the 120 Hz display work."),
    "checkbox_enable_keyboard_toggle_in_native_touch" to Wiring.Nebula(),
    "seekbar_keyboard_toggle_fingers_native_touch" to Wiring.Nebula(),
    "checkbox_mouse_nav_buttons" to Wiring.Nebula(),
    "checkbox_absolute_mouse_mode" to Wiring.Nebula(),
    "checkbox_enable_esc_menu" to Wiring.Nebula(),
    "list_esc_menu_key" to Wiring.Nebula(),
    "checkbox_optimize_hardware_touchpad" to Wiring.ComingSoon("0.4", "Arrives with tablet keyboard and touchpad polish."),
    "checkbox_enable_enhanced_touch" to Wiring.Nebula(),
    "seekbar_flat_region_pixels" to Wiring.ComingSoon("0.4", "Split touch uses a fixed long-press radius for now."),
    "checkbox_enhanced_touch_on_which_side" to Wiring.Nebula(),
    "enhanced_touch_zone_divider" to Wiring.Nebula(),
    "pointer_velocity_factor" to Wiring.Nebula(),
    // On-screen controls
    "checkbox_show_onscreen_controls" to Wiring.Nebula(),
    "list_osc_layout" to Wiring.Nebula(),
    "checkbox_only_show_L3R3" to Wiring.Nebula(),
    "checkbox_show_guide_button" to Wiring.Nebula(),
    "checkbox_half_height_osc_portrait" to Wiring.Nebula(),
    "seekbar_osc_opacity" to Wiring.Nebula(),
    "nebula_osc_with_gamepad" to Wiring.Nebula(),
    "reset_osc" to Wiring.Action,
    // Advanced
    "checkbox_show_onscreen_keyboard" to Wiring.NotInNebula(CROWN),
    "crown_config_management" to Wiring.NotInNebula(CROWN),
    "config_sync_export" to Wiring.Action,
    "config_sync_import" to Wiring.Action,
    "config_sync_select_directory" to Wiring.NotInNebula("Use Save a backup file and Restore to move settings between devices."),
    "checkbox_config_sync_background_sync" to Wiring.NotInNebula("Use Save a backup file and Restore to move settings between devices."),
    "config_sync_import_external_snapshot" to Wiring.Action,
    // About
    "documentation_handbook" to Wiring.NotInNebula("See About."),
    "about_us" to Wiring.NotInNebula("See About."),
    "open_source_notices" to Wiring.NotInNebula("See About."),
    "check_for_updates" to Wiring.NotInNebula("Nebula updates come from your own builds."),
    "pref_developer_unlock" to Wiring.NotInNebula("Everything is unlocked in Nebula."),
    "sponsor_panel" to Wiring.NotInNebula("Not part of Nebula."),
)

/** How the generic settings row should present [key]. */
sealed interface RowState {
    data object Live : RowState
    data class Coming(val release: String, val why: String) : RowState
    data class Unavailable(val why: String) : RowState
}

fun rowState(key: String): RowState = when (val w = settingWiring[key]) {
    is Wiring.ComingSoon -> RowState.Coming(w.release, w.why)
    is Wiring.NotInNebula -> RowState.Unavailable(w.why)
    null -> RowState.Unavailable("Not wired up in Nebula.")
    else -> RowState.Live
}
