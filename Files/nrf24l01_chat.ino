#include <SPI.h>
#include <nRF24L01.h>
#include <RF24.h>

#define CE_PIN 9 //stm32 - PB0, arduino - 9
#define CSN_PIN 10 //stm32 - PA4, arduino - 10

const byte Address[6] = "00001";

RF24 radio(CE_PIN, CSN_PIN);

void setup() {
  Serial.begin(115200);
  radio.begin();
  radio.setDataRate(RF24_250KBPS);
  radio.setPALevel(RF24_PA_MAX);
  radio.setAutoAck(true);
  radio.openWritingPipe(Address);
  radio.openReadingPipe(1, Address);
}


void loop() {
  // listen for radio data
  radio.startListening();

  if ( radio.available() ) {
    // read data from radio
    String receive = "";
    char dataReceived[32];
    unsigned long timeout = millis() + 50;
    while (!receive.endsWith("&433") && timeout > millis()) {
      radio.read( &dataReceived, sizeof(dataReceived));
      receive += dataReceived;
    }
    if (receive.endsWith("&433")) Serial.println(receive.substring(0, receive.length() - 4));
  }

  if ( Serial.available() ) {
    radio.stopListening();
    String raw = Serial.readStringUntil('\n');
    if (raw != "") {
      String dataInput = raw + "&433";
      bool success = true;
      char dataToSend[32];
      for (int i = 0; i < dataInput.length() / 31 + 1; i++) {
        dataInput.substring(i * 31, (i + 1) * 31).toCharArray(dataToSend, 32);
        if (!radio.write( &dataToSend, sizeof(dataToSend))) {
          success = false;
          break;
        }
      }
      if (success)Serial.println("success");
    }
  }
}
