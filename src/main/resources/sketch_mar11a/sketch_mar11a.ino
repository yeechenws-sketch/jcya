#include <HX711.h> 
#include <WiFi.h> 
#include <PubSubClient.h> 
#include <ArduinoJson.h>
#include <HTTPClient.h>

enum LedColor {
  LED_OFF,
  LED_RED,
  LED_GREEN,
  LED_BLUE,
  LED_YELLOW,
  LED_CYAN,
  LED_MAGENTA,
  LED_WHITE
};

enum BlinkMode {
  BLINK_NONE,
  BLINK_SINGLE,
  BLINK_ALTERNATE,
  BLINK_FAST
};

// ⚠️ 开源版本已脱敏：请填入你自己的 WiFi 与 MQTT 服务器信息后再烧录
const char* ssid = "YOUR_WIFI_SSID"; 
const char* password = "YOUR_WIFI_PASSWORD"; 
const char* mqtt_server = "YOUR_MQTT_SERVER_IP"; 
const int mqtt_port = 1883; 
const char* device_id = "BOX001"; 
const int slot_id = 1; 
 
const int HX711_DT = 21; 
const int HX711_SCK = 20;

const int LED_R = 2;
const int LED_G = 4;
const int LED_B = 5;

const int MP3_TX = 10;
const int MP3_RX = 9;

const int BUTTON_PIN = 1;

unsigned long lastBlinkTime = 0;
bool blinkState = false;
LedColor blinkColor1 = LED_OFF;
LedColor blinkColor2 = LED_OFF;
BlinkMode blinkMode = BLINK_NONE;
int blinkInterval = 500;
bool isBlinking = false; 
unsigned long blinkEndTime = 0;

float calibrationFactor = 0.001355; 
long emptyRaw = 552621; 

float weightThreshold = 0.5;
float emptyThreshold = 5.0;
const int STABLE_COUNT = 5;
const int SAMPLE_COUNT = 10; 

HX711 scale; 
WiFiClient espClient; 
PubSubClient mqtt(espClient);
 
float lastWeight = -1;
int stableCount = 0; 
unsigned long lastSendTime = 0; 
bool bottleRemoved = false; 
bool initialized = false;         

bool buttonPressed = false;
unsigned long buttonPressStartTime = 0;
const unsigned long SHORT_PRESS_MAX_MS = 400;
const unsigned long LONG_PRESS_MS = 500;
bool emergencyCallInProgress = false;

void setLedColor(LedColor color);
void startBlink(LedColor color1, LedColor color2, BlinkMode mode, int intervalMs, unsigned long durationMs);
void stopBlink();
void updateBlink();
void playMP3(int fileNum);
void playMP3InFolder(int folderNum, int fileNum);
void setVolume(int volume);
void stopMP3();
void selectTF();
void disableLoop();
void sendMP3Command(uint8_t cmd, uint8_t dat1, uint8_t dat2);
float getWeight();
float getStableWeight();
void sendEvent(String eventType, float before, float after);
void sendWeightChange(float before, float after);
void sendHeartbeat(float weight);
void reconnect();
void callback(char* topic, byte* payload, unsigned int length);
void executeLedEffect(String color, String effect, int duration);
void sendEmergencyCall();
void handleButton();

void setup() { 
    Serial.begin(115200); 
    Serial.println("SmartMedicineBox Starting..."); 
    
    pinMode(LED_R, OUTPUT);
    pinMode(LED_G, OUTPUT);
    pinMode(LED_B, OUTPUT);
    setLedColor(LED_OFF);
    
    pinMode(BUTTON_PIN, INPUT_PULLUP);
    
    scale.begin(HX711_DT, HX711_SCK); 
    scale.tare(); 
    
    Serial1.begin(9600, SERIAL_8N1, MP3_RX, MP3_TX);
    Serial.println("Waiting for DFPlayer Mini to initialize...");
    delay(3000);
    
    Serial.println("Initializing DFPlayer Mini...");
    
    Serial.println("Step 1: Select TF card");
    selectTF();
    delay(1000);
    
    Serial.println("Step 2: Stop any playback");
    stopMP3();
    delay(500);
    
    Serial.println("Step 3: Set volume to 30");
    setVolume(30);
    delay(500);
    
    Serial.println("Step 4: Disable loop mode");
    disableLoop();
    delay(500);
    
    Serial.println("Step 5: Stop playback final check");
    stopMP3();
    delay(500);
    
    Serial.println("DFPlayer Mini initialized!");
    
    Serial.println("Playing welcome sound (mp3/0001.mp3)...");
    setLedColor(LED_GREEN);
    delay(500);
    playMP3(1);
    Serial.println("Waiting for welcome sound to play...");
    delay(4000);
    setLedColor(LED_OFF);
    
    WiFi.begin(ssid, password); 
    Serial.print("Connecting to WiFi");
    while (WiFi.status() != WL_CONNECTED) { 
        delay(500); 
        Serial.print("."); 
    } 
    Serial.println("\nWiFi Connected!"); 
    Serial.print("IP: "); 
    Serial.println(WiFi.localIP()); 
    
    mqtt.setServer(mqtt_server, mqtt_port); 
    mqtt.setCallback(callback); 
    
    Serial.println("Waiting for stable initial weight..."); 
    delay(2000);
    
    float initWeight = getStableWeight();
    lastWeight = initWeight;
    initialized = true;
    bottleRemoved = (initWeight < emptyThreshold);
    
    Serial.printf("Initial weight: %.2f g (Bottle: %s)\n", 
        initWeight, bottleRemoved ? "Removed" : "Present"); 
    
    Serial.println("SmartMedicineBox Ready!");
} 

void loop() { 
    if (!mqtt.connected()) reconnect(); 
    mqtt.loop(); 
    
    if (!initialized) {
        delay(200);
        return;
    }
    
    updateBlink();
    
    float weight = getWeight(); 
    
    if (weight < emptyThreshold && lastWeight >= emptyThreshold) { 
        stableCount++; 
        if (stableCount >= STABLE_COUNT) { 
            sendEvent("bottle_removed", lastWeight, weight); 
            Serial.println(">>> Bottle Removed!"); 
            startBlink(LED_YELLOW, LED_OFF, BLINK_SINGLE, 500, 0);
            bottleRemoved = true; 
            lastWeight = weight; 
            stableCount = 0; 
            lastSendTime = millis(); 
        } 
    } 
    else if (weight >= emptyThreshold && lastWeight < emptyThreshold && bottleRemoved) { 
        stableCount++; 
        if (stableCount >= STABLE_COUNT) { 
            sendEvent("bottle_returned", lastWeight, weight); 
            Serial.println(">>> Bottle Returned!"); 
            stopBlink();
            setLedColor(LED_GREEN);
            delay(1000);
            setLedColor(LED_OFF);
            bottleRemoved = false; 
            lastWeight = weight; 
            stableCount = 0; 
            lastSendTime = millis(); 
        } 
    } 
    else if (weight >= emptyThreshold) {
        float change = weight - lastWeight;
        if (abs(change) >= weightThreshold) { 
            stableCount++; 
            if (stableCount >= STABLE_COUNT) { 
                sendWeightChange(lastWeight, weight); 
                if (change < 0) {
                } else {
                    Serial.println(">>> Medicine Refilled!");
                    stopBlink();
                    setLedColor(LED_BLUE);
                    delay(1000);
                    setLedColor(LED_OFF);
                    playMP3(9);
                }
                lastWeight = weight; 
                stableCount = 0; 
                lastSendTime = millis(); 
            } 
        } else {
            stableCount = 0;
        }
    }
    else { 
        stableCount = 0; 
    } 
    
    if (millis() - lastSendTime > 30000) { 
        sendHeartbeat(weight); 
        lastSendTime = millis(); 
    } 
    
    delay(200); 
}

float getWeight() { 
    if (!scale.is_ready()) return lastWeight >= 0 ? lastWeight : 0; 
    
    long sum = 0; 
    for (int i = 0; i < SAMPLE_COUNT; i++) { 
        sum += scale.read(); 
        delay(10); 
    } 
    
    float weight = (float)(sum / SAMPLE_COUNT - emptyRaw) * calibrationFactor; 
    return weight > 0 ? weight : 0; 
}

float getStableWeight() {
    float sum = 0;
    int count = 0;
    for (int i = 0; i < 20; i++) {
        if (scale.is_ready()) {
            long raw = scale.read();
            float w = (float)(raw - emptyRaw) * calibrationFactor;
            if (w > 0) {
                sum += w;
                count++;
            }
        }
        delay(50);
    }
    return count > 0 ? sum / count : 0;
}

void reconnect() { 
    String topic = String("medicine/box/") + device_id; 
    while (!mqtt.connected()) { 
        Serial.print("MQTT Connecting..."); 
        if (mqtt.connect(device_id)) { 
            mqtt.subscribe((topic + "/command").c_str()); 
            Serial.println(" Connected!"); 
            
            if (initialized && lastWeight >= 0) {
                sendHeartbeat(lastWeight);
            }
        } else { 
            Serial.println(" Failed, retry in 5s"); 
            delay(5000); 
        } 
    } 
}

void executeLedEffect(String color, String effect, int duration) {
    LedColor ledColor = LED_OFF;
    
    if (color == "red") ledColor = LED_RED;
    else if (color == "green") ledColor = LED_GREEN;
    else if (color == "blue") ledColor = LED_BLUE;
    else if (color == "yellow") ledColor = LED_YELLOW;
    else if (color == "cyan") ledColor = LED_CYAN;
    else if (color == "magenta") ledColor = LED_MAGENTA;
    else if (color == "white") ledColor = LED_WHITE;
    else if (color == "off") {
        stopBlink();
        setLedColor(LED_OFF);
        return;
    }
    
    stopBlink();
    
    if (effect == "solid" || effect == "on") {
        setLedColor(ledColor);
        if (duration > 0) {
            delay(duration);
            setLedColor(LED_OFF);
        }
    } 
    else if (effect == "blink" || effect == "blink_500") {
        startBlink(ledColor, LED_OFF, BLINK_SINGLE, 500, duration > 0 ? millis() + duration : 0);
    }
    else if (effect == "blink_fast" || effect == "blink_200") {
        startBlink(ledColor, LED_OFF, BLINK_FAST, 200, duration > 0 ? millis() + duration : 0);
    }
    else if (effect == "blink_slow" || effect == "blink_300") {
        startBlink(ledColor, LED_OFF, BLINK_SINGLE, 300, duration > 0 ? millis() + duration : 0);
    }
    else if (effect == "alternate") {
        startBlink(ledColor, LED_YELLOW, BLINK_ALTERNATE, 500, duration > 0 ? millis() + duration : 0);
    }
    else {
        setLedColor(ledColor);
        if (duration > 0) {
            delay(duration);
            setLedColor(LED_OFF);
        }
    }
}

void callback(char* topic, byte* payload, unsigned int length) { 
    String msg = ""; 
    for (int i = 0; i < length; i++) msg += (char)payload[i]; 
    
    Serial.println("=== MQTT Command Received ===");
    Serial.print("Topic: ");
    Serial.println(topic);
    Serial.print("Payload: ");
    Serial.println(msg);
    
    StaticJsonDocument<512> doc; 
    DeserializationError error = deserializeJson(doc, msg);
    
    if (error) {
        Serial.print("JSON Parse Error: ");
        Serial.println(error.c_str());
        return;
    }
    
    String cmd = doc["command"]; 
    Serial.print("Command: ");
    Serial.println(cmd);
    
    if (cmd == "tare") { 
        scale.tare(); 
        Serial.println("Tare Done"); 
    } 
    else if (cmd == "play_mp3") { 
        int fileNum = doc["fileNum"] | 1;
        Serial.printf("Playing MP3: %03d.mp3\n", fileNum);
        playMP3(fileNum);
    }
    else if (cmd == "stop_mp3") {
        stopMP3();
        Serial.println("MP3 Stopped by command");
    }
    else if (cmd == "set_led") { 
        String color = doc["color"] | "off";
        String effect = doc["effect"] | "solid";
        int duration = doc["duration"] | 0;
        
        Serial.printf("LED: color=%s, effect=%s, duration=%d\n", 
            color.c_str(), effect.c_str(), duration);
        executeLedEffect(color, effect, duration);
    }
    else if (cmd == "set_volume") { 
        int volume = doc["volume"] | 30;
        setVolume(volume);
    }
    else if (cmd == "remind_medicine") {
        int fileNum = doc["fileNum"] | 2;
        String color = doc["color"] | "red";
        String effect = doc["effect"] | "blink";
        
        Serial.println(">>> remind_medicine: 吃药提醒");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 0);
    }
    else if (cmd == "confirm_medicine") {
        int fileNum = doc["fileNum"] | 3;
        String color = doc["color"] | "green";
        String effect = doc["effect"] | "solid";
        
        Serial.println(">>> confirm_medicine: 确认服药");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 3000);
    }
    else if (cmd == "medicine_success") {
        int fileNum = doc["fileNum"] | 3;
        String color = doc["color"] | "green";
        String effect = doc["effect"] | "solid";
        
        Serial.println(">>> medicine_success: 服药成功");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 3000);
    }
    else if (cmd == "medicine_overdose") {
        int fileNum = doc["fileNum"] | 7;
        String color = doc["color"] | "red";
        String effect = doc["effect"] | "alternate";
        
        Serial.println(">>> medicine_overdose: 多服提醒");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 0);
    }
    else if (cmd == "medicine_underdose") {
        int fileNum = doc["fileNum"] | 10;
        String color = doc["color"] | "yellow";
        String effect = doc["effect"] | "blink_slow";
        
        Serial.println(">>> medicine_underdose: 少服提醒");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 0);
    }
    else if (cmd == "wrong_time") {
        int fileNum = doc["fileNum"] | 8;
        String color = doc["color"] | "red";
        String effect = doc["effect"] | "alternate";
        
        Serial.println(">>> wrong_time: 非服药时间");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 0);
    }
    else if (cmd == "refill_success") {
        int fileNum = doc["fileNum"] | 9;
        String color = doc["color"] | "blue";
        String effect = doc["effect"] | "solid";
        
        Serial.println(">>> refill_success: 药量补充成功");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 3000);
    }
    else if (cmd == "timeout_remind") {
        int fileNum = doc["fileNum"] | 4;
        String color = doc["color"] | "red";
        String effect = doc["effect"] | "blink";
        
        Serial.println(">>> timeout_remind: 超时未吃药");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 0);
    }
    else if (cmd == "low_stock") {
        int fileNum = doc["fileNum"] | 5;
        String color = doc["color"] | "yellow";
        String effect = doc["effect"] | "blink";
        
        Serial.println(">>> low_stock: 药量不足");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 0);
    }
    else if (cmd == "system_error") {
        int fileNum = doc["fileNum"] | 6;
        String color = doc["color"] | "red";
        String effect = doc["effect"] | "blink_fast";
        
        Serial.println(">>> system_error: 系统异常");
        Serial.printf("Playing: %03d.mp3\n", fileNum);
        playMP3(fileNum);
        stopBlink();
        executeLedEffect(color, effect, 0);
    }
    else {
        Serial.print("Unknown command: ");
        Serial.println(cmd);
    }
    
    Serial.println("=== Command Processing Complete ===");
} 

void sendEvent(String eventType, float before, float after) { 
    StaticJsonDocument<256> doc; 
    doc["deviceId"] = device_id; 
    doc["slotId"] = slot_id; 
    doc["eventType"] = eventType; 
    doc["weightBefore"] = before; 
    doc["weightAfter"] = after; 
    doc["timestamp"] = millis(); 
    
    char buf[256]; 
    serializeJson(doc, buf); 
    String topic = String("medicine/box/") + device_id + "/data";
    mqtt.publish(topic.c_str(), buf); 
    Serial.printf("Event: %s (%.2f -> %.2f g)\n", eventType.c_str(), before, after); 
}

void sendWeightChange(float before, float after) { 
    StaticJsonDocument<256> doc; 
    doc["deviceId"] = device_id; 
    doc["slotId"] = slot_id; 
    doc["eventType"] = "weight_change"; 
    doc["weightBefore"] = before; 
    doc["weightAfter"] = after; 
    doc["weightChange"] = before - after; 
    doc["timestamp"] = millis(); 
    
    char buf[256]; 
    serializeJson(doc, buf); 
    String topic = String("medicine/box/") + device_id + "/data";
    mqtt.publish(topic.c_str(), buf); 
    Serial.printf("Weight: %.2f -> %.2f g (Change: %.2f g)\n", before, after, before - after); 
}

void sendHeartbeat(float weight) { 
    StaticJsonDocument<128> doc; 
    doc["deviceId"] = device_id; 
    doc["eventType"] = "heartbeat"; 
    doc["weight"] = weight; 
    doc["weightAfter"] = weight;
    doc["bottlePresent"] = weight >= emptyThreshold; 
    
    char buf[128]; 
    serializeJson(doc, buf); 
    String topic = String("medicine/box/") + device_id + "/data";
    mqtt.publish(topic.c_str(), buf); 
    Serial.printf("Heartbeat: %.2f g (Bottle: %s)\n", weight, weight >= emptyThreshold ? "Present" : "Removed"); 
}

void setLedColor(LedColor color) {
    digitalWrite(LED_R, LOW);
    digitalWrite(LED_G, LOW);
    digitalWrite(LED_B, LOW);
    
    switch(color) {
        case LED_RED:
            digitalWrite(LED_R, HIGH);
            break;
        case LED_GREEN:
            digitalWrite(LED_G, HIGH);
            break;
        case LED_BLUE:
            digitalWrite(LED_B, HIGH);
            break;
        case LED_YELLOW:
            digitalWrite(LED_R, HIGH);
            digitalWrite(LED_G, HIGH);
            break;
        case LED_CYAN:
            digitalWrite(LED_G, HIGH);
            digitalWrite(LED_B, HIGH);
            break;
        case LED_MAGENTA:
            digitalWrite(LED_R, HIGH);
            digitalWrite(LED_B, HIGH);
            break;
        case LED_WHITE:
            digitalWrite(LED_R, HIGH);
            digitalWrite(LED_G, HIGH);
            digitalWrite(LED_B, HIGH);
            break;
        case LED_OFF:
        default:
            break;
    }
}

void startBlink(LedColor color1, LedColor color2, BlinkMode mode, int intervalMs, unsigned long durationMs) {
    blinkColor1 = color1;
    blinkColor2 = color2;
    blinkMode = mode;
    blinkInterval = intervalMs;
    isBlinking = true;
    lastBlinkTime = millis();
    blinkState = true;
    if (durationMs > 0) {
        blinkEndTime = millis() + durationMs;
    } else {
        blinkEndTime = 0;
    }
    setLedColor(color1);
}

void stopBlink() {
    isBlinking = false;
    blinkMode = BLINK_NONE;
    setLedColor(LED_OFF);
}

void updateBlink() {
    if (!isBlinking) return;
    
    if (blinkEndTime > 0 && millis() >= blinkEndTime) {
        stopBlink();
        return;
    }
    
    if (millis() - lastBlinkTime >= blinkInterval) {
        lastBlinkTime = millis();
        blinkState = !blinkState;
        
        if (blinkMode == BLINK_SINGLE) {
            if (blinkState) {
                setLedColor(blinkColor1);
            } else {
                setLedColor(LED_OFF);
            }
        } else if (blinkMode == BLINK_ALTERNATE) {
            if (blinkState) {
                setLedColor(blinkColor1);
            } else {
                setLedColor(blinkColor2);
            }
        } else if (blinkMode == BLINK_FAST) {
            if (blinkState) {
                setLedColor(blinkColor1);
            } else {
                setLedColor(LED_OFF);
            }
        }
    }
}

void sendMP3Command(uint8_t cmd, uint8_t dat1, uint8_t dat2) {
    uint8_t buf[8];
    buf[0] = 0x7E;
    buf[1] = 0xFF;
    buf[2] = 0x06;
    buf[3] = cmd;
    buf[4] = 0x00;
    buf[5] = dat1;
    buf[6] = dat2;
    buf[7] = 0xEF;
    
    Serial.print("DFPlayer Command: ");
    for (int i = 0; i < 8; i++) {
        Serial.printf("%02X ", buf[i]);
        Serial1.write(buf[i]);
    }
    Serial.println();
}

void playMP3(int fileNum) {
    Serial.printf("Playing MP3: mp3/%04d.mp3\n", fileNum);
    uint8_t highByte = (fileNum >> 8) & 0xFF;
    uint8_t lowByte = fileNum & 0xFF;
    sendMP3Command(0x12, highByte, lowByte);
}

void playMP3InFolder(int folderNum, int fileNum) {
    Serial.printf("Playing MP3 folder/%d/%04d.mp3\n", folderNum, fileNum);
    sendMP3Command(0x0F, folderNum, fileNum);
}

void setVolume(int volume) {
    if (volume < 0) volume = 0;
    if (volume > 30) volume = 30;
    sendMP3Command(0x06, 0x00, volume);
    Serial.printf("Volume set to: %d\n", volume);
}

void stopMP3() {
    sendMP3Command(0x16, 0x00, 0x00);
    Serial.println("MP3 Stopped");
}

void selectTF() {
    sendMP3Command(0x09, 0x00, 0x02);
    Serial.println("TF Card Selected");
}

void disableLoop() {
    sendMP3Command(0x11, 0x00, 0x00);
    Serial.println("Loop Mode Disabled");
}
