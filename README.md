[![release - v0.1-beta](https://img.shields.io/badge/release-v0.1--beta-blue)](https://)
# NRF24Chat
Hi, this is a **BETA** Android app, that allow you to chat through nrf24 modules with arduino. It is based on felHR85 UsbSerial library.

## Requirements
 - USB OTG converter
 - Arduino
 - NRF 24L01 module
 - jumper wires

## Usage
Upload the [source code](https://raw.githubusercontent.com/ASDosjani/NRF24Chat/master/nrf24l01_chat.ino) to the arduino (with [Arduino IDE](https://www.arduino.cc/en/software) or [ArduinoDroid](https://play.google.com/store/apps/details?id=name.antonsmirnov.android.arduinodroid2&hl=hu&gl=US)), then connect the appropriate cables to the nrf24L01 module.
For example:
|Arduino Nano|NRF24L01|
|-|-|
|3.3V|VCC|
|GND|GND|
|10|CSN|
|9|CE|
|13|SCK|
|11|MOSI|
|12|MISO|

*[Download apk](https://github.com/ASDosjani/NRF24Chat/raw/master/NRF24Chat.apk)* and install. (Android 4.2+)

P.S. It's open source, you can use it as you want.
