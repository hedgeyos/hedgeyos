local window_type = get_window_type()

if window_type == "WINDOW_TYPE_NORMAL" and not get_window_fullscreen() then
    local application = string.lower(get_application_name() or "")
    local class = string.lower(get_window_class() or "")
    local instance = string.lower(get_class_instance_name() or "")
    local identity = application .. " " .. class .. " " .. instance

    if string.find(identity, "xfce4%-terminal") or
       string.find(identity, "thunar") or
       string.find(identity, "xterm") then
        maximize()
    else
        local screen_width, screen_height = get_screen_geometry()
        local x, y, width, height = get_window_geometry()
        if screen_width and screen_height and width and height and
           width > math.floor(screen_width * 0.88) then
            local target_width = math.floor(screen_width * 0.78)
            local target_height = math.min(height, math.floor(screen_height * 0.82))
            local target_x = math.floor((screen_width - target_width) / 2)
            local target_y = math.max(32, math.floor((screen_height - target_height) / 2))
            set_window_geometry(target_x, target_y, target_width, target_height)
        end
    end
end
