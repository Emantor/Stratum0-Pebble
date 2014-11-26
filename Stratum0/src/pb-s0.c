#include <pebble.h>
#include <pebble_fonts.h>

#define SPACE_STATUS 0
#define SPACE_OPENER 1
#define REQUEST_DATA 2

#define FETCH_DATA 1
#define OPEN_SPACE 2
#define CLOSE_SPACE 3

static Window *window;
static TextLayer *space_opener_layer;
static GBitmap *s_icon_bitmap_open;
static GBitmap *s_icon_bitmap_closed;
static BitmapLayer *logo_layer;

void send_data(int data)
{
    DictionaryIterator *iter;
    app_message_outbox_begin(&iter);
     
    Tuplet value = TupletInteger(REQUEST_DATA, data);
    dict_write_tuplet(iter, &value);
     
    app_message_outbox_send();
    APP_LOG(APP_LOG_LEVEL_INFO, "Data send!");
}

static void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  send_data(FETCH_DATA);
}

static void up_click_handler(ClickRecognizerRef recognizer, void *context) {
  send_data(OPEN_SPACE);
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  send_data(CLOSE_SPACE);
}

static void inbox_received_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Message received!");
  Tuple *t = dict_read_first(iterator);

  // Process all pairs present
  while (t != NULL) {
    // Long lived buffer
    static char s_buffer[64];
    static char s_buffer_2[64];

    // Process this pair's key
    switch (t->key) {
      case SPACE_STATUS:
        // Copy value and display
        if(t->value->uint8==1){
	  bitmap_layer_set_bitmap(logo_layer,s_icon_bitmap_open);
	} else {
	  bitmap_layer_set_bitmap(logo_layer,s_icon_bitmap_closed);
	}
        break;
      case SPACE_OPENER:
        // Copy value and display
        snprintf(s_buffer_2, sizeof(s_buffer), "Opener:%s", t->value->cstring);
	APP_LOG(APP_LOG_LEVEL_INFO, s_buffer_2);
        text_layer_set_text(space_opener_layer, s_buffer_2);
        break;
    }

    // Get next pair, if any
    t = dict_read_next(iterator);
  }
  vibes_double_pulse();
}

static void inbox_dropped_callback(AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Message dropped!");
}

static void outbox_failed_callback(DictionaryIterator *iterator, AppMessageResult reason, void *context) {
  APP_LOG(APP_LOG_LEVEL_ERROR, "Outbox send failed!");
}

static void outbox_sent_callback(DictionaryIterator *iterator, void *context) {
  APP_LOG(APP_LOG_LEVEL_INFO, "Outbox send success!");
}

static void click_config_provider(void *context) {
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click_handler);
  window_single_click_subscribe(BUTTON_ID_UP, up_click_handler);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click_handler);
}

static void window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect window_bounds = layer_get_bounds(window_layer);

  logo_layer = bitmap_layer_create(GRect(22,5, 100, 100));
  s_icon_bitmap_closed = gbitmap_create_with_resource(RESOURCE_ID_STRATUM0_CLOSED);
  s_icon_bitmap_open = gbitmap_create_with_resource(RESOURCE_ID_STRATUM0_OPEN);
  bitmap_layer_set_bitmap(logo_layer, s_icon_bitmap_closed);

  space_opener_layer = text_layer_create(GRect(5, 105, window_bounds.size.w - 5, 50));
  text_layer_set_font(space_opener_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24));
  text_layer_set_text(space_opener_layer, "Opener...");
  text_layer_set_overflow_mode(space_opener_layer, GTextOverflowModeWordWrap);

  layer_add_child(window_layer, text_layer_get_layer(space_opener_layer));
  layer_add_child(window_layer, bitmap_layer_get_layer(logo_layer));
  send_data(FETCH_DATA);
}

static void window_unload(Window *window) {
  text_layer_destroy(space_opener_layer);
  bitmap_layer_destroy(logo_layer);
  gbitmap_destroy(s_icon_bitmap_closed);
  gbitmap_destroy(s_icon_bitmap_open);
}

static void init(void) {
  // Register callbacks
  app_message_register_inbox_received(inbox_received_callback);
  app_message_register_inbox_dropped(inbox_dropped_callback);
  app_message_register_outbox_failed(outbox_failed_callback);
  app_message_register_outbox_sent(outbox_sent_callback);
  // open AppMessage Connection
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());

  window = window_create();
  window_set_click_config_provider(window, click_config_provider);
  window_set_window_handlers(window, (WindowHandlers) {
    .load = window_load,
    .unload = window_unload,
  });
  const bool animated = true;
  window_stack_push(window, animated);
}

static void deinit(void) {
  window_destroy(window);
}

int main(void) {
  init();

  APP_LOG(APP_LOG_LEVEL_DEBUG, "Done initializing, pushed window: %p", window);

  app_event_loop();
  deinit();
}
